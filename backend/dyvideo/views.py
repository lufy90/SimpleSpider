from rest_framework import status
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework.viewsets import ModelViewSet
from rest_framework.decorators import action
from rest_framework.permissions import IsAuthenticated
from django_filters.rest_framework import DjangoFilterBackend
from rest_framework.filters import SearchFilter, OrderingFilter

from .models import DyAuthor, DyVideo, Task, TaskType
from .serializers import (
    DyAuthorSerializer, DyVideoSerializer, CrawlSerializer, 
    DyVideoDownloadSerializer, TaskSerializer, CrawlByUrlSerializer,
    CrawlAllSerializer
)
from .task_manager import task_manager



class DyAuthorView(ModelViewSet):
    """Complete CRUD operations for Douyin authors"""
    serializer_class = DyAuthorSerializer
    permission_classes = [IsAuthenticated]
    filter_backends = [DjangoFilterBackend, SearchFilter, OrderingFilter]
    filterset_fields = ['rate', 'status', 'is_valid', 'is_favor']
    search_fields = ['name', 'desc', 'unique_id', 'path', 'url', 'uid', 'used_names', 'signature_sec_uid']
    ordering_fields = ['created_at', 'updated_at', 'rate', 'name']
    ordering = ['-created_at']
    
    def get_queryset(self):
        return DyAuthor.objects.all()
    
    @action(detail=False, methods=['post'], url_path='crawl')
    def crawl(self, request):
        """Start crawling for authors - merged API for crawl_multi and crawl_all"""
        user = request.user
        serializer = CrawlSerializer(data=request.data)
        
        if serializer.is_valid():
            ids = serializer.validated_data.get('ids')
            start_page = serializer.validated_data.get('start_page', 1)
            page_size = serializer.validated_data.get('page_size', 30)
            min_rate = serializer.validated_data.get('min_rate', 0)
            multipage = serializer.validated_data.get('multipage', False)
            page_count = serializer.validated_data.get('page_count', 0) or 0
            num_crawl_workers = serializer.validated_data.get('num_crawl_workers', 0)
            num_save_workers = serializer.validated_data.get('num_save_workers', 3)
            browser_size = serializer.validated_data.get('browser_size', (1920, 1080))
            headless = serializer.validated_data.get('headless', True)
            
            # Normalize values
            if not start_page:
                start_page = 1
            if not min_rate:
                min_rate = 0
            if not page_size:
                page_size = 30
            # if not num_crawl_workers:
            #     num_crawl_workers = 2
            if not num_save_workers:
                num_save_workers = 3
            
            # Filter authors based on whether ids are provided
            if ids and len(ids) > 0:
                # If ids provided, filter by those IDs
                authors = DyAuthor.objects.filter(id__in=ids)
                if not authors.exists():
                    return Response(
                        {'error': f'Authors not found: {ids}'}, 
                        status=status.HTTP_404_NOT_FOUND
                    )
            else:
                # If no ids, filter by min_rate and pagination
                authors = DyAuthor.objects.filter(rate__gte=min_rate)
                # Apply pagination
                start_index = (start_page - 1) * page_size
                authors = authors[start_index*page_size:]
                
                if not authors.exists():
                    return Response(
                        {'error': f'No authors at page {start_page} with min_rate >= {min_rate}'}, 
                        status=status.HTTP_404_NOT_FOUND
                    )
                        
            # Get author IDs list
            author_ids = list(authors.values_list('id', flat=True))
            
            # Start crawling task using custom task queue
            task = task_manager.create_task(
                task_type=TaskType.CRAWL_AUTHORS,
                parameters={
                    'authors': author_ids,
                    'multipage': multipage,
                    'page_count': page_count,
                    'batch_size': page_size,
                    'num_crawl_workers': num_crawl_workers,
                    'num_save_workers': num_save_workers,
                    'user': user.id,
                    'browser_size': browser_size,
                    'headless': headless,
                },
                user=user
            )
            
            return Response({
                'task_id': task.id,
                'msg': f'Added to crawling queue. Author count: {authors.count()}',
                'authors': authors.values('id', 'name', 'unique_id', 'path', 'url', 'uid')
            })
        
        return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)
    

class DyVideoView(ModelViewSet):
    """Complete CRUD operations for Douyin videos"""
    serializer_class = DyVideoSerializer
    permission_classes = [IsAuthenticated]
    filter_backends = [DjangoFilterBackend, SearchFilter, OrderingFilter]
    filterset_fields = ['author', 'rate', 'is_like', 'is_favor', 'valid', 'is_auto_rated']
    search_fields = ['name', 'desc', 'author_name', 'path']
    ordering_fields = ['created_at', 'updated_at', 'rate', 'name']
    ordering = ['-created_at']

    def _is_random_request(self):
        value = (self.request.query_params.get('random') or '').strip().lower()
        return value in {'true', '1', 'yes', 'on'}
    
    def get_queryset(self):
        return DyVideo.objects.all()

    def filter_queryset(self, queryset):
        random_mode = self._is_random_request()
        for backend in list(self.filter_backends):
            if random_mode and backend is OrderingFilter:
                continue
            queryset = backend().filter_queryset(self.request, queryset, self)

        if random_mode:
            queryset = queryset.order_by('?')
        return queryset
    
    @action(detail=False, methods=['post'], url_path='download')
    def download_videos(self, request):
        """Download videos"""
        serializer = DyVideoDownloadSerializer(data=request.data)
        user = request.user
        max_workers = request.data.get('max_workers', 3)
        if serializer.is_valid():
            ids = serializer.validated_data['ids']
            
            # Get videos
            videos = DyVideo.objects.filter(id__in=ids)
            if not videos.exists():
                return Response(
                    {'error': 'Videos not found'}, 
                    status=status.HTTP_404_NOT_FOUND
                )
            
            # Get video IDs list
            video_ids = list(videos.values_list('id', flat=True))
            
            # Start download task using custom task queue
            task = task_manager.create_task(
                task_type=TaskType.DOWNLOAD_VIDEOS,
                parameters={
                    'videos': video_ids,
                    'max_workers': max_workers,
                    'user': user.id
                },
                user=user
            )
            
            return Response({
                'task_id': task.id,
                'ids': ids, 
                'msg': f'Download started for {videos.count()} videos'
            })
        return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)
    
    @action(detail=False, methods=['post'], url_path='crawl')
    def crawl(self, request):
        """Crawl video by URL"""
        serializer = CrawlByUrlSerializer(data=request.data)
        user = request.user
        
        if serializer.is_valid():
            url = serializer.validated_data['url']
            multipage = serializer.validated_data.get('multipage', False)
            page_count = serializer.validated_data.get('page_count', 0) or 0
            num_save_workers = serializer.validated_data.get('num_save_workers', 3)
            browser_size = serializer.validated_data.get('browser_size', (1920, 1080))
            headless = serializer.validated_data.get('headless', True)
            exp_resp_urls = serializer.validated_data.get('exp_resp_urls')
            
            # Normalize values
            if not num_save_workers:
                num_save_workers = 3
            if not browser_size:
                browser_size = (1920, 1080)
            
            # Create task
            task = task_manager.create_task(
                task_type=TaskType.CRAWL_BY_URL,
                parameters={
                    'url': url,
                    'multipage': multipage,
                    'page_count': page_count,
                    'num_save_workers': num_save_workers,
                    'browser_size': browser_size,
                    'headless': headless,
                    'user': user.id,
                    'exp_resp_urls': exp_resp_urls,
                },
                user=user
            )
            
            return Response({
                'task_id': task.id,
                'url': url,
                'msg': f'Crawl task created for URL: {url}'
            })
        
        return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)


class TaskView(ModelViewSet):
    """Task management and monitoring"""
    permission_classes = [IsAuthenticated]
    serializer_class = TaskSerializer
    filter_backends = [DjangoFilterBackend, SearchFilter, OrderingFilter]
    filterset_fields = ['task_type', 'status', 'priority']
    search_fields = ['task_type', 'status', 'worker_id']
    ordering_fields = ['created_at', 'priority', 'status']
    ordering = ['-created_at']
    
    def get_queryset(self):
        return Task.objects.all()
    
    @action(detail=False, methods=['get'], url_path='status/(?P<task_id>[^/.]+)')
    def get_task_status(self, request, task_id=None):
        """Get status of a specific task"""
        try:
            task_status = task_manager.get_task_status(int(task_id))
            if task_status:
                return Response(task_status)
            else:
                return Response(
                    {'error': 'Task not found'}, 
                    status=status.HTTP_404_NOT_FOUND
                )
        except ValueError:
            return Response(
                {'error': 'Invalid task ID'}, 
                status=status.HTTP_400_BAD_REQUEST
            )
    
    @action(detail=False, methods=['get'], url_path='queue-stats')
    def get_queue_stats(self, request):
        """Get queue statistics"""
        stats = task_manager.get_queue_stats()
        return Response(stats)
    
    @action(detail=False, methods=['post'], url_path='crawl-all')
    def crawl_all(self, request):
        """Crawl all authors matching the specified filters"""
        serializer = CrawlAllSerializer(data=request.data)
        user = request.user
        
        if serializer.is_valid():
            # Get filter parameters
            rate__gte = serializer.validated_data.get('rate__gte')
            rate__lte = serializer.validated_data.get('rate__lte')
            status = serializer.validated_data.get('status')
            is_valid = serializer.validated_data.get('is_valid')
            is_favor = serializer.validated_data.get('is_favor')
            
            # Get crawl parameters
            multipage = serializer.validated_data.get('multipage', False)
            page_count = serializer.validated_data.get('page_count', 0) or 0
            num_crawl_workers = serializer.validated_data.get('num_crawl_workers', 0)
            num_save_workers = serializer.validated_data.get('num_save_workers', 3)
            browser_size = serializer.validated_data.get('browser_size', (1920, 1080))
            headless = serializer.validated_data.get('headless', True)
            
            # Normalize values
            if not num_save_workers:
                num_save_workers = 3
            if not browser_size:
                browser_size = (1920, 1080)
            
            # Build query filter
            authors = DyAuthor.objects.all()
            
            # Apply filters
            if rate__gte is not None:
                authors = authors.filter(rate__gte=rate__gte)
            if rate__lte is not None:
                authors = authors.filter(rate__lte=rate__lte)
            if status is not None:
                authors = authors.filter(status=status)
            if is_valid is not None:
                authors = authors.filter(is_valid=is_valid)
            if is_favor is not None:
                authors = authors.filter(is_favor=is_favor)
            
            # Check if any authors match
            author_count = authors.count()
            if author_count == 0:
                return Response(
                    {'error': 'No authors match the specified filters'}, 
                    status=status.HTTP_404_NOT_FOUND
                )
            
            # Get author IDs list
            author_ids = list(authors.values_list('id', flat=True))
            
            # Update author status to waiting
            authors.update(status='waiting')
            
            # Create task
            task = task_manager.create_task(
                task_type=TaskType.CRAWL_AUTHORS,
                parameters={
                    'authors': author_ids,
                    'multipage': multipage,
                    'page_count': page_count,
                    'num_crawl_workers': num_crawl_workers,
                    'num_save_workers': num_save_workers,
                    'user': user.id,
                    'browser_size': browser_size,
                    'headless': headless,
                },
                user=user
            )
            
            return Response({
                'task_id': task.id,
                'msg': f'Crawl all task created for {author_count} authors',
                'author_count': author_count,
                'filters': {
                    'rate__gte': rate__gte,
                    'rate__lte': rate__lte,
                    'status': status,
                    'is_valid': is_valid,
                    'is_favor': is_favor,
                }
            })
        
        return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)
