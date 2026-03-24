import os
import json
import logging
import requests
import time
from typing import List, Dict, Any, Optional
from playwright.sync_api import sync_playwright
from django.conf import settings
from .models import DyVideo, Status, DyAuthor
from playwright.sync_api import Response
import queue
from concurrent.futures import ThreadPoolExecutor
from threading import Thread
from django.contrib.auth.models import User
from .models import Task
from django.db import close_old_connections



logger = logging.getLogger(__name__)

cookie_file = settings.BASE_DIR / 'dyvideo' / 'cookies.json'
DATADIR = settings.MEDIA_ROOT

def format_author_data(api_data: Dict[str, Any]) -> Dict[str, Any]:
    """
    Format API response data to DyAuthor model fields.
    Only extracts and maps fields that exist in the model.
    
    Args:
        api_data: Raw API response data for author/profile
        url: Author URL (optional, will be extracted from api_data if not provided)
    
    Returns:
        Dict with only valid DyAuthor model fields
    """
    formatted = {}
    
    # Map API fields to model fields
    field_mapping = {
        'nickname': 'name',
        'uid': 'uid',
        'sec_uid': 'sec_uid',
        'unique_id': 'unique_id',
        'school_name': 'school_name',
        'ip_location': 'ip_location',
        'signature': 'desc',
        'age': 'age',
    }
    
    # Extract and map fields
    for api_key, model_key in field_mapping.items():
        if api_key in api_data:
            formatted[model_key] = api_data[api_key]
    
    # Handle nested fields
    if 'user' in api_data:
        user_data = api_data['user']
        for api_key, model_key in field_mapping.items():
            if api_key in user_data:
                formatted[model_key] = user_data[api_key]
        
        # Extract signature_sec_uid from signature_extra
        # signature_extra is a list of dicts, each dict has a sec_uid key
        if 'signature_extra' in user_data:
            signature_extra = user_data['signature_extra']
            sec_uid_list = []
            
            if isinstance(signature_extra, list):
                # Extract sec_uid from each dict in the list
                for item in signature_extra:
                    if isinstance(item, dict) and 'sec_uid' in item:
                        sec_uid = item.get('sec_uid', '')
                        if sec_uid and sec_uid not in sec_uid_list:
                            sec_uid_list.append(sec_uid)
            elif isinstance(signature_extra, dict):
                # Handle legacy format (single dict)
                sec_uid = signature_extra.get('sec_uid', '')
                if sec_uid:
                    sec_uid_list.append(sec_uid)
            
            # Join all sec_uid values with comma
            formatted['signature_sec_uid'] = ', '.join(sec_uid_list)
    
    if formatted.get('sec_uid', None):
        formatted['url'] = f"https://www.douyin.com/user/{formatted['sec_uid']}"
    
    # Set path from uid if available
    if 'uid' in formatted:
        formatted['path'] = str(formatted['uid'])
    
    return formatted

def format_video_data(api_data: Dict[str, Any], author=None, video_path: str = None) -> Dict[str, Any]:
    """
    Format API response data to DyVideo model fields.
    Only extracts and maps fields that exist in the model.
    
    Args:
        api_data: Raw API response data for video/aweme
        author: DyAuthor instance (optional)
        video_path: Local path for video file (optional)
    
    Returns:
        Dict with only valid DyVideo model fields
    """
    formatted = {}
    
    # Map API fields to model fields
    field_mapping = {
        'aweme_id': 'vid',
        'desc': 'desc',
        'rate': 'rate',
    }
    
    # Extract and map direct fields
    for api_key, model_key in field_mapping.items():
        if api_key in api_data:
            formatted[model_key] = api_data[api_key]
    
    # Handle video title/name
    if 'desc' in api_data:
        formatted['name'] = api_data['desc'][:255] if api_data['desc'] else ''
    elif 'share_info' in api_data and 'share_title' in api_data['share_info']:
        formatted['name'] = api_data['share_info']['share_title'][:255]
    
    # Handle video URLs
    if 'video' in api_data:
        video_info = api_data['video']
        if 'play_addr' in video_info and 'url_list' in video_info['play_addr']:
            url_list = video_info['play_addr']['url_list']
            if url_list:
                formatted['origin_url'] = url_list[-1]
    
    # Handle cover image
    if 'video' in api_data and 'cover' in api_data['video']:
        cover_info = api_data['video']['cover']
        if 'url_list' in cover_info and cover_info['url_list']:
            formatted['cover_url'] = cover_info['url_list'][0]
    
    # Handle author information
    if author:
        formatted['author'] = author
        formatted['author_uid'] = author.uid or ''
        formatted['author_unique_id'] = author.unique_id or ''
        formatted['author_name'] = author.name or ''
    elif 'author' in api_data:
        author_info = api_data['author']
        formatted['author_uid'] = author_info.get('uid', '')
        formatted['author_unique_id'] = author_info.get('unique_id', '')
        formatted['author_name'] = author_info.get('nickname', '')
    
    # Set path if provided
    if video_path:
        formatted['path'] = video_path
    # Handle video size
    if 'video' in api_data and 'play_addr' in api_data['video']:
        play_addr = api_data['video']['play_addr']
        if 'data_size' in play_addr:
            formatted['size'] = play_addr['data_size']
    
    # Handle boolean fields
    formatted['is_like'] = api_data.get('user_digged', False)
    formatted['is_favor'] = api_data.get('collect_stat', False)
    
    return formatted

def read_cookies(fname: str = cookie_file) -> Optional[Dict]:
    """Read cookies from file"""
    with open(fname, 'r') as f:
        return json.load(f)

def set_cookies(url: str = "https://www.douyin.com", fname: str = None, driver: str = "firefox", timeout: int = 300000) -> bool:
    """
    Get cookies from a headed browser by waiting for user to log in.
    The function waits for the login indicator element <a href="//www.douyin.com/user/self"> to appear,
    which signals that the user has successfully logged in.
    
    Args:
        url: URL to navigate to (default: douyin.com)
        fname: Cookie file path (default: cookie_file)
        driver: Browser driver to use (default: "firefox")
        timeout: Timeout in milliseconds (default: 300000 = 5 minutes)
    
    Returns:
        bool: True if cookies were successfully saved, False otherwise
    """
    if fname is None:
        fname = cookie_file
    
    # Create directory if it doesn't exist
    fname_path = os.path.dirname(fname)
    if fname_path:
        os.makedirs(fname_path, exist_ok=True)
    
    try:
        with sync_playwright() as p:
            browser = p[driver].launch(headless=False)
            context = browser.new_context(viewport={"width": 1364, "height": 768})
            page = context.new_page()
            
            logger.info(f"Navigating to {url}, please log in manually in the browser window")
            page.goto(url, timeout=300000)
            
            # Wait for login indicator: <a href="//www.douyin.com/user/self"> element appears
            logger.info("Waiting for login indicator (user/self link) to appear...")
            try:
                # Wait for the element that indicates user is logged in
                page.wait_for_selector('a[href="//www.douyin.com/user/self"]', timeout=timeout)
                logger.info("Login indicator detected, user is logged in")
                # Get cookies from context
                cookies = context.cookies()
                # Save cookies to file
                with open(fname, 'w') as f:
                    json.dump(cookies, f, indent=2, ensure_ascii=False)
                logger.info(f"Cookies saved successfully to {fname}")
                page.wait_for_timeout(2000)
                browser.close()
                return True
            except Exception as e:
                logger.error(f"Timeout waiting for login indicator: {e}")
                return False
            
    except Exception as e:
        logger.error(f"Error setting cookies: {e}")
        return False

def response_handler(res: Response, exp_urls: List[str], **kwargs) -> Dict[str, Any]:
    q = kwargs.get("q", None)
    if not q:
        raise ValueError("request queue is required")
    for url in exp_urls:
        if url in res.url:
            try:
                res_json = res.json()
            except Exception as e:
                logger.warning("response_handler: res.json() failed for %s: %s", res.url[:80], e)
                return None
            logger.debug("expected url: %s", url)
            q.put({"url": url, "json": res_json, **kwargs})
            logger.info("response_handler: enqueued item, queue size: %s", q.qsize())
            logger.debug("response_handler kwargs: %s", {k: v for k, v in kwargs.items() if k != "q"})
    return None

def crawl_page(page, url, exp_resp_urls, **kwargs) -> List[Dict[str, Any]]:
    res_count = 0
    def wrapped_handler(res: Response):
#        has_more = res.json().get('has_more', False)
        nonlocal res_count
        for url in exp_resp_urls:
           if url in res.url:
               res_count = res_count + 1
               break
        response_handler(res, exp_resp_urls, **kwargs)
    page.on('response', wrapped_handler)
    logger.info("crawl_page goto: %s", url)
    page.goto(url, timeout=60000, wait_until="load")

    page_count = kwargs.get("page_count", 0)
    logger.info(f"crawl_page get page_count: {page_count}")
    if page_count:
        page.get_by_text("抖音号").click()
        while res_count < page_count:
            logger.info(f"crawl_page current res_count: {res_count}")
            page.keyboard.press("End")
            page.wait_for_load_state('domcontentloaded', timeout=60000)
            page.wait_for_timeout(10000)


#        for i in range(page_count):
#            page.keyboard.press("End")
#            page.wait_for_load_state('domcontentloaded', timeout=60000)
#            page.wait_for_timeout(5000)
#            logger.info(f'page {i} loaded')
    
    elif kwargs.get("multipage", False):
        # turn to new page and crawl data
        page.get_by_text("抖音号").click()
        locator = page.locator("text='暂时没有更多了'")
        is_end = locator.is_visible()
        while not is_end:
            page.keyboard.press("End")
            page.wait_for_load_state('domcontentloaded', timeout=60000)
            locator = page.locator("text='暂时没有更多了'")
            is_end = locator.is_visible()
    else:
        page.wait_for_load_state('domcontentloaded', timeout=60000)
        page.wait_for_timeout(10000)

    # Allow late response events to be captured before returning
    page.wait_for_timeout(5000)


def crawl_authors(aids, **kwargs) -> List[Dict[str, Any]]:
    """
    Crawl authors in a batch.
    Note: This function runs in a worker thread, so we need to use bulk updates
    instead of saving individual author objects to avoid database connection issues.
    """
    driver = kwargs.get("driver", "firefox")
    exp_resp_urls = [
        "https://www.douyin.com/aweme/v1/web/user/profile/other/",
        "https://www.douyin.com/aweme/v1/web/aweme/post",
    ]
    q = kwargs.get("q", None)
    with sync_playwright() as p:
        headless = kwargs.get("headless", True)
        browser_size = kwargs.get("browser_size", (1920, 1080))
        width, height = browser_size

        browser = p[driver].launch(headless=headless)
        context = browser.new_context(viewport={"width": width, "height": height})
        try:
            cookies = read_cookies()
            context.add_cookies(cookies)
        except:
            logger.warning("add cookies failed")
        page = context.new_page()

        # Create a thread pool executor for Django ORM operations
        # This isolates Django ORM from Playwright's async context
        db_executor = ThreadPoolExecutor(max_workers=1, thread_name_prefix="db-worker")
        
        for aid in aids:
            close_old_connections()
            logger.info(f'crawl_authors aid: {aid}')
            try:
                # Helper function to run Django ORM operations in a separate thread
                # This avoids the async context issue with Playwright
                def run_db_operation(func):
                    """Run a database operation in a separate thread to avoid async context issues"""
                    future = db_executor.submit(func)
                    return future.result()
                
                # Get author in a thread-safe way
                def get_author():
                    close_old_connections()
                    return DyAuthor.objects.filter(id=aid).first()
                
                author = run_db_operation(get_author)
                if not author:
                    logger.warning(f"Author {aid} not found, skipping")
                    continue
                
                # Store author URL before crawling (needed for crawl_page)
                author_url = author.url
                
                # Update author status to RUNNING
                def update_to_running():
                    close_old_connections()
                    author = DyAuthor.objects.filter(id=aid).first()
                    if author:
                        author.status = Status.RUNNING
                        author.save()
                
                run_db_operation(update_to_running)
                
                # Crawl the page (this is the Playwright operation)
                # Note: We pass author object but it might not be usable in Playwright context
                # So we'll re-fetch it after crawling if needed
                crawl_page(page, author_url, exp_resp_urls, author_id=aid, **kwargs)
                
                # Update author status to READY
                def update_to_ready():
                    close_old_connections()
                    author = DyAuthor.objects.filter(id=aid).first()
                    if author:
                        author.status = Status.READY
                        author.save()
                
                run_db_operation(update_to_ready)
                
                # Update task count
                if kwargs.get("task_id"):
                    task_id = kwargs.get("task_id")
                    def update_task_processed():
                        close_old_connections()
                        task = Task.objects.filter(id=task_id).first()
                        if task:
                            task.processed_items += 1
                            task.save(update_fields=['processed_items'])
                    
                    run_db_operation(update_task_processed)
                else:
                    logger.warning("task is not set, skip status update")
                    
            except Exception as e:
                import traceback
                traceback.print_exc()
                logger.error(f'crawl_authors error: {e}')
                
                # Update author status to ERROR in a thread-safe way
                def update_to_error():
                    close_old_connections()
                    author = DyAuthor.objects.filter(id=aid).first()
                    if author:
                        author.status = Status.ERROR
                        author.save()
                
                try:
                    run_db_operation(update_to_error)
                except Exception as db_error:
                    logger.error(f"Failed to update author status to ERROR: {db_error}")
                
                # Update task error count
                if kwargs.get("task_id"):
                    task_id = kwargs.get("task_id")
                    def update_task_error():
                        close_old_connections()
                        task = Task.objects.filter(id=task_id).first()
                        if task:
                            task.error_items += 1
                            task.save(update_fields=['error_items'])
                    
                    try:
                        run_db_operation(update_task_error)
                    except Exception as task_error:
                        logger.error(f"Failed to update task error count: {task_error}")
                else:
                    logger.warning("task is not set, skip status update")
                
                continue
        
        # Shutdown the executor when done
        db_executor.shutdown(wait=True)



def crawl_by_url(url, **kwargs):
    """
    Crawl a single URL using playwright.
    Sets up browser, page, and calls crawl_by_url.
    """
    driver = kwargs.pop("driver", "firefox")
    exp_resp_urls = [
        "https://www.douyin.com/aweme/v1/web/user/profile/other/",
        "https://www.douyin.com/aweme/v1/web/aweme/",
    ]
    exp_resp_urls = kwargs.pop("exp_resp_urls", exp_resp_urls)
    task_id = kwargs.get("task_id")

    logger.info(f"task (id {task_id}) started.")

    logger.debug(f'exp_Resp_urls: {exp_resp_urls}')
    
    # response queue
    resp_q = queue.Queue()
    num_save_workers = kwargs.get("num_save_workers", 3)
    sentinel = object()

    with sync_playwright() as p:
        headless = kwargs.get("headless", True)
        browser_size = kwargs.get("browser_size", (1920, 1080))
        width, height = browser_size

        browser = p[driver].launch(headless=headless)
        context = browser.new_context(viewport={"width": width, "height": height})
        try:
            cookies = read_cookies()
            context.add_cookies(cookies)
        except:
            logger.warning("add cookies failed")
        page = context.new_page()

        save_workers = [Thread(target=save_worker, args=(i, resp_q, sentinel)) for i in range(num_save_workers)]
        logger.info("save_workers start: %s", num_save_workers)
        for t in save_workers:
            t.start()

        logger.info(f'crawl_by_url url: {url}')
        logger.debug(f"kwargs: {kwargs}")
        crawl_page(page, url, exp_resp_urls, q=resp_q, **kwargs)

        for _ in range(num_save_workers):
            resp_q.put(sentinel)
        for t in save_workers:
            t.join()

        browser.close()
    logger.info(f"task (id {task_id}) finished.")

def save_data(item:dict={}):
    close_old_connections()
    author_id = item.get("author_id", None)

    # Query author by author_id if it exists
    author = None
    if author_id:
        try:
            author = DyAuthor.objects.get(id=author_id)
            if author:
                logger.info(f'Found author by author_id: {author_id}, name: {author.name}')
        except Exception as e:
            logger.error(f'Error querying author by author_id {author_id}: {e}')
            return

    url = item.get("url")
    json_info = item.get("json")
    user_id = item.get("user", None)

    if author:
        logger.info(f'author_url: {author.url}')
    else:
        logger.info(f"no author by {author_id}")
    logger.info(f'user_id: {user_id}')

    user = User.objects.filter(id=user_id).first()

    logger.debug(f'author: {author}')
    logger.debug(f'user:{user}')

    # handle profile response
    if "https://www.douyin.com/aweme/v1/web/user/profile/" in url:
        # save profile data
        author_data = format_author_data(json_info)
        sec_uid = author_data.get("sec_uid")
        if author and sec_uid in author.url:
            if sec_uid:
                if sec_uid in author.url or sec_uid == author.sec_uid:
                    author = author
        else:
            try:
                author = DyAuthor.objects.get(sec_uid=author_data["sec_uid"])
            except DyAuthor.DoesNotExist:
                author = None
            except Exception as e:
                logger.error(f"invalid author_data: {author_data}, resason: {str(e)}")
                return

        if author:
            # Update existing author
            # Track old name before updating
            old_name = author.name
            new_name = author_data.get('name', '')
            
            for key, value in author_data.items():
                if hasattr(author, key) and key not in ['created_by', 'crawled_by'] and value:
                    setattr(author, key, value)
            
            # If name changed, add old name to used_names
            if old_name and new_name and old_name != new_name and old_name.strip():
                # Initialize used_names if it's None or empty
                if not author.used_names:
                    author.used_names = ""
                
                # Split existing used_names into a list
                existing_names = [n.strip() for n in author.used_names.split(",") if n.strip()]
                
                # Add old name to used_names if not already present
                if old_name not in existing_names:
                    existing_names.append(old_name)
                    # Join back with comma separator
                    author.used_names = ", ".join(existing_names)
            
            if user:
                author.updated_by = user
                author.crawled_by = user
            try:
                author.save()
            except Exception as e:
                logger.error(e)
                logger.error(author.path)
                logger.error(author.name)
                logger.error(author.sec_uid)
                logger.error(author.signature_sec_uid)
                logger.error(author.unique_id)
                logger.error(author.uid)
                logger.error(author.school_name)
                logger.error(author.ip_location)
        else:
            # Create new author
            # Clean up empty unique_id/uid to prevent unique constraint violations
            # Set them to None instead of empty string
            if 'unique_id' in author_data and not author_data['unique_id']:
                author_data['unique_id'] = None
            if 'uid' in author_data and not author_data['uid']:
                author_data['uid'] = None
            
            if user:
                author_data["created_by"] = user
                author_data["crawled_by"] = user
            author = DyAuthor(**author_data)
            logger.info(f"author does not exists, now create: {author}")
            author.save()

        save_to = os.path.join(DATADIR, author.path)
        os.makedirs(save_to, exist_ok=True)

        with open(os.path.join(save_to, 'info.json'), 'w') as f:
            json.dump(json_info, f, indent=2, ensure_ascii=False)
        
        # Download avatar (if model has avatar field, it will be saved separately)
        if author and "user" in json_info and "avatar_medium" in json_info["user"] and "url_list" in json_info["user"]["avatar_medium"]:
            avatar_url = json_info["user"]["avatar_medium"]["url_list"][0]
            download_media(avatar_url, os.path.join(DATADIR, author.path), "avatar.jpg")
            # Note: If DyAuthor model has avatar field, set it here
            # author.avatar = os.path.join(author.path, "avatar.jpg")
            if user:
                author.updated_by = user
            author.save()
        
        logger.info(f"Saved profile data for author {author.id} by user {user}")
    # handle post response
    elif "https://www.douyin.com/aweme/v1/web/aweme/" in url:
        # save post data
        videos = json_info.get("aweme_list", [])
        author_specified = False
        if author:
            author_specified = True
        for video_info in videos:
            # Handle author if not provided
            ainfo = video_info.get("author", {})


            if author_specified:
                # current video not belongs to current author
                if author.uid != ainfo.get("uid"):
                    logger.warn(f"current video(belongs to uid: {ainfo.get('uid')}) not belongs to specified author(uid: {author.uid})")
                    author_new = DyAuthor.objects.filter(uid=ainfo.get("uid")).first()
                    # get video not belongs to current author. if author exists, update author, else ignore this video
                    if not author_new:
                        logger.warn(f"author query by uid {ainfo.get('uid')} failed, skip this video")
                        continue
                    else:
                        author = author_new
            else:
                logger.debug(f"author id: {author_id}")
                adata = format_author_data(ainfo)
                author = DyAuthor.objects.filter(uid=adata.get("uid","xxxx")).first()
                if not author:
                    logger.debug(f"adata: {adata}")
                    if user:
                        adata["created_by"] = user
                        adata["crawled_by"] = user
                    if 'unique_id' in adata and not adata.get("unique_id"):
                        adata["unique_id"] = None
                    if 'uid' in adata and not adata.get("uid"):
                        author_data["uid"] = None
                    author = DyAuthor(**adata)
                author.save()

            # Format video data
            aweme_id = video_info.get("aweme_id")
            video_path = os.path.join(author.uid, aweme_id) if author and aweme_id else None
            video_data = format_video_data(video_info, author=author, video_path=video_path)
            save_to = os.path.join(DATADIR, video_path) if video_path else None

            # Add user information
            if user:
                video_data["created_by"] = user
                video_data["updated_by"] = user
            
            # Create and save video
            vid = video_data.get("vid", None)
            try:
                video = DyVideo.objects.filter(vid=vid).first()
                if video:
                    # update video urls
                    if not video.valid:
                        video.origin_url = video_data.get("origin_url")
                        video.cover_url = video_data.get("cover_url")
                        video.save()
                else:
                    video = DyVideo(valid=False, **video_data)
                    video.save()
            except Exception as e:
                logger.error(f"save data to db failed (vid: {vid}), reason: {str(e)}")
                if not video.pk:
                    # video.save failed, current video object is not been saved.
                    # possible reason is video info inside multiple responses, and handled by multiple thread, ignore is OK.
                    continue

            # save json
            os.makedirs(save_to, exist_ok=True)
            with open(os.path.join(save_to, 'info.json'), 'w') as f:
                json.dump(video_info, f, indent=2, ensure_ascii=False)

            # Download video file
            if not video.valid:
                logger.info(f"Now download video: {video}")
                try:
                    download_media(video.origin_url, save_to, "video.mp4")
                    download_media(video.cover_url, save_to, "cover.jpg")
                    video.valid = True
                    video.save()
                except Exception as e:
                    logger.error(f"video download failed, reason: {str(e)}")
                    video.valid = False
                    video.save()
                if video_info.get("images"):
                    i = 1
                    for img in video_info.get('images'):
                        if os.path.isfile(os.path.join(save_to, f'{i}.jpg')):
                            continue
                        download_media(img.get("download_url_list")[-1], save_to, f"{i}.jpg")
                        i = i + 1
            else:
                logger.info(f"{save_to} already exists, skip")

            logger.info(f"Saved video {video.id if video else None} for author {author.id if author else 'unknown'}")
    else:
        logger.info(f"Unknown URL: {url}")

def _save_worker_sentinel():
    """Sentinel object to signal save_worker to exit. Use a unique object so None is a valid payload."""
    pass

def save_worker(worker_id, q, sentinel=None):
    """
    Consume items from q and call save_data. Exits when sentinel is received.
    Uses sentinel-based shutdown so workers do not exit early while crawl_workers
    are still starting or loading pages (avoiding timeout=600 queue.Empty race).
    """
    if sentinel is None:
        sentinel = _save_worker_sentinel
    logger.info("save_worker start: %s", worker_id)
    while True:
        item = q.get()
        if item is sentinel:
            q.task_done()
            break
        try:
            logger.info("save_worker %s processing item, queue size: %s", worker_id, q.qsize())
            # timeout needed here
            save_data(item)
        except Exception as e:
            logger.exception("save_worker %s save_data error: %s", worker_id, e)
        q.task_done()
    logger.info("save_worker %s exit", worker_id)

def crawl_worker(worker_id, batches, **kwargs):
    # Close old database connections in this thread before starting
    # This is necessary because Django database connections are thread-local
    while True:
        try:
            batch = batches.pop()
        except IndexError:
            logger.info(f"reach the end of batches, finished.")
            break
        except Exception as e:
            logger.error(str(e))
        try:
            crawl_authors(batch, **kwargs)
            logger.info(f"left batches: {len(batches)}")
        except Exception as e:
            logger.error("crawl_worker error for batch %s: %s", batch, e)
            # Continue to next batch instead of exiting so remaining batches are still processed

def download_media(url, path, name, **kwargs):
    """Download media file from URL to local path"""
    logger.info("url: %s", url)
    full_path = os.path.join(path, name)
    
    # Create directory if it doesn't exist
    os.makedirs(path, exist_ok=True)
    
    if os.path.isfile(full_path):
        return {'status': 'success', 'message': 'File already exists', 'path': full_path}
    try:
        if kwargs.get("size", 0) > 1048576:
            response = requests.get(url, stream=True, timeout=60)
            size = 0
            chunk_size = 1024 * 1024
            with open(full_path, 'wb') as f:
                for data in response.iter_content(chunk_size=chunk_size):
                    f.write(data)
                    size += len(data)
        else:
            response = requests.get(url, timeout=60)
            response.raise_for_status()
        
            with open(full_path, 'wb') as f:
                f.write(response.content)
        
        return {'status': 'success', 'message': 'Downloaded successfully', 'path': full_path}
    except Exception as e:
        logger.error(f"Error downloading media from {url}: {str(e)}")
        return {'status': 'error', 'message': str(e), 'path': full_path}

def crawl_authors_batch(aids, **kwargs):
    # Fetch all author objects in the main thread
    # authors = list(DyAuthor.objects.filter(id__in=author_ids))
    
    # response queue
    resp_q = queue.Queue()
    batch_size = kwargs.get("batch_size", 30)
    task_id = kwargs.get("task_id")
    logger.info(f"task (id {task_id}) started.")
    # Create batches of author objects (not IDs)
    batches = [aids[i:i+batch_size] for i in range(0, len(aids), batch_size)]
    logger.info("crawl_authors_batch batches: %s batches with %s total aids", len(batches), len(aids))
    default_num_crawl_workers = len(batches) if len(batches) < 2 else 2
    num_crawl_workers = kwargs.get("num_crawl_workers")
    if not num_crawl_workers:
        num_crawl_workers = default_num_crawl_workers
    num_save_workers = kwargs.get("num_save_workers", 3)

    # num_crawl_workers = 1
    # num_save_workers = 1

    crawl_workers = [Thread(target=crawl_worker, args=(i, batches), kwargs={"q": resp_q, **kwargs}) for i in range(num_crawl_workers)]
    logger.info("crawl_workers start: %s", num_crawl_workers)
    for t in crawl_workers:
        t.start()

    sentinel = object()
    save_workers = [Thread(target=save_worker, args=(i, resp_q, sentinel)) for i in range(num_save_workers)]
    logger.info("save_workers start: %s", num_save_workers)
    for t in save_workers:
        t.start()

    for t in crawl_workers:
        t.join()

    for _ in range(num_save_workers):
        resp_q.put(sentinel)
    for t in save_workers:
        t.join()
    logger.info(f"task (id {task_id}) finished.")

def download_videos(video_ids, max_workers=3, **kwargs):
    """
    Download videos using ThreadPoolExecutor.
    """
    # Fetch video objects from IDs
    videos = DyVideo.objects.filter(id__in=video_ids)
    results = []
    
    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        futures = [
            executor.submit(download_media, video.origin_url, os.path.join(DATADIR, video.path), "video.mp4", **kwargs)
            for video in videos
        ]
        for future in futures:
            result = future.result()
            results.append(result)
    
    logger.info(f"Downloaded {len(results)} videos with max_workers={max_workers}")
    return results
