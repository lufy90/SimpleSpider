# Django Backend for Douyin Crawler

This is a Django REST API backend for the Douyin (TikTok) content management system, rewritten from the original FastAPI implementation.

## Features

- **User Management**: Custom user model with admin permissions
- **Douyin Author Management**: CRUD operations for Douyin authors
- **Douyin Video Management**: CRUD operations for Douyin videos
- **Background Tasks**: Database-backed task queue for crawling and media download tasks
- **Auto Video Rating**: ONNX-based auto rating (updates `DyVideo.rate` + `is_auto_rated`)
- **Training Dataset Export**: Export covers and video frame captures for model training
- **JWT Authentication**: Secure API authentication
- **CORS Support**: Cross-origin resource sharing for frontend integration

## Installation

运行环境：需要在 Linux 桌面图形环境中运行（`set_cookies` 需要打开有界面浏览器）。

1. **Install Dependencies**:
   ```bash
   pip install -r requirements.txt
   ```

2. **Database Setup**:
   ```bash
   python manage.py makemigrations
   python manage.py migrate
   ```

3. **Configure IPs / ports**:
   - Edit `backend/settings.py`:
     - Update `CORS_ALLOWED_ORIGINS` for your frontend address (if needed)
     - If you switch to MariaDB/PostgreSQL, update the `DATABASES` entries `HOST/PORT`
   - Edit `frontend/src/config/index.js`:
     - Update `HOST_TO_APIDOMAIN` and `DEFAULT_APIDOMAIN` so `APIURL` points to your backend `host:port`

4. **Create Admin User**:
   ```bash
   python manage.py createsuperuser
   ```

5. **Set Cookies**:
   ```bash
   python manage.py set_cookies
   ```

   This command opens a headed browser window. Please log in manually and wait for the command to save cookies.

6. **Start Workers** (in a separate terminal):
   ```bash
   python manage.py start_workers --max-concurrent-tasks 2
   ```

7. **Start Django Server**:
   ```bash
   python manage.py runserver 0.0.0.0:8000
   ```

## API Endpoints

### Authentication
- `POST /api/token/` - Login (get JWT token)
- `POST /api/token/refresh/` - Refresh JWT token

### User Management (Admin only)
- `GET /api/user/` - List users
- `POST /api/user/` - Create user
- `GET /api/user/{id}/` - Get user details
- `PATCH /api/user/{id}/` - Update user
- `DELETE /api/user/{id}/` - Delete user

### Self Management
- `GET /api/self/` - Get current user info
- `PATCH /api/self/setpass/` - Change password

### Douyin Authors
- `GET /api/dy/author/` - List authors
- `POST /api/dy/author/` - Create author
- `GET /api/dy/author/{id}/` - Get author details
- `PATCH /api/dy/author/{id}/` - Update author
- `DELETE /api/dy/author/{id}/` - Delete author

### Douyin Videos
- `GET /api/dy/video/` - List videos
- `POST /api/dy/video/` - Create video
- `GET /api/dy/video/{id}/` - Get video details
- `PATCH /api/dy/video/{id}/` - Update video
- `DELETE /api/dy/video/{id}/` - Delete video

### Crawling & Download
- `POST /api/dy/author/crawl/` - Start crawling authors (by `ids` or by filters/pagination)
- `POST /api/dy/task/crawl-all/` - Start crawling all authors (by filters)
- `POST /api/dy/video/crawl/` - Start crawling a single video by URL
- `POST /api/dy/video/download/` - Download videos

## Auto rating & dataset export

### ONNX auto rating
- `python manage.py auto_rate` (quick mode from `cover.jpg`)
- `python manage.py auto_rate -p` (precise mode: average over N frames from `video.mp4`)

### Export training dataset
- Export covers (recommended for training input):
  - `python manage.py export_covers -o dataset`
- Export video frame captures:
  - `python manage.py export_captures --num-captures 5 -o dataset_captures`

