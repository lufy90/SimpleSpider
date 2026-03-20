"""
Management command to start the custom task queue worker process.
This process manages a task queue and forks threads to handle tasks.
"""

from django.core.management.base import BaseCommand
from dyvideo.task_manager import task_manager
import time
import signal
import sys


class Command(BaseCommand):
    help = 'Start the task queue worker process with configurable concurrent tasks'

    def add_arguments(self, parser):
        parser.add_argument(
            '--max-concurrent-tasks',
            type=int,
            default=2,
            help='Maximum number of concurrent tasks to process (default: 2)'
        )

    def handle(self, *args, **options):
        max_concurrent_tasks = options['max_concurrent_tasks']
        
        self.stdout.write(
            self.style.SUCCESS(
                f'Starting task queue worker process with max_concurrent_tasks={max_concurrent_tasks}...'
            )
        )
        
        # Setup signal handlers for graceful shutdown
        def signal_handler(sig, frame):
            self.stdout.write(
                self.style.WARNING('\nReceived interrupt signal, stopping workers...')
            )
            task_manager.stop_workers()
            self.stdout.write(
                self.style.SUCCESS('Workers stopped successfully!')
            )
            sys.exit(0)
        
        signal.signal(signal.SIGINT, signal_handler)
        signal.signal(signal.SIGTERM, signal_handler)
        
        try:
            # Start the task processing system
            task_manager.start_workers(max_concurrent_tasks)
            
            self.stdout.write(
                self.style.SUCCESS('Task queue worker process started successfully!')
            )
            self.stdout.write(f'Max concurrent tasks: {max_concurrent_tasks}')
            self.stdout.write('Press Ctrl+C to stop the process')
            
            # Keep the command running and show stats periodically
            stats_interval = 30  # Show stats every 30 seconds
            last_stats_time = time.time()
            
            while True:
                time.sleep(1)
                
                # Show queue stats periodically
                current_time = time.time()
                if current_time - last_stats_time >= stats_interval:
                    stats = task_manager.get_queue_stats()
                    self.stdout.write(
                        f'\nQueue Stats: Pending={stats.get("pending", 0)}, '
                        f'Running={stats.get("running", 0)}, '
                        f'Active={stats.get("active_tasks", 0)}, '
                        f'Queue Size={stats.get("queue_size", 0)}'
                    )
                    last_stats_time = current_time
                
        except KeyboardInterrupt:
            signal_handler(None, None)
        except Exception as e:
            self.stdout.write(
                self.style.ERROR(f'Error: {e}')
            )
            task_manager.stop_workers()
            raise
