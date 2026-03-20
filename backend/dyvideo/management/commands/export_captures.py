"""
Management command to export video frame captures for training.
Finds DyVideos with is_auto_rated=False and rate in 1..5, extracts N frames per video
at evenly spaced timestamps to dataset/1/, dataset/2/, ... dataset/5/.
Requires opencv-python-headless.
"""

import logging
import os
from itertools import groupby

import cv2

from django.core.management.base import BaseCommand
from django.conf import settings as django_settings

from dyvideo.models import DyVideo

logger = logging.getLogger(__name__)

RATES = (1, 2, 3, 4, 5)
JPEG_QUALITY = 95


def get_video_path(media_root: str, video_path: str) -> str:
    """Build absolute path to video.mp4 for a dyvideo."""
    return os.path.join(media_root, video_path.rstrip("/"), "video.mp4")


def get_video_info(video_path: str) -> tuple[float, int]:
    """
    Get video duration (seconds) and frame count using OpenCV.
    Returns (0.0, 0) on error.
    """
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        return 0.0, 0
    try:
        frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        fps = cap.get(cv2.CAP_PROP_FPS)
        if fps and fps > 0 and frame_count >= 0:
            return frame_count / fps, frame_count
    except Exception as e:
        logger.warning("OpenCV failed to get info for %s: %s", video_path, e)
    finally:
        cap.release()
    return 0.0, 0


def extract_frames_sequential(
    video_path: str,
    targets: list[tuple[int, str]],
) -> tuple[bool, str]:
    """
    Read video sequentially and save frames at given indices.
    targets: list of (frame_index, output_path). No seeking - most compatible.
    Handles duplicate indices (same frame saved to multiple paths).
    Returns (success, error_message).
    """
    if not targets:
        return True, ""
    grouped = [
        (idx, [p for _, p in g])
        for idx, g in groupby(
            sorted(targets, key=lambda x: x[0]), key=lambda x: x[0]
        )
    ]
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        return False, "failed to open video"
    try:
        frame_idx = 0
        for idx, out_paths in grouped:
            while frame_idx < idx:
                if not cap.grab():
                    return False, "failed to read frame"
                frame_idx += 1
            ret, frame = cap.read()
            if not ret or frame is None:
                return False, "failed to read frame"
            frame_idx += 1
            for out_path in out_paths:
                ok = cv2.imwrite(
                    out_path,
                    frame,
                    [cv2.IMWRITE_JPEG_QUALITY, JPEG_QUALITY],
                )
                if not ok or not os.path.isfile(out_path):
                    return False, "failed to write image"
        return True, ""
    except Exception as e:
        return False, str(e)
    finally:
        cap.release()


class Command(BaseCommand):
    help = (
        "Export frame captures from user-rated videos for training. "
        "Extracts N evenly-spaced frames per video to output_dir/1..5 by rate. "
        "Source: MEDIA_ROOT/video.path/video.mp4. Requires opencv-python-headless."
    )

    def add_arguments(self, parser):
        parser.add_argument(
            "-n",
            "--num-captures",
            type=int,
            default=5,
            help="Number of frame captures to extract per video (default: 5).",
        )
        parser.add_argument(
            "-o",
            "--output-dir",
            type=str,
            default="dataset_captures",
            help="Base output directory; subdirs 1,2,3,4,5 will be created (default: dataset_captures).",
        )
        parser.add_argument(
            "-d",
            "--dry-run",
            action="store_true",
            help="Only list which videos would be processed, do not extract.",
        )
        parser.add_argument(
            "--show-errors",
            action="store_true",
            help="Print error details when extraction fails.",
        )

    def handle(self, *args, **options):
        num_captures = options["num_captures"]
        output_dir = options["output_dir"]
        dry_run = options["dry_run"]
        show_errors = options["show_errors"]

        if num_captures < 1:
            self.stdout.write(
                self.style.ERROR("num_captures must be >= 1")
            )
            return

        media_root = getattr(django_settings, "MEDIA_ROOT", "/var/data/dydata/")
        if not os.path.isdir(media_root):
            self.stdout.write(
                self.style.ERROR(f"MEDIA_ROOT not found: {media_root}")
            )
            return

        queryset = (
            DyVideo.objects.filter(is_auto_rated=False, rate__in=RATES)
            .order_by("rate", "id")
        )
        videos = list(queryset)
        total = len(videos)

        if total == 0:
            self.stdout.write(
                self.style.WARNING(
                    "No videos with is_auto_rated=False and rate in 1..5 found."
                )
            )
            return

        self.stdout.write(
            f"Found {total} video(s) to export ({num_captures} captures each)."
        )

        if dry_run:
            for v in videos:
                video_path = get_video_path(media_root, v.path)
                subdir = os.path.join(output_dir, str(v.rate))
                self.stdout.write(
                    f"  Would export: id={v.id} rate={v.rate} -> {subdir}/{v.id}_*.jpg ({num_captures} frames)"
                )
                if not os.path.isfile(video_path):
                    self.stdout.write(
                        self.style.WARNING(f"    (video not found: {video_path})")
                    )
            return

        exported_videos = 0
        exported_frames = 0
        failed = 0

        for video in videos:
            video_path = get_video_path(media_root, video.path)

            if not os.path.isfile(video_path):
                logger.warning(
                    "Video not found for id=%s path=%s",
                    video.id,
                    video.path,
                )
                self.stdout.write(
                    self.style.WARNING(
                        f"  Skip id={video.id}: video not found {video_path}"
                    )
                )
                failed += 1
                continue

            duration, frame_count = get_video_info(video_path)
            if duration <= 0 or frame_count <= 0:
                self.stdout.write(
                    self.style.WARNING(
                        f"  Skip id={video.id}: could not get video info: {video_path}"
                    )
                )
                failed += 1
                continue

            rate_dir = os.path.join(output_dir, str(video.rate))
            os.makedirs(rate_dir, exist_ok=True)

            if num_captures == 1:
                frame_indices = [frame_count // 2]
            else:
                frame_indices = [
                    min(
                        i * max(1, frame_count - 1) // max(1, num_captures - 1),
                        frame_count - 1,
                    )
                    for i in range(num_captures)
                ]

            targets = [
                (fi, os.path.join(rate_dir, f"{video.id}_{i + 1:04d}.jpg"))
                for i, fi in enumerate(frame_indices)
            ]
            ok, last_error = extract_frames_sequential(video_path, targets)
            if ok:
                exported_frames += len(targets)
                exported_videos += 1
                self.stdout.write(
                    f"  id={video.id} rate={video.rate} -> {len(targets)} frames"
                )
            else:
                failed += 1
                self.stdout.write(
                    self.style.ERROR(f"  id={video.id} failed to extract frames")
                )
                if show_errors and last_error:
                    for line in last_error.splitlines()[-5:]:
                        self.stdout.write(f"    error: {line}")

        self.stdout.write(
            self.style.SUCCESS(
                f"Done. Exported {exported_frames} frames from {exported_videos} videos "
                f"to {output_dir}/1..5, failed/skipped {failed}."
            )
        )
