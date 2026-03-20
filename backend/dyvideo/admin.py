from django.contrib import admin
from .models import DyAuthor, DyVideo, Status


@admin.register(DyAuthor)
class DyAuthorAdmin(admin.ModelAdmin):
    """Admin configuration for DyAuthor model"""
    list_display = ['name', 'unique_id', 'uid', 'rate', 'status', 'is_valid', 'is_favor', 'crawled_at', 'created_at']
    list_filter = ['status', 'is_valid', 'is_favor', 'created_at']
    search_fields = ['name', 'unique_id', 'uid', 'sec_uid', 'school_name']
    ordering = ['-created_at']
    readonly_fields = ['created_at', 'updated_at']
    
    fieldsets = (
        ('Basic Info', {
            'fields': ('user', 'name', 'unique_id', 'uid', 'sec_uid')
        }),
        ('Profile', {
            'fields': ('avatar', 'age', 'school_name', 'ip_location')
        }),
        ('Crawling', {
            'fields': ('url', 'path', 'status', 'crawled_at')
        }),
        ('Stats', {
            'fields': ('rate', 'is_valid', 'is_favor')
        }),
        ('Timestamps', {
            'fields': ('created_at', 'updated_at'),
            'classes': ('collapse',)
        }),
    )


@admin.register(DyVideo)
class DyVideoAdmin(admin.ModelAdmin):
    """Admin configuration for DyVideo model"""
    list_display = ['name', 'vid', 'author_name', 'rate', 'is_favor', 'valid', 'created_at']
    list_filter = ['is_like', 'is_favor', 'valid', 'created_at']
    search_fields = ['name', 'vid', 'author_name', 'author_uid', 'desc']
    ordering = ['-created_at']
    readonly_fields = ['created_at', 'updated_at']
    
    fieldsets = (
        ('Basic Info', {
            'fields': ('name', 'vid', 'desc')
        }),
        ('Author', {
            'fields': ('author', 'author_name', 'author_uid', 'author_unique_id')
        }),
        ('Media', {
            'fields': ('path', 'play_src', 'cover', 'origin_url', 'cover_url')
        }),
        ('Stats', {
            'fields': ('rate', 'is_like', 'is_favor', 'valid')
        }),
        ('Timestamps', {
            'fields': ('created_at', 'updated_at'),
            'classes': ('collapse',)
        }),
    )