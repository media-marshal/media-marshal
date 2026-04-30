# Media Marshal

> Automatically organize your media library — identify, rename, and generate NFO metadata for movies and TV shows. Docker-native, Web UI, TMDB-powered.

[中文](#中文说明) | [English](#english)

---

## English

### What it does

Media Marshal watches your download directories and automatically:
1. Detects new video files
2. Parses filenames using [guessit](https://github.com/guessit-io/guessit)
3. Searches [TMDB](https://www.themoviedb.org/) for metadata
4. Renames files using a configurable template
5. Generates `.nfo` metadata files compatible with Emby / Jellyfin / Plex
6. Sends low-confidence matches to a manual review queue (no silent failures)

### Quick Start

```bash
# 1. Copy environment template
cp .env.example .env

# 2. Start all services
docker compose up -d

# 3. Open Web UI and set the TMDB API key in System Settings
open http://localhost:3000
```

### Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `MEDIA_DIRS` | Host media directory mounted into the container. Production and beta mount it to `/media`; alpha mounts it to `/Resources`. | Production/beta: `/media`; alpha: `/Resources` | Yes |
| `MEDIA_MARSHAL_CONFIDENCE_THRESHOLD` | Auto-processing confidence threshold. Matches below this value enter the manual confirmation queue. Valid range: `0.0` to `1.0`. | `0.8` | No |
| `MEDIA_MARSHAL_DEBUG` | Enables verbose backend debug logs. Currently passed by alpha and beta compose files. | `false` | No |
| `MEDIA_MARSHAL_EMAIL_ENABLED` | Enables email notifications. | `false` | No |
| `MEDIA_MARSHAL_EMAIL_RECIPIENT` | Notification recipient email address. Required only when email notifications are enabled. | empty | Conditional |
| `MEDIA_MARSHAL_MAIL_HOST` | SMTP server host. Required only when email notifications are enabled. | empty | Conditional |
| `MEDIA_MARSHAL_MAIL_PORT` | SMTP server port. | `587` | No |
| `MEDIA_MARSHAL_MAIL_USERNAME` | SMTP username. Required only when your SMTP server requires authentication. | empty | Conditional |
| `MEDIA_MARSHAL_MAIL_PASSWORD` | SMTP password or app password. Required only when your SMTP server requires authentication. | empty | Conditional |
| `MEDIA_MARSHAL_PORTAL_PORT` | Host port exposed by the Portal Web UI. The container port is always `80`. | `3000` | No |
| `MEDIA_MARSHAL_HTTP_PORT` | Host port exposed by the backend API in alpha and beta compose files. The container port is always `8080`. Production compose currently exposes `8080:8080`. | `8080` | No |
| `MEDIA_MARSHAL_IMAGE_TAG` | Image tag used by alpha image deployment and beta locally built images. | alpha: `alpha`; beta: `beta`; `.env.example`: `latest` | No |
| `MAVEN_MIRROR_URL` | Optional Maven mirror URL used only by beta Docker builds. Leave empty to use the default Maven repositories. | empty | No |
| `NPM_REGISTRY` | Optional npm registry used only by beta Docker builds. Leave empty to use the default npm registry. | empty | No |

Watch directories, target directories, and file operations are configured in the Web UI path settings page.
The TMDB API key is configured only in the Web UI system settings page; it is no longer read from environment variables.

### Compose Files

| File | Purpose | Image behavior |
|------|---------|----------------|
| `docker-compose.yml` | Production startup | Builds from local Dockerfiles with default Maven / npm sources |
| `docker-compose.alpha.yml` | Alpha testing | Pulls prebuilt images from `ghcr.io/media-marshal/*` |
| `docker-compose.beta.yml` | Beta testing | Builds local images with `pull_policy: build`; optional Maven / npm mirrors are controlled by `MAVEN_MIRROR_URL` and `NPM_REGISTRY` |

### Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3, SQLite |
| Frontend | Vue 3, TypeScript, Element Plus |
| Parser | Python 3, FastAPI, guessit |
| Transport | REST API + WebSocket (STOMP) |
| Deployment | Docker + Docker Compose |

---

## 中文说明

### 功能介绍

Media Marshal 监控您的下载目录，自动完成：
1. 发现新视频文件
2. 使用 guessit 解析文件名
3. 搜索 TMDB 元数据
4. 按命名模板重命名文件
5. 生成兼容 Emby / Jellyfin / Plex 的 `.nfo` 元数据文件
6. 低置信度匹配进入人工确认队列（不静默失败）

### 快速启动

```bash
# 1. 复制环境变量模板
cp .env.example .env

# 2. 启动所有服务
docker compose up -d

# 3. 打开 Web UI，并在系统设置中填写 TMDB API Key
open http://localhost:3000
```

### 本地开发

```bash
# 启动 parser sidecar
docker compose up parser -d

# 启动 Java 后端（另开终端）
cd backend && mvn spring-boot:run

# 启动前端（另开终端）
cd frontend && npm install && npm run dev
```

### 环境变量

| 参数名 | 参数解释 | 默认值 | 是否必填 |
|--------|----------|--------|----------|
| `MEDIA_DIRS` | 宿主机媒体目录挂载路径。生产和 beta 会挂载到容器 `/media`；alpha 会挂载到容器 `/Resources`。 | 生产/beta：`/media`；alpha：`/Resources` | 是 |
| `MEDIA_MARSHAL_CONFIDENCE_THRESHOLD` | 自动处理置信度阈值，低于该值进入人工确认队列。取值范围：`0.0` 到 `1.0`。 | `0.8` | 否 |
| `MEDIA_MARSHAL_DEBUG` | 是否开启后端详细调试日志。当前 alpha 和 beta compose 会传入该变量。 | `false` | 否 |
| `MEDIA_MARSHAL_EMAIL_ENABLED` | 是否开启邮件通知。 | `false` | 否 |
| `MEDIA_MARSHAL_EMAIL_RECIPIENT` | 邮件通知收件人。仅开启邮件通知时必填。 | 空 | 条件必填 |
| `MEDIA_MARSHAL_MAIL_HOST` | SMTP 服务地址。仅开启邮件通知时必填。 | 空 | 条件必填 |
| `MEDIA_MARSHAL_MAIL_PORT` | SMTP 服务端口。 | `587` | 否 |
| `MEDIA_MARSHAL_MAIL_USERNAME` | SMTP 用户名。仅 SMTP 服务要求认证时必填。 | 空 | 条件必填 |
| `MEDIA_MARSHAL_MAIL_PASSWORD` | SMTP 密码或应用专用密码。仅 SMTP 服务要求认证时必填。 | 空 | 条件必填 |
| `MEDIA_MARSHAL_PORTAL_PORT` | Portal Web UI 暴露到宿主机的端口，容器内端口固定为 `80`。 | `3000` | 否 |
| `MEDIA_MARSHAL_HTTP_PORT` | alpha / beta compose 中后端 API 暴露到宿主机的端口，容器内端口固定为 `8080`。生产 compose 当前固定为 `8080:8080`。 | `8080` | 否 |
| `MEDIA_MARSHAL_IMAGE_TAG` | alpha 拉取镜像和 beta 本地构建镜像使用的 tag。 | alpha：`alpha`；beta：`beta`；`.env.example`：`latest` | 否 |
| `MAVEN_MIRROR_URL` | beta Docker 构建时可选的 Maven 镜像源地址。留空时使用 Maven 官方默认源。 | 空 | 否 |
| `NPM_REGISTRY` | beta Docker 构建时可选的 npm registry。留空时使用 npm 官方默认源。 | 空 | 否 |

监控目录、目标目录、文件操作方式、路径模板等整理规则在 Web UI 的路径设置页面中配置。
TMDB API Key 仅在 Web UI 的系统设置页面中配置，不再从环境变量读取。

### Compose 文件

| 文件 | 用途 | 镜像行为 |
|------|------|----------|
| `docker-compose.yml` | 生产启动 | 使用本地 Dockerfile 构建，Maven / npm 使用默认源 |
| `docker-compose.alpha.yml` | alpha 测试 | 从 `ghcr.io/media-marshal/*` 拉取预构建镜像 |
| `docker-compose.beta.yml` | beta 测试 | 使用 `pull_policy: build` 本地构建镜像；可通过 `MAVEN_MIRROR_URL` 和 `NPM_REGISTRY` 配置 Maven / npm 镜像源 |

### 许可证

[GPL-3.0](LICENSE)
