"""
Auto-rate dyvideos using an ONNX ResNet18-style model.
- Quick rating: predict from cover image only.
- Precise rating: extract N frames from video, predict each, use average rate.
"""

import logging
import os

import numpy as np
from PIL import Image

logger = logging.getLogger(__name__)

# Optional Django settings reader with safe fallback.
def _get_setting(name, default):
    try:
        from django.conf import settings as django_settings
        return getattr(django_settings, name, default)
    except Exception:
        return default


# ResNet/ImageNet standard normalization
IMAGENET_MEAN = np.array(
    _get_setting("AUTO_RATE_IMAGENET_MEAN", [0.485, 0.456, 0.406]),
    dtype=np.float32,
)
IMAGENET_STD = np.array(
    _get_setting("AUTO_RATE_IMAGENET_STD", [0.229, 0.224, 0.225]),
    dtype=np.float32,
)
INPUT_SIZE = tuple(_get_setting("AUTO_RATE_INPUT_SIZE", (128, 128)))

# Default number of frames for precise rating
DEFAULT_PRECISE_FRAME_COUNT = int(_get_setting("AUTO_RATE_PRECISE_FRAME_COUNT", 3))
DEFAULT_PRECISE_REDUCE = str(_get_setting("AUTO_RATE_PRECISE_REDUCE", "max")).strip().lower()


def _load_onnx_session(model_path: str):
    """Load ONNX model and return inference session."""
    try:
        import onnxruntime as ort
    except ImportError:
        raise ImportError("onnxruntime is required for auto-rate. Install with: pip install onnxruntime")
    if not os.path.isfile(model_path):
        raise FileNotFoundError(f"Model file not found: {model_path}")
    return ort.InferenceSession(model_path, providers=["CPUExecutionProvider"])


def _preprocess_cover(image_path: str) -> np.ndarray:
    """
    Load cover image and preprocess for ResNet-style input: 224x224 RGB, ImageNet normalize.
    Returns float32 array NCHW (1, 3, 224, 224).
    """
    img = Image.open(image_path).convert("RGB")
    img = img.resize(INPUT_SIZE, Image.BILINEAR)
    arr = np.array(img, dtype=np.float32) / 255.0
    arr = (arr - IMAGENET_MEAN) / IMAGENET_STD
    arr = arr.transpose(2, 0, 1)  # HWC -> CHW
    arr = np.expand_dims(arr, axis=0)  # (1, 3, 224, 224)
    return arr.astype(np.float32)


def _logits_to_rate(logits: np.ndarray) -> int:
    """
    Map model output to rate 1-5.
    If shape (1, 5) or (5,): classification, use argmax + 1.
    If shape (1, 1) or (1,): regression, round and clamp to [1, 5].
    """
    flat = np.asarray(logits).flatten()
    if flat.size == 5:
        return int(np.argmax(flat)) + 1
    if flat.size >= 1:
        r = int(round(float(flat[0])))
        return max(1, min(5, r))
    return 1


class OnnxRatePredictor:
    """Predict dyvideo rate from cover image using ONNX model."""

    def __init__(self, model_path: str):
        self.model_path = model_path
        self._session = None

    def _ensure_session(self):
        if self._session is None:
            self._session = _load_onnx_session(self.model_path)
            logger.info("Loaded ONNX model from %s", self.model_path)

    def predict_from_cover_path(self, cover_path: str) -> int:
        """
        Quick rating: run model on cover image file; returns rate in [1, 5].
        Raises FileNotFoundError if cover does not exist; logs and returns 1 on inference error.
        """
        if not os.path.isfile(cover_path):
            raise FileNotFoundError(f"Cover image not found: {cover_path}")
        self._ensure_session()
        input_name = self._session.get_inputs()[0].name
        x = _preprocess_cover(cover_path)
        try:
            out = self._session.run(None, {input_name: x})
            rate = _logits_to_rate(out[0])
            logger.debug("Predicted rate %s for %s", rate, cover_path)
            return rate
        except Exception as e:
            logger.exception("Inference failed for %s: %s", cover_path, e)
            return 1

    def predict_from_frame(self, bgr_frame: np.ndarray) -> int:
        """
        Run model on a single BGR frame (HWC from OpenCV). Returns rate in [1, 5].
        """
        self._ensure_session()
        input_name = self._session.get_inputs()[0].name
        x = _preprocess_frame(bgr_frame)
        try:
            out = self._session.run(None, {input_name: x})
            return _logits_to_rate(out[0])
        except Exception as e:
            logger.exception("Frame inference failed: %s", e)
            return 1

    def predict_from_video_path(
        self,
        video_path: str,
        frame_count: int = DEFAULT_PRECISE_FRAME_COUNT,
        on_precise_done=None,
    ) -> int:
        """
        Precise rating: extract N frames from video, predict each, return average rate in [1, 5].
        If on_precise_done is set, calls on_precise_done(rates: list, avg: int) at the end (e.g. for one-line stdout).
        """
        if not os.path.isfile(video_path):
            raise FileNotFoundError(f"Video file not found: {video_path}")
        frames = _extract_frames(video_path, frame_count)
        if not frames:
            logger.warning("No frames extracted from %s", video_path)
            return 1
        rates = []
        for bgr in frames:
            rate = self.predict_from_frame(bgr)
            rates.append(rate)
        avg_val = sum(rates) / len(rates)
        max_val = max(rates)
        if DEFAULT_PRECISE_REDUCE == "avg":
            result = max(1, min(5, int(round(avg_val))))
        else:
            result = max_val
        if callable(on_precise_done):
            on_precise_done(rates, result)
        return result

    def close(self):
        self._session = None


def get_cover_path(media_root: str, video_path: str) -> str:
    """Build absolute path to cover.jpg for a dyvideo (path is relative to media_root)."""
    return os.path.join(media_root, video_path.rstrip("/"), "cover.jpg")


def get_video_path(media_root: str, video_path: str) -> str:
    """Build absolute path to video.mp4 for a dyvideo (path is relative to media_root)."""
    return os.path.join(media_root, video_path.rstrip("/"), "video.mp4")


def _extract_frames(video_path: str, frame_count: int) -> list:
    """
    Extract evenly spaced frames from video using OpenCV.
    Returns list of BGR frames (numpy arrays HWC). Empty list on error.
    """
    try:
        import cv2
    except ImportError:
        raise ImportError("opencv-python is required for precise rating. Install with: pip install opencv-python-headless")
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        logger.warning("Could not open video: %s", video_path)
        return []
    try:
        total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        if total <= 0:
            ret, frame = cap.read()
            if ret and frame is not None:
                return [frame]
            return []
        n = max(1, min(frame_count, total))
        if n == 1:
            cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
            ret, frame = cap.read()
            return [frame] if (ret and frame is not None) else []
        indices = [int(round(i * (total - 1) / (n - 1))) for i in range(n)]
        frames = []
        for idx in indices:
            cap.set(cv2.CAP_PROP_POS_FRAMES, idx)
            ret, frame = cap.read()
            if not ret or frame is None:
                continue
            frames.append(frame)
        return frames
    finally:
        cap.release()


def _preprocess_frame(bgr_frame: np.ndarray) -> np.ndarray:
    """
    Preprocess a BGR frame (HWC from OpenCV) for the model: resize, BGR->RGB, ImageNet norm.
    Returns float32 NCHW (1, 3, H, W).
    """
    try:
        import cv2
    except ImportError:
        raise ImportError("opencv-python is required for precise rating. Install with: pip install opencv-python-headless")
    img = cv2.resize(bgr_frame, INPUT_SIZE, interpolation=cv2.INTER_LINEAR)
    img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
    arr = np.array(img, dtype=np.float32) / 255.0
    arr = (arr - IMAGENET_MEAN) / IMAGENET_STD
    arr = arr.transpose(2, 0, 1)
    arr = np.expand_dims(arr, axis=0)
    return arr.astype(np.float32)
