# SimpleSpider（抖音内容爬取与管理）

这是一个使用 Django REST Framework 和 VUE3 开发的抖音内容管理与抓取项目，并附带可选的 **Android 客户端**。系统包含：

- 爬虫基于 playwright，可批量爬取喜欢/收藏视频，可批量爬取多位作者的所有视频
- 便捷的 UI 功能，通过前端 UI 下发任务，包含丰富的任务配置：爬取的请求数量、按过滤结果爬取等
- 内容管理功能，管理爬取到的作者和视频（Web 前端基于 Vue 3 和 element-plus；Android 端可浏览作者与视频、竖滑播放、评分与筛选）
- 自动评分，可导出人工评分的数据并训练模型，再通过模型自动评分

## 目录说明

- `backend/`：Django 后端（API、数据库、任务队列、管理命令）
- `frontend/`：前端（Vue 3）
- `android/`：Android 客户端源码
- `templates/`：部署模板（systemd service、Nginx 配置等）

## 快速开始

爬虫，API，数据处理

### 后端

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

### 前端

内容管理、展示、任务下发、数据标记

#### 界面预览

以下为部分页面截图（资源位于 `docs/images/`）。

**作者列表**

![作者列表](docs/images/author_list_page.png)

**作者详情**

![作者详情](docs/images/author_detail_page.png)

**视频列表**

![视频列表](docs/images/video_list_page.png)

**任务列表**

![任务列表](docs/images/task_list_page.png)

1. 构建静态资源

```bash
cd frontend
npm install
npm run build
```

2. 让前端静态文件被后端/Nginx 提供

- 若使用 Nginx，配合 `templates/simplespider.conf`
- 若使用 gunicorn/django 方式，也需要确保静态目录与路由配置正确

## Android 客户端（可选）

### 获取安装包

预编译 APK（文件名与版本以该下载为准）：[simplespider-0.0.1.apk](http://lufy.org:8000/simplespider-0.0.1.apk)

### 使用前请确认

- 本项目的 **Django 后端** 已启动且手机或模拟器 **能访问** 到该地址（例如同一局域网内的 `http://电脑IP:8000`）。
- 已在后台 **创建好登录用户**；应用里用该账号密码登录。
- 安装打开后，若列表或登录一直失败，请到 **设置** 页，把 **API server** 改成你的实际 **主机或 `主机:端口`**（不必手写 `http://`，按界面说明即可），再点 **Apply API address**。

### 界面与用法说明

- **登录**：输入用户名、密码后登录；登录页也可进入 **设置** 先改 API 地址。
- **底部三个入口**：**作者**、**视频**、**设置**。
- **作者**：浏览作者列表，点一行进入 **作者详情**（头像、昵称、状态、评分等）；下方是该作者的 **视频网格**，点某个视频即 **全屏播放**。
- **视频**：浏览全库视频的 **网格**；在 **视频** 页从顶部可 **下滑拉出搜索框**，输入关键词后稍停片刻会自动按搜索刷新列表；列表向下滑动到底会自动加载更多。
- **播放**：竖向 **上/下滑** 切换上一条、下一条；**点一下画面** 暂停或继续；暂停时会显示进度等信息。右侧有 **作者头像**，可点 **菜单（⋮）** 给当前视频 **打分**；也可从菜单进入 **作者详情**（从播放器打开的作者页带 **系统返回** 即可回到播放）。
- **设置**：除 **API 地址** 外，还可打开 **随机顺序**（视频列表每次打乱顺序）、选择 **一条播完后** 是接播下一条还是重播当前条、调整 **视频列表每行几个格子**；另有 **「仅影响视频列表请求」的筛选**（例如按评分、评分区间、是否点赞/收藏、处理状态等过滤列表）。需要离开当前账号时使用 **Log out**。

### 自行从源码编译（可选）

若要在本机构建：用 **Android Studio** 打开仓库里的 **`android`** 目录，待 Gradle 同步完成后，直接 **Run** 到设备，或通过菜单生成 APK 即可。

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

