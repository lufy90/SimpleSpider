from django.urls import path, include
from rest_framework.routers import DefaultRouter
from . import views

app_name = 'dyvideo'

# Create router and register viewsets
router = DefaultRouter()
router.register(r'author', views.DyAuthorView, basename='author')
router.register(r'video', views.DyVideoView, basename='video')
router.register(r'task', views.TaskView, basename='task')
router.register(r'author-view', views.DyAuthorCursorView, basename='author-cursor')
router.register(r'video-view', views.DyVideoCursorView, basename='video-cursor')

urlpatterns = [
    path('', include(router.urls)),
]
