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
# Edit .env and set MEDIA_MARSHAL_TMDB_API_KEY

# 2. Start all services
docker compose up -d

# 3. Open Web UI
open http://localhost:3000
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `MEDIA_DIRS` | Host media directory mounted into the container at `/media` | `/media` |
| `MEDIA_MARSHAL_TMDB_API_KEY` | TMDB API v3 key (required) | — |
| `MEDIA_MARSHAL_CONFIDENCE_THRESHOLD` | Auto-process threshold (0.0–1.0) | `0.8` |
| `MEDIA_MARSHAL_EMAIL_ENABLED` | Enable email notifications | `false` |
| `MEDIA_MARSHAL_EMAIL_RECIPIENT` | Notification recipient email | — |

Watch directories, target directories, and file operations are configured in the Web UI path settings page.

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
# 编辑 .env，填写 MEDIA_MARSHAL_TMDB_API_KEY

# 2. 启动所有服务
docker compose up -d

# 3. 打开 Web UI
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

### 许可证

[GPL-3.0](LICENSE)
