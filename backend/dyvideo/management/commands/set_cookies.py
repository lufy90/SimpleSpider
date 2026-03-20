"""
Management command to set cookies by opening a headed browser for user to log in.
"""

from django.core.management.base import BaseCommand
from dyvideo.utils import set_cookies


class Command(BaseCommand):
    help = 'Set cookies by opening a headed browser and waiting for user to log in'

    def add_arguments(self, parser):
        parser.add_argument(
            '--url',
            type=str,
            default='https://www.douyin.com',
            help='URL to navigate to (default: https://www.douyin.com)'
        )
        parser.add_argument(
            '--driver',
            type=str,
            default='firefox',
            choices=['firefox', 'chromium', 'webkit'],
            help='Browser driver to use (default: firefox)'
        )
        parser.add_argument(
            '--timeout',
            type=int,
            default=300000,
            help='Timeout in milliseconds (default: 300000 = 5 minutes)'
        )
        parser.add_argument(
            '--fname',
            type=str,
            default=None,
            help='Cookie file path (default: use default cookie file location)'
        )

    def handle(self, *args, **options):
        url = options['url']
        driver = options['driver']
        timeout = options['timeout']
        fname = options['fname']
        
        self.stdout.write(
            self.style.SUCCESS('Starting browser to set cookies...')
        )
        self.stdout.write(f'URL: {url}')
        self.stdout.write(f'Driver: {driver}')
        self.stdout.write(f'Timeout: {timeout}ms ({timeout // 1000 // 60} minutes)')
        self.stdout.write('')
        self.stdout.write(
            self.style.WARNING('A browser window will open. Please log in manually.')
        )
        self.stdout.write(
            'The command will wait for the login indicator to appear...'
        )
        self.stdout.write('')
        
        try:
            success = set_cookies(url=url, fname=fname, driver=driver, timeout=timeout)
            
            if success:
                self.stdout.write(
                    self.style.SUCCESS('Cookies saved successfully!')
                )
            else:
                self.stdout.write(
                    self.style.ERROR('Failed to save cookies. Please check the logs.')
                )
                
        except KeyboardInterrupt:
            self.stdout.write(
                self.style.WARNING('\nOperation cancelled by user.')
            )
        except Exception as e:
            self.stdout.write(
                self.style.ERROR(f'Error occurred: {e}')
            )
            raise

