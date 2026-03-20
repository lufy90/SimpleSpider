"""
Management command to migrate data from fastapi_backend sqlite3 database to Django database.
"""

import sqlite3
import os
from django.core.management.base import BaseCommand
from django.contrib.auth.models import User
from django.db import transaction
from dyvideo.models import DyAuthor, DyVideo, Status


class Command(BaseCommand):
    help = 'Migrate dyauthors and dyvideos from fastapi_backend sqlite3 database to Django database'

    def add_arguments(self, parser):
        parser.add_argument(
            '--db-file',
            type=str,
            required=True,
            help='Path to the sqlite3 database file from fastapi_backend'
        )
        parser.add_argument(
            '--user-id',
            type=int,
            default=1,
            help='User ID to use for created_by fields (default: 1)'
        )
        parser.add_argument(
            '--dry-run',
            action='store_true',
            help='Perform a dry run without actually saving data'
        )

    def handle(self, *args, **options):
        db_file = options['db_file']
        user_id = options['user_id']
        dry_run = options['dry_run']
        
        if not os.path.exists(db_file):
            self.stdout.write(
                self.style.ERROR(f'Database file not found: {db_file}')
            )
            return
        
        try:
            user = User.objects.get(id=user_id)
        except User.DoesNotExist:
            self.stdout.write(
                self.style.ERROR(f'User with id={user_id} does not exist')
            )
            return
        
        self.stdout.write(
            self.style.SUCCESS(f'Starting migration from {db_file}')
        )
        self.stdout.write(f'Using user: {user.username} (id={user_id})')
        if dry_run:
            self.stdout.write(self.style.WARNING('DRY RUN MODE - No data will be saved'))
        self.stdout.write('')
        
        try:
            conn = sqlite3.connect(db_file)
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            
            # Migrate DyAuthors first
            self.stdout.write(self.style.SUCCESS('Migrating DyAuthors...'))
            author_count = self.migrate_authors(cursor, user, dry_run)
            self.stdout.write(
                self.style.SUCCESS(f'Migrated {author_count} authors')
            )
            self.stdout.write('')
            
            # Migrate DyVideos
            self.stdout.write(self.style.SUCCESS('Migrating DyVideos...'))
            video_count = self.migrate_videos(cursor, user, dry_run)
            self.stdout.write(
                self.style.SUCCESS(f'Migrated {video_count} videos')
            )
            
            conn.close()
            
            if not dry_run:
                self.stdout.write('')
                self.stdout.write(
                    self.style.SUCCESS('Migration completed successfully!')
                )
            else:
                self.stdout.write('')
                self.stdout.write(
                    self.style.WARNING('Dry run completed. No data was saved.')
                )
                
        except Exception as e:
            self.stdout.write(
                self.style.ERROR(f'Error during migration: {e}')
            )
            import traceback
            self.stdout.write(traceback.format_exc())
            raise

    def migrate_authors(self, cursor, user, dry_run):
        """Migrate DyAuthor data from sqlite3 to Django"""
        cursor.execute('SELECT * FROM dyauthor')
        rows = cursor.fetchall()
        
        # Get column names
        column_names = [description[0] for description in cursor.description]
        
        created_count = 0
        updated_count = 0
        skipped_count = 0
        
        # Status mapping from fastapi_backend to Django
        status_map = {
            0: Status.READY,
            1: Status.WAITING,
            2: Status.RUNNING,
            3: Status.ERROR,
        }
        
        def get_value(row, col_name, default=None):
            """Helper function to get value from row by column name"""
            if isinstance(row, dict) or hasattr(row, 'keys'):
                return row[col_name] if col_name in row.keys() else default
            elif isinstance(row, (list, tuple)):
                try:
                    idx = column_names.index(col_name)
                    return row[idx] if idx < len(row) else default
                except (ValueError, IndexError):
                    return default
            else:
                try:
                    return getattr(row, col_name, default)
                except:
                    return default
        
        for row in rows:
            try:
                # Get values from row
                unique_id = get_value(row, 'unique_id')
                uid = get_value(row, 'uid')
                name = get_value(row, 'name', '')
                
                # Check if author already exists by unique_id or uid
                existing_author = None
                if unique_id:
                    try:
                        existing_author = DyAuthor.objects.get(unique_id=unique_id)
                    except DyAuthor.DoesNotExist:
                        pass
                
                if not existing_author and uid:
                    try:
                        existing_author = DyAuthor.objects.get(uid=uid)
                    except DyAuthor.DoesNotExist:
                        pass
                
                if existing_author:
                    self.stdout.write(
                        f'  Skipping existing author: {name} (uid={uid})'
                    )
                    skipped_count += 1
                    continue
                
                # Map status from int to Django TextChoices
                status_value = get_value(row, 'status', 0)
                django_status = status_map.get(status_value, Status.READY)
                
                author_data = {
                    'url': get_value(row, 'url') or None,
                    'path': get_value(row, 'path') or '',
                    'is_valid': bool(get_value(row, 'valid', True)),
                    'rate': get_value(row, 'stars', 0),
                    'name': get_value(row, 'name') or '',
                    'sec_uid': get_value(row, 'sec_uid') or '',
                    'age': get_value(row, 'age', 0),
                    'unique_id': unique_id or None,
                    'uid': uid or None,
                    'school_name': get_value(row, 'school_name') or '',
                    'ip_location': get_value(row, 'ip_location') or '',
                    'status': django_status,
                    'is_favor': bool(get_value(row, 'is_favor', False)),
                    'is_like': False,  # fastapi_backend doesn't have this field
                    'desc': '',  # fastapi_backend doesn't have this field
                    'created_by': user,
                    'updated_by': user,
                    'crawled_at': get_value(row, 'last_crawl'),
                }
                
                if not dry_run:
                    with transaction.atomic():
                        author = DyAuthor(**author_data)
                        author.save()
                    created_count += 1
                    self.stdout.write(
                        f'  Created author: {author.name} (uid={author.uid})'
                    )
                else:
                    created_count += 1
                    self.stdout.write(
                        f'  [DRY RUN] Would create author: {name} (uid={uid})'
                    )
                    
            except Exception as e:
                self.stdout.write(
                    self.style.ERROR(f'  Error migrating author {get_value(row, "name", "unknown")}: {e}')
                )
                continue
        
        self.stdout.write(f'  Created: {created_count}, Skipped: {skipped_count}')
        return created_count

    def migrate_videos(self, cursor, user, dry_run):
        """Migrate DyVideo data from sqlite3 to Django"""
        cursor.execute('SELECT * FROM dyvideo')
        rows = cursor.fetchall()
        
        # Get column names
        column_names = [description[0] for description in cursor.description]
        
        created_count = 0
        skipped_count = 0
        no_author_count = 0
        
        def get_value(row, col_name, default=None):
            """Helper function to get value from row by column name"""
            if isinstance(row, dict) or hasattr(row, 'keys'):
                return row[col_name] if col_name in row.keys() else default
            elif isinstance(row, (list, tuple)):
                try:
                    idx = column_names.index(col_name)
                    return row[idx] if idx < len(row) else default
                except (ValueError, IndexError):
                    return default
            else:
                try:
                    return getattr(row, col_name, default)
                except:
                    return default
        
        # Build a mapping of author_uid to Django DyAuthor
        author_map = {}
        for author in DyAuthor.objects.all():
            if author.uid:
                author_map[author.uid] = author
        
        for row in rows:
            try:
                # Get values from row
                vid = get_value(row, 'vid')
                name = get_value(row, 'name', '')
                author_uid = get_value(row, 'author_uid', '')
                author_name = get_value(row, 'author_name', '')
                
                # Check if video already exists by vid
                if vid:
                    try:
                        existing_video = DyVideo.objects.get(vid=vid)
                        self.stdout.write(
                            f'  Skipping existing video: {name} (vid={vid})'
                        )
                        skipped_count += 1
                        continue
                    except DyVideo.DoesNotExist:
                        pass
                
                # Find author by author_uid
                author = None
                if author_uid:
                    author = author_map.get(author_uid)
                    if not author:
                        # Try to query directly in case it was just created
                        try:
                            author = DyAuthor.objects.get(uid=author_uid)
                            author_map[author_uid] = author
                        except DyAuthor.DoesNotExist:
                            pass
                
                video_data = {
                    'path': get_value(row, 'path') or '',
                    'valid': bool(get_value(row, 'valid', False)),
                    'rate': get_value(row, 'stars', 0),
                    'name': name or '',
                    'vid': vid or None,
                    'author': author,  # Can be None if author not found
                    'author_unique_id': get_value(row, 'author_unique_id') or '',
                    'author_uid': author_uid or '',
                    'author_name': author_name or '',
                    'is_like': bool(get_value(row, 'is_like', False)),
                    'is_favor': bool(get_value(row, 'is_favor', False)),
                    'desc': get_value(row, 'desc') or '',
                    'origin_url': get_value(row, 'origin_url') or '',
                    'cover_url': get_value(row, 'cover_url') or '',
                    'size': 0,  # fastapi_backend doesn't have this field
                    'created_by': user,
                    'updated_by': user,
                }
                
                if not dry_run:
                    with transaction.atomic():
                        video = DyVideo(**video_data)
                        video.save()
                    created_count += 1
                    author_info = f'author={author.name}' if author else 'no author'
                    self.stdout.write(
                        f'  Created video: {video.name} (vid={video.vid}, {author_info})'
                    )
                else:
                    created_count += 1
                    author_info = f'author={author_name or "unknown"}' if author else 'no author'
                    self.stdout.write(
                        f'  [DRY RUN] Would create video: {name} (vid={vid}, {author_info})'
                    )
                
                if not author:
                    no_author_count += 1
                    
            except Exception as e:
                self.stdout.write(
                    self.style.ERROR(f'  Error migrating video {get_value(row, "name", "unknown")}: {e}')
                )
                continue
        
        self.stdout.write(f'  Created: {created_count}, Skipped: {skipped_count}, No author: {no_author_count}')
        return created_count

