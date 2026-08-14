"""
Backfill DyVideo.content_type and media_urls from stored info.json files.
Offline maintenance only; the download worker reads the database, not info.json.
"""

import json
import os

from django.conf import settings
from django.core.management.base import BaseCommand

from dyvideo.models import DyVideo
from dyvideo.utils import format_video_data


class Command(BaseCommand):
    help = "Backfill content_type and media_urls from info.json on disk"

    def add_arguments(self, parser):
        parser.add_argument(
            "--dry-run",
            action="store_true",
            help="Print changes without saving",
        )
        parser.add_argument(
            "--vid",
            type=str,
            default="",
            help="Only backfill a single aweme vid",
        )

    def handle(self, *args, **options):
        dry_run = options["dry_run"]
        only_vid = options["vid"].strip()

        queryset = DyVideo.objects.all().order_by("id")
        if only_vid:
            queryset = queryset.filter(vid=only_vid)

        updated = 0
        skipped = 0
        missing = 0

        for video in queryset.iterator():
            info_path = os.path.join(settings.MEDIA_ROOT, video.path, "info.json")
            if not os.path.isfile(info_path):
                missing += 1
                continue

            with open(info_path, encoding="utf-8") as f:
                api_data = json.load(f)

            formatted = format_video_data(api_data, author=video.author, video_path=video.path)
            new_type = formatted.get("content_type")
            new_urls = formatted.get("media_urls") or []
            new_cover = formatted.get("cover_url") or video.cover_url

            if (
                video.content_type == new_type
                and video.media_urls == new_urls
                and video.cover_url == new_cover
            ):
                skipped += 1
                continue

            self.stdout.write(
                f"{'[DRY RUN] ' if dry_run else ''}vid={video.vid} "
                f"type {video.content_type} -> {new_type}, "
                f"media_urls {len(video.media_urls or [])} -> {len(new_urls)}"
            )

            if not dry_run:
                video.content_type = new_type
                video.media_urls = new_urls
                video.cover_url = new_cover
                video.save(update_fields=["content_type", "media_urls", "cover_url", "updated_at"])
            updated += 1

        self.stdout.write(
            self.style.SUCCESS(
                f"Done. updated={updated}, skipped={skipped}, missing_info_json={missing}"
            )
        )
