"""
Auto-rate dyvideos using an ONNX ResNet18-style model.
- Quick rating: predict from cover image only.
- Precise rating: extract N frames from video or slide media, predict each, reduce rates.
"""

import logging
import os

import numpy as np
from PIL import Image

logger = logging.getLogger(__name__)

IMAGE_EXTENSIONS = (".jpg", ".jpeg", ".webp", ".png")
CLIP_EXTENSIONS = (".mp4",)


def _get_setting(name, default):
    try:
        from django.conf import settings as django_settings
        return getattr(django_settings, name, default)
    except Exception:
        return default


IMAGENET_MEAN = np.array(
    _get_setting("AUTO_RATE_IMAGENET_MEAN", [0.485, 0.456, 0.406]),
    dtype=np.float32,
)
IMAGENET_STD = np.array(
    _get_setting("AUTO_RATE_IMAGENET_STD", [0.229, 0.224, 0.225]),
    dtype=np.float32,
)
INPUT_SIZE = tuple(_get_setting("AUTO_RATE_INPUT_SIZE", (128, 128)))

DEFAULT_PRECISE_FRAME_COUNT = int(_get_setting("AUTO_RATE_PRECISE_FRAME_COUNT", 3))
DEFAULT_PRECISE_REDUCE = str(_get_setting("AUTO_RATE_PRECISE_REDUCE", "max")).strip().lower()


def _load_onnx_session(model_path: str):
    try:
        import onnxruntime as ort
    except ImportError:
        raise ImportError("onnxruntime is required for auto-rate. Install with: pip install onnxruntime")
    if not os.path.isfile(model_path):
        raise FileNotFoundError(f"Model file not found: {model_path}")
    return ort.InferenceSession(model_path, providers=["CPUExecutionProvider"])


def _preprocess_cover(image_path: str) -> np.ndarray:
    img = Image.open(image_path).convert("RGB")
    img = img.resize(INPUT_SIZE, Image.BILINEAR)
    arr = np.array(img, dtype=np.float32) / 255.0
    arr = (arr - IMAGENET_MEAN) / IMAGENET_STD
    arr = arr.transpose(2, 0, 1)
    arr = np.expand_dims(arr, axis=0)
    return arr.astype(np.float32)


def _logits_to_rate(logits: np.ndarray) -> int:
    flat = np.asarray(logits).flatten()
    if flat.size == 5:
        return int(np.argmax(flat)) + 1
    if flat.size >= 1:
        r = int(round(float(flat[0])))
        return max(1, min(5, r))
    return 1


def _reduce_rates(rates: list[int]) -> int:
    if not rates:
        return 1
    avg_val = sum(rates) / len(rates)
    max_val = max(rates)
    if DEFAULT_PRECISE_REDUCE == "avg":
        return max(1, min(5, int(round(avg_val))))
    return max_val


def _sample_evenly(items: list, count: int) -> list:
    if not items:
        return []
    n = len(items)
    sample_count = max(1, min(count, n))
    if sample_count == 1:
        return [items[0]]
    indices = [int(round(i * (n - 1) / (sample_count - 1))) for i in range(sample_count)]
    return [items[i] for i in indices]


def _video_base_dir(media_root: str, video_path: str) -> str:
    return os.path.join(media_root, video_path.rstrip("/"))


def _scan_dir_paths(base_dir: str, relative_dir: str, extensions: tuple) -> list[str]:
    dir_path = os.path.join(base_dir, relative_dir)
    if not os.path.isdir(dir_path):
        return []
    paths = []
    for name in sorted(os.listdir(dir_path)):
        if name.startswith("."):
            continue
        if not any(name.lower().endswith(ext) for ext in extensions):
            continue
        full_path = os.path.join(dir_path, name)
        if os.path.isfile(full_path) and os.path.getsize(full_path) > 0:
            paths.append(full_path)
    return paths


def resolve_slide_image_paths(media_root: str, video_path: str, media_urls=None) -> list[str]:
    base_dir = _video_base_dir(media_root, video_path)
    paths = []
    seen = set()
    for item in sorted(media_urls or [], key=lambda entry: entry.get("index", 0)):
        if item.get("kind") != "image":
            continue
        index = item.get("index", 0)
        rel = f"images/{index:03d}.jpg"
        full_path = os.path.join(base_dir, rel)
        if full_path in seen:
            continue
        if os.path.isfile(full_path) and os.path.getsize(full_path) > 0:
            paths.append(full_path)
            seen.add(full_path)
    if not paths:
        paths = _scan_dir_paths(base_dir, "images", IMAGE_EXTENSIONS)
    return paths


def resolve_clip_paths(media_root: str, video_path: str, media_urls=None) -> list[str]:
    base_dir = _video_base_dir(media_root, video_path)
    paths = []
    seen = set()
    for item in sorted(media_urls or [], key=lambda entry: entry.get("index", 0)):
        if item.get("kind") != "clip":
            continue
        index = item.get("index", 0)
        rel = f"clips/{index:03d}.mp4"
        full_path = os.path.join(base_dir, rel)
        if full_path in seen:
            continue
        if os.path.isfile(full_path) and os.path.getsize(full_path) > 0:
            paths.append(full_path)
            seen.add(full_path)
    if not paths:
        paths = _scan_dir_paths(base_dir, "clips", CLIP_EXTENSIONS)
    return paths


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
        self._ensure_session()
        input_name = self._session.get_inputs()[0].name
        x = _preprocess_frame(bgr_frame)
        try:
            out = self._session.run(None, {input_name: x})
            return _logits_to_rate(out[0])
        except Exception as e:
            logger.exception("Frame inference failed: %s", e)
            return 1

    def _finalize_precise_rates(self, rates: list[int], on_precise_done=None) -> int:
        if not rates:
            raise ValueError("No rates produced for precise scoring")
        result = _reduce_rates(rates)
        if callable(on_precise_done):
            on_precise_done(rates, result)
        return result

    def predict_from_video_path(
        self,
        video_path: str,
        frame_count: int = DEFAULT_PRECISE_FRAME_COUNT,
        on_precise_done=None,
    ) -> int:
        if not os.path.isfile(video_path):
            raise FileNotFoundError(f"Video file not found: {video_path}")
        frames = _extract_frames(video_path, frame_count)
        if not frames:
            raise ValueError(f"No frames extracted from {video_path}")
        rates = [self.predict_from_frame(bgr) for bgr in frames]
        return self._finalize_precise_rates(rates, on_precise_done)

    def predict_from_image_paths(
        self,
        image_paths: list[str],
        frame_count: int = DEFAULT_PRECISE_FRAME_COUNT,
        on_precise_done=None,
    ) -> int:
        if not image_paths:
            raise FileNotFoundError("No slide image files found")
        sampled = _sample_evenly(image_paths, frame_count)
        rates = [self.predict_from_cover_path(path) for path in sampled]
        return self._finalize_precise_rates(rates, on_precise_done)

    def predict_from_clip_paths(
        self,
        clip_paths: list[str],
        frame_count: int = DEFAULT_PRECISE_FRAME_COUNT,
        on_precise_done=None,
    ) -> int:
        if not clip_paths:
            raise FileNotFoundError("No clip files found")
        sampled = _sample_evenly(clip_paths, frame_count)
        rates = []
        for clip_path in sampled:
            frames = _extract_frames(clip_path, 1)
            if not frames:
                logger.warning("No frames extracted from clip %s", clip_path)
                continue
            rates.append(self.predict_from_frame(frames[0]))
        if not rates:
            raise ValueError("No frames extracted from clip files")
        return self._finalize_precise_rates(rates, on_precise_done)

    def predict_from_slide_visuals(
        self,
        image_paths: list[str],
        clip_paths: list[str],
        frame_count: int = DEFAULT_PRECISE_FRAME_COUNT,
        on_precise_done=None,
    ) -> int:
        if not image_paths and not clip_paths:
            raise FileNotFoundError("No slide image or clip files found")
        rates = []
        if image_paths:
            sampled_images = _sample_evenly(image_paths, frame_count)
            rates.extend(self.predict_from_cover_path(path) for path in sampled_images)
        if clip_paths:
            sampled_clips = _sample_evenly(clip_paths, frame_count)
            for clip_path in sampled_clips:
                frames = _extract_frames(clip_path, 1)
                if not frames:
                    logger.warning("No frames extracted from clip %s", clip_path)
                    continue
                rates.append(self.predict_from_frame(frames[0]))
        if not rates:
            raise ValueError("No slide visuals could be scored")
        return self._finalize_precise_rates(rates, on_precise_done)

    def predict_precise_for_dyvideo(
        self,
        video,
        media_root: str,
        frame_count: int = DEFAULT_PRECISE_FRAME_COUNT,
        on_precise_done=None,
    ) -> int:
        from dyvideo.models import ContentType

        content_type = video.content_type
        if content_type == ContentType.VIDEO:
            return self.predict_from_video_path(
                get_video_path(media_root, video.path),
                frame_count=frame_count,
                on_precise_done=on_precise_done,
            )
        if content_type == ContentType.PHOTO_SLIDES:
            image_paths = resolve_slide_image_paths(media_root, video.path, video.media_urls)
            return self.predict_from_image_paths(
                image_paths,
                frame_count=frame_count,
                on_precise_done=on_precise_done,
            )
        if content_type == ContentType.VIDEO_SLIDES:
            image_paths = resolve_slide_image_paths(media_root, video.path, video.media_urls)
            clip_paths = resolve_clip_paths(media_root, video.path, video.media_urls)
            return self.predict_from_slide_visuals(
                image_paths,
                clip_paths,
                frame_count=frame_count,
                on_precise_done=on_precise_done,
            )
        raise ValueError(f"Unsupported content type for precise rating: {content_type}")

    def close(self):
        self._session = None


def get_cover_path(media_root: str, video_path: str) -> str:
    return os.path.join(media_root, video_path.rstrip("/"), "cover.jpg")


def get_video_path(media_root: str, video_path: str) -> str:
    return os.path.join(media_root, video_path.rstrip("/"), "video.mp4")


def _extract_frames(video_path: str, frame_count: int) -> list:
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
