from django.urls import path, include
from rest_framework.routers import DefaultRouter
from . import views

app_name = 'dyvideo'

# Create router and register viewsets
router = DefaultRouter()
router.register(r'author', views.DyAuthorView, basename='author')
router.register(r'video', views.DyVideoView, basename='video')
router.register(r'task', views.TaskView, basename='task')

urlpatterns = [
    # Include router URLs (includes all CRUD + custom actions)
    path('', include(router.urls)),
]