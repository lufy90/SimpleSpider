"""
Management command to auto-rate dyvideos using ONNX model.
- Quick (default): predict from cover image only.
- Precise (-p/--precise): extract N frames from video or slide media; for slides, fall back to cover on failure.
By default only un-rated dyvideos (rate==0) are processed; use -f to force re-rate all.
"""

import logging
import os

from django.core.management.base import BaseCommand, CommandError
from django.conf import settings as django_settings

from dyvideo.models import ContentType, DyVideo
from dyvideo.auto_rate import (
    DEFAULT_PRECISE_FRAME_COUNT,
    OnnxRatePredictor,
    get_cover_path,
)

logger = logging.getLogger(__name__)


def _parse_author_ids(values):
    """
    Parse CLI author-id tokens into a unique list of ints.
    Accepts space-separated and/or comma-separated values, e.g. 1 2 3 or 1,2,3.
    """
    if not values:
        return None
    ids = []
    for token in values:
        for part in str(token).split(","):
            part = part.strip()
            if not part:
                continue
            try:
                ids.append(int(part))
            except ValueError as e:
                raise CommandError(f"Invalid author id: {part!r}") from e
    seen = set()
    unique = []
    for author_id in ids:
        if author_id not in seen:
            seen.add(author_id)
            unique.append(author_id)
    return unique or None


class Command(BaseCommand):
    help = (
        "Auto-rate dyvideos: quick (cover image) or precise (-p, frames from video/slide media). "
        "Precise mode falls back to cover for photo_slides/video_slides when slide scoring fails. "
        "Default: only rate=0. Use -f to force re-rate. You can also pass --vid to process a specific dyvideo."
    )

    def add_arguments(self, parser):
        parser.add_argument(
            "-a",
            "--author-id",
            nargs="+",
            default=None,
            dest="author_ids",
            metavar="ID",
            help=(
                "Only process videos belonging to these authors (dyauthor ids). "
                "Accepts multiple ids: -a 1 2 3 or -a 1,2,3."
            ),
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
            help="Precise rating: score N frames from video.mp4 or slide media (images/clips). "
            "For photo_slides/video_slides, falls back to cover.jpg when precise scoring fails.",
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
        author_ids = _parse_author_ids(options["author_ids"])
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
            if author_ids is not None:
                queryset = queryset.filter(author_id__in=author_ids)

        total = queryset.count()
        if total == 0:
            if video_id is not None:
                msg = "Video not found." if DyVideo.objects.filter(id=video_id).count() == 0 else "Skipped (already rated, use -f to force)."
            elif author_ids is not None:
                msg = (
                    f"No videos found for author id(s): {', '.join(map(str, author_ids))}."
                    if force
                    else f"No un-rated videos found for author id(s): {', '.join(map(str, author_ids))}."
                )
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
        author_scope = (
            f", authors={','.join(map(str, author_ids))}" if author_ids is not None else ""
        )
        self.stdout.write(
            f"Found {len(to_process)} {scope} to process (total: {total}), mode: {mode}{author_scope}."
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
                    is_slide_content = video.content_type in (
                        ContentType.PHOTO_SLIDES,
                        ContentType.VIDEO_SLIDES,
                    )
                    cover_path = get_cover_path(media_root, video.path)

                    def on_done(rates, val):
                        r_str = "[" + ",".join(map(str, rates)) + "]"
                        self.stdout.write(
                            f"  id={video.id} path={video.path} {r_str}, max {val}."
                        )

                    try:
                        rate = predictor.predict_precise_for_dyvideo(
                            video,
                            media_root,
                            frame_count=frame_count,
                            on_precise_done=on_done,
                        )
                        method = "precise(slides)" if is_slide_content else "precise"
                        video.rate = rate
                        video.is_auto_rated = True
                        video.save(update_fields=["rate", "is_auto_rated", "updated_at"])
                        updated += 1
                        self.stdout.write(
                            f"  id={video.id} path={video.path} -> rate={rate} ({method})"
                        )
                    except Exception as e:
                        if not is_slide_content:
                            logger.exception(
                                "Precise auto-rate failed for video id=%s: %s", video.id, e
                            )
                            self.stdout.write(self.style.ERROR(f"  id={video.id} error: {e}"))
                            failed += 1
                            continue
                        logger.warning(
                            "Precise auto-rate failed for slide id=%s, falling back to cover: %s",
                            video.id,
                            e,
                        )
                        if not os.path.isfile(cover_path):
                            self.stdout.write(
                                self.style.WARNING(
                                    f"  Skip id={video.id}: precise failed and cover not found {cover_path}"
                                )
                            )
                            failed += 1
                            continue
                        try:
                            rate = predictor.predict_from_cover_path(cover_path)
                            video.rate = rate
                            video.is_auto_rated = True
                            video.save(update_fields=["rate", "is_auto_rated", "updated_at"])
                            updated += 1
                            self.stdout.write(
                                f"  id={video.id} path={video.path} -> rate={rate} (fallback(cover))"
                            )
                        except Exception as cover_error:
                            logger.exception(
                                "Cover fallback failed for video id=%s: %s",
                                video.id,
                                cover_error,
                            )
                            self.stdout.write(
                                self.style.ERROR(f"  id={video.id} fallback error: {cover_error}")
                            )
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
