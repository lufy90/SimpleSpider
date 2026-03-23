"""
Custom task queue manager for handling background tasks.
Replaces Celery with a simple database-based task queue.
"""

import logging
import threading
import time
import os
import queue
from typing import Optional, Dict, Any
from django.db import transaction, close_old_connections
from django.utils import timezone
from .models import Task, TaskType, TaskStatus, DyAuthor, DyVideo
from .utils import crawl_authors_batch, download_videos, crawl_by_url

logger = logging.getLogger(__name__)


class TaskManager:
    """Manages the custom task queue with process-internal queue and thread pool"""
    
    def __init__(self):
        self.task_queue = queue.Queue()
        self.running = False
        self.worker_threads = []
        self.main_loop_thread = None
        self.max_concurrent_tasks = 2
        self.active_tasks = {}  # Track active tasks: {task_id: thread}
        self.active_tasks_lock = threading.Lock()
    
    def create_task(self, task_type: str, parameters: Dict[str, Any], 
                   priority: int = 5, user=None) -> Task:
        """Create a new task and save it to database (status will be PENDING)"""
        with transaction.atomic():
            task = Task.objects.create(
                task_type=task_type,
                parameters=parameters,
                priority=priority,
                created_by=user
            )
            
            # Set total items based on task type
            if task_type == TaskType.CRAWL_AUTHORS:
                author_ids = parameters.get('authors', [])
                task.total_items = len(author_ids)
            elif task_type == TaskType.DOWNLOAD_VIDEOS:
                video_ids = parameters.get('videos', [])
                task.total_items = len(video_ids)
            elif task_type == TaskType.CRAWL_BY_URL:
                # Single URL crawl, so total_items is 1
                task.total_items = 1
            
            task.save()
            
        logger.info(f"Created task {task.id}: {task_type} (status: PENDING)")
        # Note: Task will be picked up by worker process's main loop from database
        return task
    
    def execute_task(self, task: Task, worker_id: str):
        """Execute a task"""
        try:
            logger.info(f"Worker {worker_id} executing task {task.id}: {task.task_type}")
            
            # Mark task as running
            task.mark_as_running(worker_id)
            
            # Execute based on task type
            if task.task_type == TaskType.CRAWL_AUTHORS:
                result = self._execute_crawl_authors(task)
            elif task.task_type == TaskType.DOWNLOAD_VIDEOS:
                result = self._execute_download_videos(task)
            elif task.task_type == TaskType.UPDATE_VALIDITY:
                result = self._execute_update_validity(task)
            elif task.task_type == TaskType.CRAWL_BY_URL:
                result = self._execute_crawl_by_url(task)
            else:
                raise ValueError(f"Unknown task type: {task.task_type}")
            
            # Mark as completed
            task.mark_as_completed(result)
            logger.info(f"Task {task.id} completed successfully")
            
        except Exception as e:
            logger.error(f"Task {task.id} failed: {str(e)}")
            task.mark_as_failed(str(e))
    
    def _execute_crawl_authors(self, task: Task) -> Dict[str, Any]:
        """Execute crawl authors task"""
        params = task.parameters.copy()
        author_ids = params.pop('authors', [])

        if task.is_rerun:
            author_ids = DyAuthor.objects.filter(id__in=author_ids, status='waiting').values_list('id', flat=True)
        
        # Execute crawling (author_ids is passed as positional arg, params without authors as kwargs)
        # Status updates are handled inside crawl_authors_batch by batch
        crawl_authors_batch(author_ids, task_id=task.id,**params)
        
        return {
            'total_authors': len(author_ids),
            'success_count': len(author_ids),
            'error_count': 0
        }
    
    def _execute_download_videos(self, task: Task) -> Dict[str, Any]:
        """Execute download videos task"""
        params = task.parameters.copy()
        video_ids = params.pop('videos', [])
        max_workers = params.pop('max_workers', 3)
        
        # Execute downloading (video_ids is passed as positional arg, params without videos and max_workers as kwargs)
        results = download_videos(video_ids, max_workers=max_workers, **params)
        
        # Count results
        success_count = sum(1 for r in results if r.get('status') == 'success')
        error_count = len(results) - success_count
        
        # Update video status
        if success_count > 0:
            DyVideo.objects.filter(id__in=video_ids).update(valid=True)
        
        return {
            'total_videos': len(video_ids),
            'success_count': success_count,
            'error_count': error_count,
            'results': results
        }
    
    def _execute_update_validity(self, task: Task) -> Dict[str, Any]:
        """Execute update validity task"""
        params = task.parameters
        video_ids = params.get('video_ids', [])
        
        videos = DyVideo.objects.filter(id__in=video_ids)
        updated_count = 0
        
        for video in videos:
            if video.play_src and os.path.isfile(video.play_src):
                if not video.valid:
                    video.valid = True
                    video.save()
                    updated_count += 1
            else:
                if video.valid:
                    video.valid = False
                    video.save()
                    updated_count += 1
        
        return {
            'total_videos': len(video_ids),
            'updated_count': updated_count
        }
    
    def _execute_crawl_by_url(self, task: Task) -> Dict[str, Any]:
        """Execute crawl by URL task"""
        params = task.parameters.copy()
        url = params.pop('url', '')
        
        if not url:
            raise ValueError("URL is required for crawl_by_url task")
        
        # Execute crawling
        crawl_by_url(url=url, **params)
        
        return {
            'url': url,
            'success': True
        }
    
    def get_next_task_from_db(self) -> Optional[Task]:
        """Get the next pending task from database (highest priority first)"""
        try:
            close_old_connections()
            with transaction.atomic():
                task = Task.objects.select_for_update().filter(
                    status=TaskStatus.PENDING
                ).order_by('-priority', 'created_at').first()
                
                if task:
                    return task
        except Exception as e:
            logger.error(f"Error getting next task from database: {e}")
        
        return None
    
    def main_loop(self):
        """Main loop that reads pending tasks from database and puts them into queue"""
        logger.info("Main loop started (reading from database)")
        
        while self.running:
            try:
                close_old_connections()
                
                # Check if we can start more tasks
                with self.active_tasks_lock:
                    active_count = len(self.active_tasks)
                
                if active_count < self.max_concurrent_tasks:
                    # Get next pending task from database
                    task = self.get_next_task_from_db()
                    
                    if task:
                        # Put task into queue
                        self.task_queue.put(task)
                        logger.info(f"Queued task {task.id}: {task.task_type} from database")
                    else:
                        # No tasks available, sleep briefly
                        time.sleep(2)
                else:
                    # Max concurrent tasks reached, wait a bit
                    time.sleep(1)
                    
            except Exception as e:
                logger.error(f"Main loop error: {e}")
                time.sleep(5)  # Wait before retrying
        
        logger.info("Main loop stopped")
    
    def worker_loop(self, worker_id: str):
        """Worker loop that processes tasks from queue"""
        logger.info(f"Worker {worker_id} started")
        
        while self.running:
            try:
                close_old_connections()
                
                # Get task from queue (with timeout to allow checking self.running)
                try:
                    task = self.task_queue.get(timeout=1)
                except queue.Empty:
                    continue
                
                # Track active task
                with self.active_tasks_lock:
                    self.active_tasks[task.id] = threading.current_thread()
                
                try:
                    # Execute task
                    self.execute_task(task, worker_id)
                finally:
                    # Remove from active tasks
                    with self.active_tasks_lock:
                        self.active_tasks.pop(task.id, None)
                    self.task_queue.task_done()
                    
            except Exception as e:
                logger.error(f"Worker {worker_id} error: {e}")
                time.sleep(1)
        
        logger.info(f"Worker {worker_id} stopped")
    
    def start_workers(self, max_concurrent_tasks: int = 2):
        """Start the task processing system"""
        if self.running:
            logger.warning("Workers already running")
            return
        
        self.max_concurrent_tasks = max_concurrent_tasks
        self.running = True
        
        # Start main loop thread (reads pending tasks from database and puts them into queue)
        self.main_loop_thread = threading.Thread(
            target=self.main_loop,
            daemon=False
        )
        self.main_loop_thread.start()
        logger.info("Main loop thread started (reading from database)")
        
        # Start worker threads (process tasks from queue)
        # We create enough threads to handle max_concurrent_tasks
        for i in range(max_concurrent_tasks):
            worker_id = f"worker-{i+1}"
            thread = threading.Thread(
                target=self.worker_loop,
                args=(worker_id,),
                daemon=False
            )
            thread.start()
            self.worker_threads.append(thread)
            logger.info(f"Started worker {worker_id}")
        
        logger.info(f"Task processing system started with max_concurrent_tasks={max_concurrent_tasks}")
    
    def stop_workers(self):
        """Stop the task processing system"""
        if not self.running:
            return
        
        logger.info("Stopping task processing system...")
        self.running = False
        
        # Wait for main loop to finish
        if self.main_loop_thread:
            self.main_loop_thread.join(timeout=10)
        
        # Wait for all tasks in queue to be processed
        self.task_queue.join()
        
        # Wait for worker threads to finish
        for thread in self.worker_threads:
            thread.join(timeout=10)
        
        self.worker_threads.clear()
        self.main_loop_thread = None
        logger.info("Task processing system stopped")
    
    def get_task_status(self, task_id: int) -> Optional[Dict[str, Any]]:
        """Get task status"""
        try:
            task = Task.objects.get(id=task_id)
            # Calculate progress based on processed_items and total_items
            progress = 0
            if task.total_items > 0:
                progress = int((task.processed_items / task.total_items) * 100)
            
            return {
                'id': task.id,
                'task_type': task.task_type,
                'status': task.status,
                'progress': progress,
                'total_items': task.total_items,
                'processed_items': task.processed_items,
                'result': task.result,
                'error_message': task.error_message,
                'created_at': task.created_at,
                'started_at': task.started_at,
                'completed_at': task.completed_at,
                'worker_id': task.worker_id,
            }
        except Task.DoesNotExist:
            return None
    
    def get_queue_stats(self) -> Dict[str, Any]:
        """Get queue statistics"""
        stats = {}
        
        for status in TaskStatus:
            count = Task.objects.filter(status=status).count()
            stats[status.value] = count
        
        # Add process-internal queue stats
        with self.active_tasks_lock:
            stats['active_tasks'] = len(self.active_tasks)
        stats['queue_size'] = self.task_queue.qsize()
        stats['max_concurrent_tasks'] = self.max_concurrent_tasks
        
        return stats


# Global task manager instance
task_manager = TaskManager()
