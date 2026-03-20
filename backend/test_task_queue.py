#!/usr/bin/env python
"""
Test script for the custom task queue system.
"""

import os
import sys
import django

# Setup Django
sys.path.append('/root/Develop/fileviewer/backend')
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'backend.settings')
django.setup()

from dyvideo.models import DyAuthor, Task, TaskType
from dyvideo.task_manager import task_manager


def test_task_queue():
    """Test the custom task queue"""
    print("Testing custom task queue...")
    
    # Get some author IDs for testing
    authors = DyAuthor.objects.all()[:3]
    if not authors:
        print("No authors found. Please add some authors first.")
        return
    
    author_ids = [author.id for author in authors]
    print(f"Testing with author IDs: {author_ids}")
    
    # Create a crawl task
    task = task_manager.create_task(
        task_type=TaskType.CRAWL_AUTHORS,
        parameters={
            'author_ids': author_ids,
            'multipage': False,
            'batch_size': 10,
        }
    )
    
    print(f"Created task {task.id}")
    print(f"Task status: {task.status}")
    print(f"Task priority: {task.priority}")
    
    # Start workers
    print("Starting workers...")
    task_manager.start_workers(2)
    
    # Monitor task progress
    print("Monitoring task progress...")
    import time
    
    while True:
        task_status = task_manager.get_task_status(task.id)
        if task_status:
            print(f"Task {task.id}: {task_status['status']} - Progress: {task_status['progress']}%")
            
            if task_status['status'] in ['completed', 'failed']:
                break
        else:
            print("Task not found!")
            break
        
        time.sleep(2)
    
    # Get final result
    task_status = task_manager.get_task_status(task.id)
    if task_status:
        print(f"Final result: {task_status['result']}")
    
    # Stop workers
    print("Stopping workers...")
    task_manager.stop_workers()
    
    # Show queue stats
    stats = task_manager.get_queue_stats()
    print(f"Queue stats: {stats}")


if __name__ == '__main__':
    test_task_queue()
