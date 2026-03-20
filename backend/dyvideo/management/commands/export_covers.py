"""
Management command to export video covers for training.
Finds DyVideos with is_auto_rated=False and rate in 1..5, copies cover.jpg to dataset/1/, dataset/2/, ... dataset/5/.
"""

import logging
import os
import shutil

from django.core.management.base import BaseCommand
from django.conf import settings as django_settings

from dyvideo.models import DyVideo
from dyvideo.auto_rate import get_cover_path

logger = logging.getLogger(__name__)

RATES = (1, 2, 3, 4, 5)


class Command(BaseCommand):
    help = (
        "Export covers of user-rated (not is_auto_rated) videos to dataset by rate: "
        "rate=1 -> output_dir/1/, rate=2 -> output_dir/2/, ... rate=5 -> output_dir/5/. "
        "Cover source: MEDIA_ROOT/video.path/cover.jpg."
    )

    def add_arguments(self, parser):
        parser.add_argument(
            "-o",
            "--output-dir",
            type=str,
            default="dataset",
            help="Base output directory; subdirs 1,2,3,4,5 will be created (default: dataset).",
        )
        parser.add_argument(
            "-d",
            "--dry-run",
            action="store_true",
            help="Only list which videos would be exported, do not copy.",
        )

    def handle(self, *args, **options):
        output_dir = options["output_dir"]
        dry_run = options["dry_run"]

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
            f"Found {total} video(s) to export (is_auto_rated=False, rate in {RATES})."
        )

        if dry_run:
            for v in videos:
                cover_path = get_cover_path(media_root, v.path)
                subdir = os.path.join(output_dir, str(v.rate))
                self.stdout.write(
                    f"  Would export: id={v.id} rate={v.rate} -> {subdir}/{v.id}.jpg"
                )
                if not os.path.isfile(cover_path):
                    self.stdout.write(
                        self.style.WARNING(f"    (cover not found: {cover_path})")
                    )
            return

        exported = 0
        failed = 0

        for video in videos:
            cover_path = get_cover_path(media_root, video.path)
            if not os.path.isfile(cover_path):
                logger.warning(
                    "Cover not found for video id=%s path=%s",
                    video.id,
                    video.path,
                )
                self.stdout.write(
                    self.style.WARNING(
                        f"  Skip id={video.id}: cover not found {cover_path}"
                    )
                )
                failed += 1
                continue
            rate_dir = os.path.join(output_dir, str(video.rate))
            os.makedirs(rate_dir, exist_ok=True)
            dest = os.path.join(rate_dir, f"{video.id}.jpg")
            try:
                shutil.copy2(cover_path, dest)
                exported += 1
                self.stdout.write(f"  id={video.id} rate={video.rate} -> {dest}")
            except Exception as e:
                logger.exception(
                    "Export failed for video id=%s: %s", video.id, e
                )
                self.stdout.write(
                    self.style.ERROR(f"  id={video.id} error: {e}")
                )
                failed += 1

        self.stdout.write(
            self.style.SUCCESS(
                f"Done. Exported {exported} to {output_dir}/1..5, failed/skipped {failed}."
            )
        )
