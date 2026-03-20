"""
Management command to start the custom task queue workers.
"""

from django.core.management.base import BaseCommand
from dyvideo.task_manager import task_manager


class Command(BaseCommand):
    help = 'Start custom task queue workers'

    def add_arguments(self, parser):
        parser.add_argument(
            '--workers',
            type=int,
            default=2,
            help='Number of workers to start (default: 2)'
        )

    def handle(self, *args, **options):
        num_workers = options['workers']
        
        self.stdout.write(
            self.style.SUCCESS(f'Starting {num_workers} task queue workers...')
        )
        
        try:
            task_manager.start_workers(num_workers)
            
            self.stdout.write(
                self.style.SUCCESS('Workers started successfully!')
            )
            self.stdout.write('Press Ctrl+C to stop workers')
            
            # Keep the command running
            import time
            while True:
                time.sleep(1)
                
        except KeyboardInterrupt:
            self.stdout.write(
                self.style.WARNING('\nStopping workers...')
            )
            task_manager.stop_workers()
            self.stdout.write(
                self.style.SUCCESS('Workers stopped successfully!')
            )
