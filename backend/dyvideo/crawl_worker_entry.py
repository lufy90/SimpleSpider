import os


def crawl_process_entry(worker_id, work_q, resp_q, crawl_kwargs):
    import django
    from django.apps import apps
    from django.db import close_old_connections

    os.environ.setdefault("DJANGO_SETTINGS_MODULE", "backend.settings")
    if not apps.ready:
        django.setup()

    from dyvideo.utils import crawl_worker_loop

    close_old_connections()
    crawl_worker_loop(worker_id, work_q, resp_q, crawl_kwargs)
