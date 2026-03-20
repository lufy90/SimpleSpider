from django.db import models
from django.contrib.auth.models import User
from django.conf import settings
from django.utils import timezone
import os


class Status(models.TextChoices):
    READY = 'ready', 'Ready'
    WAITING = 'waiting', 'Waiting'
    RUNNING = 'running', 'Running'
    ERROR = 'error', 'Error'


class OldAuthor(models.Model):
    class Meta:
        managed = False
        db_table = "dyauthor"

class DyAuthor(models.Model):
    """Douyin Author model"""
    url = models.URLField(unique=True, null=True, blank=True)
    path = models.CharField(max_length=255, default="")
    is_valid = models.BooleanField(default=True)
    rate = models.IntegerField(default=0)
    name = models.CharField(max_length=255, default="", null=True, blank=True)
    used_names = models.TextField(default="", blank=True, help_text="Comma-separated list of names used by this author")
    sec_uid = models.CharField(max_length=255, default="", null=True, blank=True)
    signature_sec_uid = models.CharField(max_length=255, default="", null=True, blank=True)
    age = models.IntegerField(default=0)
    unique_id = models.CharField(max_length=255, unique=True, null=True, blank=True)
    uid = models.CharField(max_length=255, unique=True, null=True, blank=True)
    school_name = models.CharField(max_length=255, default="", null=True, blank=True)
    ip_location = models.CharField(max_length=255, default="", null=True, blank=True)
    status = models.CharField(max_length=20, choices=Status.choices, default=Status.READY)
    is_favor = models.BooleanField(default=False)
    is_like = models.BooleanField(default=False)
    desc = models.TextField(default="")
    created_at = models.DateTimeField(auto_now_add=True)
    created_by = models.ForeignKey(User, on_delete=models.CASCADE, related_name='created_dyauthors', null=True, blank=True)
    updated_at = models.DateTimeField(auto_now=True)
    updated_by = models.ForeignKey(User, on_delete=models.CASCADE, related_name='updated_dyauthors', null=True, blank=True)
    crawled_at = models.DateTimeField(null=True, blank=True)
    crawled_by = models.ForeignKey(User, on_delete=models.CASCADE, related_name='crawled_dyauthors', null=True, blank=True)
    
    class Meta:
        db_table = 'dyauthor'
        ordering = ['-created_at']
    
    def __str__(self):
        return f"{self.name} ({self.unique_id})"


class DyVideo(models.Model):
    """Douyin Video model"""
    path = models.CharField(max_length=255, default="")
    valid = models.BooleanField(default=False)
    rate = models.IntegerField(default=0)
    is_auto_rated = models.BooleanField(default=False, help_text="True if rate was set by AI; False if by user")
    name = models.CharField(max_length=255, default="")
    vid = models.CharField(max_length=255, unique=True, null=True, blank=True, db_index=True)
    author = models.ForeignKey(DyAuthor, on_delete=models.CASCADE, related_name='videos', null=True, blank=True)
    author_unique_id = models.CharField(max_length=255, default="")
    author_uid = models.CharField(max_length=255, default="")
    author_name = models.CharField(max_length=255, default="")
    is_like = models.BooleanField(default=False)
    is_favor = models.BooleanField(default=False)
    desc = models.TextField(blank=True, default="")
    origin_url = models.CharField(max_length=2000, default="")
    cover_url = models.CharField(max_length=2000, default="")
    created_at = models.DateTimeField(auto_now_add=True)
    created_by = models.ForeignKey(User, on_delete=models.CASCADE, related_name='created_dyvideos', null=True, blank=True)
    updated_at = models.DateTimeField(auto_now=True)
    updated_by = models.ForeignKey(User, on_delete=models.CASCADE, related_name='updated_dyvideos', null=True, blank=True)
    size = models.IntegerField(default=0)
    
    class Meta:
        db_table = 'dyvideo'
        ordering = ['-created_at']
    
    def __str__(self):
        return f"{self.name} ({self.vid})"
    
    def save(self, *args, **kwargs):
        # Update valid status based on file existence
        play_src = os.path.join(settings.MEDIA_ROOT, self.path, "video.mp4")
        self.valid = os.path.isfile(play_src) and os.path.getsize(play_src) > 0
        super().save(*args, **kwargs)


class TaskType(models.TextChoices):
    CRAWL_AUTHORS = 'crawl_authors', 'Crawl Authors'
    DOWNLOAD_VIDEOS = 'download_videos', 'Download Videos'
    UPDATE_VALIDITY = 'update_validity', 'Update Video Validity'
    CRAWL_BY_URL = 'crawl_by_url', 'Crawl By URL'


class TaskStatus(models.TextChoices):
    PENDING = 'pending', 'Pending'
    RUNNING = 'running', 'Running'
    COMPLETED = 'completed', 'Completed'
    FAILED = 'failed', 'Failed'
    CANCELLED = 'cancelled', 'Cancelled'


class Task(models.Model):
    """Custom task queue model"""
    task_type = models.CharField(max_length=50, choices=TaskType.choices)
    status = models.CharField(max_length=20, choices=TaskStatus.choices, default=TaskStatus.PENDING)
    priority = models.IntegerField(default=5, help_text="Higher number = higher priority")
    
    # Task parameters (stored as JSON)
    parameters = models.JSONField(default=dict, help_text="Task parameters")
    
    # Progress tracking
    total_items = models.IntegerField(default=0, help_text="Total items to process")
    processed_items = models.IntegerField(default=0, help_text="Items processed")
    error_items = models.IntegerField(default=0, help_text="Items with error")
    
    # Results and error handling
    result = models.JSONField(default=dict, help_text="Task result data")
    error_message = models.TextField(blank=True, help_text="Error message if task failed")
    
    # Worker information
    worker_id = models.CharField(max_length=100, blank=True, help_text="ID of worker processing this task")
    started_at = models.DateTimeField(null=True, blank=True)
    completed_at = models.DateTimeField(null=True, blank=True)
    
    # User tracking
    created_by = models.ForeignKey(User, on_delete=models.CASCADE, related_name='created_tasks', null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
    
    class Meta:
        db_table = 'task'
        ordering = ['-priority', 'created_at']
        indexes = [
            models.Index(fields=['status', 'priority']),
            models.Index(fields=['task_type', 'status']),
        ]
    
    def __str__(self):
        return f"{self.task_type} - {self.status} ({self.id})"
    
    @property
    def is_completed(self):
        return self.status == TaskStatus.COMPLETED
    
    @property
    def is_failed(self):
        return self.status == TaskStatus.FAILED
    
    @property
    def is_running(self):
        return self.status == TaskStatus.RUNNING
    
    @property
    def is_pending(self):
        return self.status == TaskStatus.PENDING
    
    def mark_as_running(self, worker_id):
        """Mark task as running"""
        
        self.status = TaskStatus.RUNNING
        self.worker_id = worker_id
        self.started_at = timezone.now()
        self.save(update_fields=["status", "worker_id", "started_at", "updated_at"])
    
    def mark_as_completed(self, result=None):
        """Mark task as completed"""
        self.status = TaskStatus.COMPLETED
        self.completed_at = timezone.now()
        if result:
            self.result = result
        update_fields = ["status", "completed_at", "updated_at"]
        if result:
            update_fields.append("result")
        self.save(update_fields=update_fields)
    
    def mark_as_failed(self, error_message):
        """Mark task as failed"""
        self.status = TaskStatus.FAILED
        self.completed_at = timezone.now()
        self.error_message = error_message
        self.save(update_fields=["status", "completed_at", "error_message", "updated_at"])

    def increase_processed_count(self):
        """Increase processed items count"""
        self.processed_items += 1
        self.save()

    def increase_error_count(self):
        """Increase error items count"""
        self.error_items += 1
        self.save()
