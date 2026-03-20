"""
Management command to auto-rate dyvideos using ONNX model.
- Quick (default): predict from cover image only.
- Precise (-p/--precise): extract N frames from video, predict each, use average rate (requires video.mp4).
By default only un-rated dyvideos (rate==0) are processed; use -f to force re-rate all.
"""

import logging
import os

from django.core.management.base import BaseCommand
from django.conf import settings as django_settings

from dyvideo.models import DyVideo
from dyvideo.auto_rate import (
    DEFAULT_PRECISE_FRAME_COUNT,
    OnnxRatePredictor,
    get_cover_path,
    get_video_path,
)

logger = logging.getLogger(__name__)


class Command(BaseCommand):
    help = (
        "Auto-rate dyvideos: quick (cover image) or precise (-p, N frames from video). "
        "Default: only rate=0. Use -f to force re-rate. You can also pass --vid to process a specific dyvideo."
    )

    def add_arguments(self, parser):
        parser.add_argument(
            "-a",
            "--author-id",
            type=int,
            default=None,
            help="Only process videos belonging to this author (dyauthor id).",
        )
        parser.add_argument(
            "-i",
            "--index",
            type=int,
            default=0,
            help="Start index in the video list (default: 0).",
        )
        parser.add_argument(
            "-o",
            "--offset",
            type=int,
            default=0,
            help="Max number of videos to process (default: 0 = all from index).",
        )
        parser.add_argument(
            "--vid",
            type=int,
            default=None,
            help="Process a single dyvideo by id (overrides author/index/offset filters).",
        )
        parser.add_argument(
            "-m",
            "--model-path",
            type=str,
            default=None,
            help="Override ONNX model path (default: use AutoRateConfig.model_path).",
        )
        parser.add_argument(
            "-f",
            "--force",
            action="store_true",
            help="Force re-rate all dyvideos (default: only rate=0).",
        )
        parser.add_argument(
            "-d",
            "--dry-run",
            action="store_true",
            help="Only list which videos would be processed, do not update.",
        )
        parser.add_argument(
            "-p",
            "--precise",
            action="store_true",
            help="Precise rating: extract N frames from video, predict each, use average rate (requires video.mp4).",
        )
        parser.add_argument(
            "--frames",
            type=int,
            default=DEFAULT_PRECISE_FRAME_COUNT,
            help="Number of frames to extract for precise rating (default: %(default)s).",
        )
        parser.add_argument(
            "--file-path",
            type=str,
            default=None,
            help="Rate a single file by path only (no DB query/write). "
            "Use with quick (image) or precise (-p, video) mode.",
        )

    def handle(self, *args, **options):
        author_id = options["author_id"]
        index = options["index"]
        offset = options["offset"]
        video_id = options["vid"]
        model_path = options["model_path"]
        force = options["force"]
        dry_run = options["dry_run"]
        file_path = options["file_path"]

        if model_path is None:
            model_path = (getattr(django_settings, "AUTO_RATE_MODEL_PATH", None) or "").strip()
        if not model_path or not os.path.isfile(model_path):
            self.stdout.write(
                self.style.ERROR(
                    "ONNX model path not set or file not found. "
                    "Set AUTO_RATE_MODEL_PATH in backend/settings.py or use -m/--model-path."
                )
            )
            return

        precise = options["precise"]
        frame_count = max(1, options["frames"])
        media_root = getattr(django_settings, "MEDIA_ROOT", "/var/data/dydata/")

        if file_path:
            if not os.path.isfile(file_path):
                self.stdout.write(self.style.ERROR(f"File not found: {file_path}"))
                return

            predictor = OnnxRatePredictor(model_path)
            try:
                if precise:
                    video_exts = {".mp4", ".mkv", ".avi", ".mov", ".webm", ".mpeg", ".mpg"}
                    _, ext = os.path.splitext(file_path.lower())
                    if ext and ext not in video_exts:
                        self.stdout.write(
                            self.style.ERROR(
                                f"Precise mode requires a video file. Got extension: {ext or '(none)'}"
                            )
                        )
                        return

                    def on_done(rates, val):
                        r_str = "[" + ",".join(map(str, rates)) + "]"
                        self.stdout.write(f"  path={file_path} {r_str}, max {val}.")

                    rate = predictor.predict_from_video_path(
                        file_path,
                        frame_count=frame_count,
                        on_precise_done=on_done,
                    )
                    self.stdout.write(self.style.SUCCESS(f"rate={rate}"))
                else:
                    image_exts = {".jpg", ".jpeg", ".png", ".webp", ".bmp", ".tiff"}
                    _, ext = os.path.splitext(file_path.lower())
                    if ext and ext not in image_exts:
                        self.stdout.write(
                            self.style.ERROR(
                                f"Quick mode requires an image file. Got extension: {ext or '(none)'}"
                            )
                        )
                        return
                    rate = predictor.predict_from_cover_path(file_path)
                    self.stdout.write(self.style.SUCCESS(f"path={file_path} rate={rate}"))
            finally:
                predictor.close()
            return

        if video_id is not None:
            # When explicitly targeting a video, keep default behavior: only process rate==0 unless --force.
            queryset = DyVideo.objects.filter(id=video_id)
            if not force:
                queryset = queryset.filter(rate=0)
        else:
            if force:
                queryset = DyVideo.objects.all().order_by("id")
            else:
                queryset = DyVideo.objects.filter(rate=0).order_by("id")
            if author_id is not None:
                queryset = queryset.filter(author_id=author_id)

        total = queryset.count()
        if total == 0:
            if video_id is not None:
                msg = "Video not found." if DyVideo.objects.filter(id=video_id).count() == 0 else "Skipped (already rated, use -f to force)."
            else:
                msg = "No videos found." if force else "No un-rated videos found."
            self.stdout.write(self.style.WARNING(msg))
            return

        if video_id is not None:
            to_process = list(queryset)
        else:
            if offset > 0:
                slice_qs = queryset[index : index + offset]
            else:
                slice_qs = queryset[index:]
            to_process = list(slice_qs)

        scope = "video(s)" if force else "un-rated video(s)"
        mode = f"precise ({frame_count} frames)" if precise else "quick (cover)"
        self.stdout.write(
            f"Found {len(to_process)} {scope} to process (total: {total}), mode: {mode}."
        )
        if dry_run:
            for v in to_process:
                self.stdout.write(f"  Would process: id={v.id} path={v.path} name={v.name}")
            return

        predictor = OnnxRatePredictor(model_path)
        updated = 0
        failed = 0
        try:
            for video in to_process:
                if precise:
                    video_path = get_video_path(media_root, video.path)
                    if not os.path.isfile(video_path):
                        logger.warning("Video not found for id=%s path=%s", video.id, video.path)
                        self.stdout.write(
                            self.style.WARNING(f"  Skip id={video.id}: video not found {video_path}")
                        )
                        failed += 1
                        continue
                    try:
                        def on_done(rates, val):
                            r_str = "[" + ",".join(map(str, rates)) + "]"
                            self.stdout.write(
                                f"  id={video.id} path={video.path} {r_str}, max {val}."
                            )

                        rate = predictor.predict_from_video_path(
                            video_path,
                            frame_count=frame_count,
                            on_precise_done=on_done,
                        )
                        video.rate = rate
                        video.is_auto_rated = True
                        video.save(update_fields=["rate", "is_auto_rated", "updated_at"])
                        updated += 1
                    except Exception as e:
                        logger.exception("Precise auto-rate failed for video id=%s: %s", video.id, e)
                        self.stdout.write(self.style.ERROR(f"  id={video.id} error: {e}"))
                        failed += 1
                else:
                    cover_path = get_cover_path(media_root, video.path)
                    if not os.path.isfile(cover_path):
                        logger.warning("Cover not found for video id=%s path=%s", video.id, video.path)
                        self.stdout.write(
                            self.style.WARNING(f"  Skip id={video.id}: cover not found {cover_path}")
                        )
                        failed += 1
                        continue
                    try:
                        rate = predictor.predict_from_cover_path(cover_path)
                        video.rate = rate
                        video.is_auto_rated = True
                        video.save(update_fields=["rate", "is_auto_rated", "updated_at"])
                        updated += 1
                        self.stdout.write(f"  id={video.id} path={video.path} -> rate={rate}")
                    except Exception as e:
                        logger.exception("Auto-rate failed for video id=%s: %s", video.id, e)
                        self.stdout.write(self.style.ERROR(f"  id={video.id} error: {e}"))
                        failed += 1
        finally:
            predictor.close()

        self.stdout.write(
            self.style.SUCCESS(f"Done. Updated {updated}, failed/skipped {failed}.")
        )
