from django.apps import AppConfig


class DyvideoConfig(AppConfig):
    default_auto_field = 'django.db.models.BigAutoField'
    name = 'dyvideo'
    
    def ready(self):
        """Called when Django starts up"""
        # Task workers should be started via management command only:
        # python manage.py start_workers --max-concurrent-tasks 2
        # This avoids issues with gunicorn and other multi-process/thread request handlers
        pass
