from rest_framework import serializers
from django.conf import settings
from .models import DyAuthor, DyVideo, Task


class DyAuthorSerializer(serializers.ModelSerializer):
    """Serializer for DyAuthor model"""
    avatar_src = serializers.SerializerMethodField()
    
    class Meta:
        model = DyAuthor
        fields = '__all__'
    
    def get_avatar_src(self, obj):
        """Generate absolute URL for author avatar"""
        if not obj.path:
            return None
        
        path = obj.path.rstrip('/')
        avatar_path = f"{path}/avatar.jpg"
        request = self.context.get('request')
        
        if request:
            return request.build_absolute_uri(f"{settings.MEDIA_URL}{avatar_path}")
        else:
            return f"{settings.MEDIA_URL}{avatar_path}"


class DyVideoSerializer(serializers.ModelSerializer):
    """Serializer for DyVideo model"""
    cover_src = serializers.SerializerMethodField()
    play_src = serializers.SerializerMethodField()
    
    class Meta:
        model = DyVideo
        fields = '__all__'
    
    def get_cover_src(self, obj):
        """Generate absolute URL for video cover"""
        if not obj.path:
            return None
        
        path = obj.path.rstrip('/')
        cover_path = f"{path}/cover.jpg"
        request = self.context.get('request')
        
        if request:
            return request.build_absolute_uri(f"{settings.MEDIA_URL}{cover_path}")
        else:
            return f"{settings.MEDIA_URL}{cover_path}"
    
    def get_play_src(self, obj):
        """Generate absolute URL for video file"""
        if not obj.path:
            return None
        
        path = obj.path.rstrip('/')
        video_path = f"{path}/video.mp4"
        request = self.context.get('request')
        
        if request:
            return request.build_absolute_uri(f"{settings.MEDIA_URL}{video_path}")
        else:
            return f"{settings.MEDIA_URL}{video_path}"

    def update(self, instance, validated_data):
        if "rate" in validated_data:
            validated_data["is_auto_rated"] = False
        return super().update(instance, validated_data)


class CrawlSerializer(serializers.Serializer):
    """Merged serializer for crawl authors request"""
    ids = serializers.ListField(child=serializers.IntegerField(), required=False, allow_null=True)
    start_page = serializers.IntegerField(default=1, required=False)
    page_size = serializers.IntegerField(default=30, required=False)
    min_rate = serializers.IntegerField(default=0, required=False)
    multipage = serializers.BooleanField(default=False, required=False)
    page_count = serializers.IntegerField(default=0, required=False)
    num_crawl_workers = serializers.IntegerField(default=0, required=False)
    num_save_workers = serializers.IntegerField(default=3, required=False)
    browser_size = serializers.ListField(child=serializers.IntegerField(), default=(1920, 1080), required=False)
    headless = serializers.BooleanField(default=True, required=False)

class DyVideoDownloadSerializer(serializers.Serializer):
    """Serializer for video download request"""
    ids = serializers.ListField(child=serializers.IntegerField())

class CrawlByUrlSerializer(serializers.Serializer):
    """Serializer for crawl by URL request"""
    url = serializers.CharField(max_length=2000, required=True)
    multipage = serializers.BooleanField(default=False, required=False)
    page_count = serializers.IntegerField(default=0, required=False)
    num_save_workers = serializers.IntegerField(default=3, required=False)
    browser_size = serializers.ListField(child=serializers.IntegerField(), default=(1920, 1080), required=False)
    headless = serializers.BooleanField(default=True, required=False)
    exp_resp_urls = serializers.ListField(child=serializers.CharField(), required=False, allow_null=True)

class CrawlAllSerializer(serializers.Serializer):
    """Serializer for crawl all authors request with filters"""
    # Filter fields
    rate__gte = serializers.IntegerField(required=False, allow_null=True, help_text="Filter by minimum rate")
    rate__lte = serializers.IntegerField(required=False, allow_null=True, help_text="Filter by maximum rate")
    status = serializers.CharField(required=False, allow_null=True, help_text="Filter by status")
    is_valid = serializers.BooleanField(required=False, allow_null=True, help_text="Filter by is_valid")
    is_favor = serializers.BooleanField(required=False, allow_null=True, help_text="Filter by is_favor")
    
    # Crawl parameters
    multipage = serializers.BooleanField(default=False, required=False)
    page_count = serializers.IntegerField(default=0, required=False)
    num_crawl_workers = serializers.IntegerField(default=0, required=False)
    num_save_workers = serializers.IntegerField(default=3, required=False)
    browser_size = serializers.ListField(child=serializers.IntegerField(), default=(1920, 1080), required=False)
    headless = serializers.BooleanField(default=True, required=False)

class TaskSerializer(serializers.ModelSerializer):
    """Serializer for Task model"""
    progress = serializers.SerializerMethodField()
    
    class Meta:
        model = Task
        fields = '__all__'
    
    def get_progress(self, obj):
        """Calculate progress percentage based on processed_items and total_items"""
        if obj.total_items > 0:
            return int(((obj.processed_items + obj.error_items) / obj.total_items) * 100)
        return 0
