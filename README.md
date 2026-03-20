# SimpleSpider（抖音内容爬取与管理）

这是一个使用 Django REST Framework 和 VUE3 开发的抖音（TikTok）内容管理与抓取项目。系统包含：

- 爬虫基于 playwright，可批量爬取喜欢/收藏视频，可批量爬取作者的所有视频
- 便捷的 UI 功能，通过前端 UI 下发任务，包含丰富的任务配置：爬取的请求数量、按过滤结果爬取等
- 内容管理功能，管理爬取到的作者和视频 (前端基于 VUE3 和 element-plus)
- 自动评分，可导出人工评分的数据并训练模型，再通过模型自动评分

## 目录说明

- `backend/`：Django 后端（API、数据库、任务队列、管理命令）
- `frontend/`：前端（Vue 3）
- `templates/`：部署模板（systemd service、Nginx 配置等）

## 快速开始（后端）

前提说明：

- 需要在 Linux 桌面图形环境运行（`set_cookies` 会打开有界面浏览器）
- 后端默认使用 Postgresql
- 项目里自定义的任务队列通过 `python manage.py start_workers` 启动
- 默认服务端口：`8000`

1. 安装依赖

```bash
cd backend
pip install -r requirements.txt
playwright install
```

2. 初始化数据库

```bash
python manage.py makemigrations
python manage.py migrate
python manage.py createsuperuser
```

3. 配置 IP/端口（前后端互通）

- 修改 `backend/backend/settings.py`
  - 如需要跨域访问，请调整 `CORS_ALLOWED_ORIGINS`
  - 如你使用 MariaDB/PostgreSQL，请把 `DATABASES` 里对应的 `HOST/PORT` 改成你的实际地址
- 修改 `frontend/src/config/index.js`
  - 根据你实际访问前端的主机名，调整 `HOST_TO_APIDOMAIN`
  - 同时更新 `DEFAULT_APIDOMAIN`，确保 `APIURL/STATICURL/DYURL` 指向你的后端 `host:port`

4. 设置 Cookie（首次/更新时）

```bash
python manage.py set_cookies
```

命令会打开一个有界面浏览器窗口，你需要手动登录并完成授权，等待命令保存 cookie。

5. 启动后台 worker

```bash
python manage.py start_workers --max-concurrent-tasks 2
```

该命令会常驻运行，并定期从数据库拉取待处理任务执行抓取/下载/校验等逻辑。

6. 启动 API 服务

开发调试：

```bash
python manage.py runserver 0.0.0.0:8000
```

生产部署一般建议使用 gunicorn（可参考 `templates/simplespider.service`）。

访问：

- Django 管理后台：`http://localhost:8000/admin/`
- API：`http://localhost:8000/api/`

## 快速开始（前端，可选）

1. 构建静态资源

```bash
cd frontend
npm install
npm run build
```

2. 让前端静态文件被后端/Nginx 提供

- 若使用 Nginx，配合 `templates/simplespider.conf`
- 若使用 gunicorn/django 方式，也需要确保静态目录与路由配置正确

## 部署模板（可选）

- `templates/simplespider.service`：gunicorn（API）systemd 模板
- `templates/start_workers.service`：worker 进程（数据库任务队列）systemd 模板

你可以把其中的占位符替换为你的实际路径（如项目根目录、虚拟环境路径），再用 `systemctl` 启动。

## 许可证（Strict）

本项目使用 `GNU Affero General Public License v3.0 only`（AGPL-3.0-only）许可协议。
许可证条款请参见：<https://www.gnu.org/licenses/agpl-3.0.html>

## 使用与合规免责声明

本项目仅用于学习与研究目的。任何人使用本项目及其产生的结果，应当遵守中华人民共和国相关法律法规。
若存在任何违反中国法律法规的使用情形及其后果，由使用者自行承担全部责任；与项目作者无关。

