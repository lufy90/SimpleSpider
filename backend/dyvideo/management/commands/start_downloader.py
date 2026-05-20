"""
Management command to run an independent download worker process.
This worker continuously downloads media for DyVideo objects with valid=False.
"""

import signal
import sys
import time

from django.core.management.base import BaseCommand
from django.db import close_old_connections
from django.utils import timezone
from datetime import timedelta

from dyvideo.models import DyVideo, Status
from dyvideo.utils import download_videos


class Command(BaseCommand):
    help = "Start independent download worker for videos with valid=False"

    def _claim_pending_video_ids(self, batch_size: int, time_threshold):
        """
        Claim a batch of pending videos for this worker by atomically switching
        status waiting -> running. This avoids two workers downloading same video.
        """
        candidate_ids = list(
            DyVideo.objects.filter(
                valid=False,
                created_at__gte=time_threshold,
                status=Status.WAITING,
            )
            .exclude(origin_url="")
            .order_by("created_at", "id")
            .values_list("id", flat=True)[:batch_size]
        )

        if not candidate_ids:
            return []

        now = timezone.now()
        claimed_ids = []
        for video_id in candidate_ids:
            updated = DyVideo.objects.filter(
                id=video_id,
                valid=False,
                status=Status.WAITING,
            ).update(status=Status.RUNNING, updated_at=now)
            if updated:
                claimed_ids.append(video_id)

        return claimed_ids

    def add_arguments(self, parser):
        parser.add_argument(
            "--poll-interval",
            type=int,
            default=3,
            help="Polling interval in seconds when no pending videos found (default: 3)",
        )
        parser.add_argument(
            "--batch-size",
            type=int,
            default=20,
            help="Max number of pending videos handled per loop (default: 20)",
        )
        parser.add_argument(
            "--max-workers",
            type=int,
            default=3,
            help="Max concurrent download threads inside one batch (default: 3)",
        )
        parser.add_argument(
            "--once",
            action="store_true",
            help="Run one batch and exit",
        )

    def handle(self, *args, **options):
        poll_interval = max(1, options["poll_interval"])
        batch_size = max(1, options["batch_size"])
        max_workers = max(1, options["max_workers"])
        run_once = options["once"]
        running = True

        self.stdout.write(
            self.style.SUCCESS(
                f"Starting download worker: poll_interval={poll_interval}s, "
                f"batch_size={batch_size}, max_workers={max_workers}, once={run_once}"
            )
        )

        def stop_handler(sig, frame):
            nonlocal running
            running = False
            self.stdout.write(self.style.WARNING("\nStopping download worker..."))

        signal.signal(signal.SIGINT, stop_handler)
        signal.signal(signal.SIGTERM, stop_handler)

        while running:
            close_old_connections()

            time_threshold = timezone.now() - timedelta(hours=24)

            pending_ids = self._claim_pending_video_ids(batch_size, time_threshold)

            if not pending_ids:
                self.stdout.write("No pending videos (valid=False).")
                if run_once:
                    break
                time.sleep(poll_interval)
                continue

            self.stdout.write(
                f"Processing {len(pending_ids)} claimed videos (ids: {pending_ids[0]}..{pending_ids[-1]})"
            )
            results = download_videos(pending_ids, max_workers=max_workers)
            success_count = sum(1 for r in results if r.get("status") == "success")
            error_count = len(results) - success_count
            self.stdout.write(
                self.style.SUCCESS(
                    f"Batch finished. success={success_count}, error={error_count}"
                )
            )

            if run_once:
                break

        self.stdout.write(self.style.SUCCESS("Download worker stopped."))
        if not running:
            sys.exit(0)
