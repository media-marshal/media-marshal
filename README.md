# Media Marshal

面向 Emby / Jellyfin / Plex 用户的自托管媒体文件整理工具。

Media Marshal 会从你的下载目录或临时整理目录中发现媒体文件，使用 guessit 解析文件名，通过 TMDB 匹配电影 / 剧集元数据，在低置信度时进入人工确认队列，然后按路径模板把文件整理到媒体库目录。

> 当前版本：`v0.2.8`
>
> 首个正式发行版本的定位是“可靠整理普通电影 / 剧集文件，并保留人工确认兜底”。它不是下载器，不集成 PT 站，也不是播放器或媒体服务器。

## 目录

- [适合谁](#适合谁)
- [当前能力概览](#当前能力概览)
- [工作流程](#工作流程)
- [支持状态](#支持状态)
- [快速开始](#快速开始)
- [首次配置](#首次配置)
- [路径规则](#路径规则)
- [待确认队列](#待确认队列)
- [路径模板](#路径模板)
- [部署与数据](#部署与数据)
- [环境变量](#环境变量)
- [本地开发](#本地开发)
- [安全边界](#安全边界)
- [版本亮点](#版本亮点)
- [项目结构](#项目结构)
- [技术栈](#技术栈)
- [English Summary](#english-summary)
- [许可证](#许可证)

## 适合谁

Media Marshal 适合这些场景：

- 你使用 Emby / Jellyfin / Plex 管理本地媒体库。
- 你希望把下载完成目录、NAS 临时目录或手工整理目录中的文件自动归档。
- 你希望自动化整理，但不希望识别错误直接污染正式媒体库。
- 你需要在 Docker / NAS / 网络挂载等文件事件不稳定的环境中定时补扫。
- 你希望按自己的目录规则生成电影、剧集路径和文件名。

Media Marshal 当前不做这些事：

- 不下载任何内容。
- 不集成 PT 站、下载器或播放器。
- 不替代 Emby / Jellyfin / Plex 的播放能力。
- 首版不提供用户认证。
- 不建议直接暴露到公网。

## 当前能力概览

### 文件发现

- 支持多条路径规则，每条规则包含源目录、目标目录、媒体类型、路径模板和文件操作方式。
- 支持实时监听、定时扫描、混合模式。
- 支持手动全量扫描已有目录。
- 支持新目录发现后自动注册监听并轻量补扫。
- 支持源文件缺失巡检，避免待确认队列长期残留已经被删除的文件。
- 支持忽略规则，静默跳过样片目录、临时文件、系统文件等噪音。

### 媒体识别与匹配

- 使用 Python FastAPI sidecar 封装 guessit，解析标题、年份、季号、集号、分辨率等信息。
- 使用 TMDB 进行电影 / 剧集搜索、详情获取和候选排序。
- 支持中文 / 双语标题多 query 搜索。
- 支持 TMDB 搜索缓存、详情缓存和 in-flight 去重。
- 支持多维置信度评分：标题、年份、媒体类型、文件结构等。
- 支持多季剧集年份优化：第二季及后续季可按当前季播出年份理解文件年份。
- 支持低置信度时参考父级发布目录，提高缺失标题或标题不完整场景下的召回率。
- 仅父目录召回的候选不会自动确认，降低误整理风险。

### 待确认队列

- 低置信度或无候选任务会进入待确认队列。
- 支持查看系统候选、海报、简介、年份、媒体类型和置信度。
- 支持全局手动搜索 TMDB 关键词或 TMDB ID。
- 支持将搜索结果应用到单个或多个待确认任务。
- 支持当前页批量选择、批量确认、批量跳过。
- 支持编辑待确认任务的当前有效识别信息：
  - 媒体类型：电影 / 剧集
  - 解析标题
  - 解析年份
  - 季号
  - 集号
- 支持“仅保存”和“保存并重新匹配”。
- 重新匹配不会自动确认任务，仍需用户人工确认。
- 支持展示分辨率和连续多集范围。

### 文件整理

- 支持按电影模板和剧集模板生成目标路径。
- 支持文件操作方式：
  - `MOVE`：移动
  - `COPY`：复制
  - `HARD_LINK`：硬链接
  - `SYMBOLIC_LINK`：符号链接
- 目标路径已存在时任务失败；不会覆盖、不会合并、不会自动改名。
- 支持移动 / 复制 / 链接同名附属文件。
- 支持自动生成基础 NFO。
- 支持 MOVE 成功后清理源目录空文件夹，且不会删除源根目录。

### 仪表盘

- 展示总任务数、已完成、待确认、已跳过、失败任务。
- 支持按状态、资产类型、媒体类型筛选。
- 支持按源文件路径和匹配标题关键字搜索。
- 失败任务展示失败次数和错误信息。
- 支持删除单条或批量删除任务记录；该操作不删除媒体文件。

### 系统设置

- 首次启动时，如果没有 TMDB API Key，会进入初始化页面。
- 支持配置 TMDB API Key。
- 支持配置 TMDB 返回语言。
- 支持配置匹配置信度阈值。
- 支持系统重置：清空数据库中的配置、路径规则、任务和候选数据，但不删除媒体文件。
- 页面提供版本号和 ReleaseNote 展示。

## 工作流程

```text
文件发现
  -> guessit 解析
  -> TMDB 搜索与置信度评分
  -> 高置信度自动整理 / 低置信度进入待确认队列
  -> 人工确认或手动修正识别信息
  -> 路径模板渲染
  -> 移动 / 复制 / 硬链接 / 符号链接
  -> 附属文件处理 / NFO 生成
  -> WebSocket 推送任务状态
```

## 支持状态

| 能力 | 当前状态 | 说明 |
|---|---|---|
| 普通电影视频文件 | 支持 | 常见 `.mkv`、`.mp4` 等视频文件 |
| 普通剧集视频文件 | 支持 | 支持季集识别、剧集模板整理 |
| 连续多集文件 | 支持 | 可处理 `E16-E17`、`episode [21..28]` 等连续范围 |
| 电影蓝光原盘目录 | 支持 | 识别 `BDMV/` 结构并按整盘目录整理 |
| ISO 镜像 | 当前跳过 | 能识别为 `ISO_IMAGE`，但当前版本不执行整理 |
| 剧集蓝光原盘 | 当前不支持 | 会跳过并记录原因 |
| 裸 `sourceDir/BDMV` | 当前不作为正常蓝光处理 | 建议放在影片外层目录中 |
| 同名字幕 / NFO / 封面 | 支持 | 由主视频任务带动处理 |
| `.md5` 附属文件 | 支持 | 同主视频 basename 时跟随处理 |
| `.sfv` / `.sha*` | 后续评估 | 当前未纳入默认附属文件规则 |
| Webhook 文件发现 | 后续版本 | 字段预留，接口和 token 鉴权尚未开放 |
| 用户认证 | 首版不支持 | 建议仅部署在可信内网 |
| 邮件通知 | 后端具备低频告警能力，UI 暂未开放 | 可通过环境变量配置，正式使用前建议自行验证 |

## 快速开始

### 1. 准备环境

需要：

- Docker
- Docker Compose
- 一个 TMDB API Key
- 一个可挂载到容器内的媒体目录

### 2. 复制环境变量模板

```bash
cp .env.example .env
```

编辑 `.env`，至少修改：

```dotenv
MEDIA_DIRS=/your/media/path
```

`MEDIA_DIRS` 会挂载到容器内的 `/media`。在 Web UI 中配置路径规则时，请使用容器内路径，例如：

```text
源目录：/media/downloads
目标目录：/media/library/Movies
```

### 3. 启动服务

当前默认 `docker-compose.yml` 会从本仓库 Dockerfile 构建镜像：

```bash
docker compose up -d --build
```

启动后访问：

```text
http://localhost:3000
```

默认端口：

| 服务 | 宿主机端口 | 容器端口 |
|---|---:|---:|
| Portal Web UI | `3000` | `80` |
| Backend API | `8080` | `8080` |
| Parser sidecar | 不对外暴露 | `8000` |

### 4. 查看服务状态

```bash
docker compose ps
docker compose logs -f
```

## 首次配置

首次进入 Web UI 后，系统会要求配置 TMDB API Key。

完成初始化后：

1. 进入“设置 -> 路径设置”。
2. 新增路径规则。
3. 选择源目录和目标目录。
4. 设置媒体类型：电影、剧集或自动识别。
5. 设置文件操作方式：移动、复制、硬链接或符号链接。
6. 选择文件发现模式，推荐优先使用混合模式。
7. 保存规则后，可等待自动发现，也可以手动全量扫描。

TMDB API Key 仅通过 Web UI 保存到数据库配置中，不再从环境变量读取。

## 路径规则

一条路径规则代表一套完整整理策略：

| 字段 | 说明 |
|---|---|
| 规则名称 | 页面展示用名称 |
| 源目录 | 待整理文件所在目录，使用容器内路径 |
| 目标根目录 | 整理后媒体库根目录，使用容器内路径 |
| 媒体类型 | `MOVIE`、`TV_SHOW`、`AUTO` |
| 文件操作方式 | `MOVE`、`COPY`、`HARD_LINK`、`SYMBOLIC_LINK` |
| 电影路径模板 | 电影目标路径和文件名模板 |
| 剧集路径模板 | 剧集目标路径和文件名模板 |
| 文件发现模式 | 实时监听、定时扫描、混合模式 |
| 附属文件处理 | 同名字幕、NFO、封面、MD5 等跟随主视频 |
| NFO 生成 | 没有用户自带 NFO 时自动生成基础 NFO |
| 忽略规则 | 静默忽略样片、临时文件、系统文件等 |

### 文件发现模式

| 模式 | 适用场景 |
|---|---|
| 实时监听 | 本地磁盘，响应快 |
| 定时扫描 | NAS、SMB、Docker volume、网络挂载等事件不稳定环境 |
| 混合模式 | 推荐默认选择，实时监听 + 定时补扫 |

### 文件操作方式

| 操作 | 行为 | 源文件保留 |
|---|---|---|
| `MOVE` | 移动到目标路径 | 否 |
| `COPY` | 复制一份到目标路径 | 是 |
| `HARD_LINK` | 创建硬链接 | 是 |
| `SYMBOLIC_LINK` | 创建符号链接 | 是 |

注意：

- 硬链接通常要求源目录和目标目录位于同一文件系统。
- 符号链接可能需要额外系统权限。
- Media Marshal 不会在目标路径冲突时覆盖已有文件。

## 待确认队列

任务进入待确认队列的常见原因：

- 没有找到 TMDB 候选。
- 候选置信度低于系统阈值。
- 文件名中标题、年份、季集信息不足或不准确。
- 电影 / 剧集类型被 guessit 误判。

在待确认队列中，你可以：

- 查看系统候选。
- 手动搜索 TMDB 关键词或 TMDB ID。
- 把搜索结果应用到多个任务。
- 批量确认当前页任务。
- 批量跳过当前页任务。
- 编辑单个任务的识别信息后重新匹配。

编辑识别信息适合这些场景：

- 剧集被识别成电影。
- 标题解析错了。
- 年份导致 TMDB 搜索不到正确条目。
- 季号 / 集号解析错误。

## 路径模板

默认电影模板：

```text
{title} ({year})/{title} ({year})[[ - {resolution}]]{ext}
```

默认剧集模板：

```text
{title} ({year})/S{season:02d}/{title} ({year}) - S{season:02d}E{episode:02d}[[ - {resolution}]]{ext}
```

常用变量：

| 变量 | 说明 |
|---|---|
| `{title}` | TMDB 本地化标题 |
| `{year}` | TMDB 年份 |
| `{tmdb_id}` | TMDB ID |
| `{media_type}` | `MOVIE` 或 `TV_SHOW` |
| `{season}` / `{season:02d}` | 季号 |
| `{episode}` / `{episode:02d}` | 集号 |
| `{resolution}` | 分辨率 |
| `{ext}` | 原始扩展名，包含点号 |

### 可选片段

使用 `[[ ... ]]` 包裹可选片段。片段内任意变量缺失时，整个片段会被移除。

```text
{title} ({year})[[ - {resolution}]]{ext}
```

如果分辨率为空，结果不会残留多余的 ` - `。

### 格式参数

默认模板保持常见的 `E{episode:02d}` 形式；如果你需要连续多集范围或更本地化的命名，可以使用格式参数。

占位符支持参数：

- `prefix`
- `suffix`
- `separator`
- `repeatPrefix`
- `repeatSuffix`

示例：

```text
{episode:02d;prefix=E}
```

单集渲染为：

```text
E16
```

连续多集渲染为：

```text
E16-E17
```

如果你更喜欢 `E16-17`：

```text
{episode:02d;prefix=E;repeatPrefix=false}
```

## 部署与数据

### Compose 文件

| 文件 | 用途 | 镜像行为 |
|---|---|---|
| `docker-compose.yml` | 默认生产启动 | 从本仓库 Dockerfile 构建镜像 |
| `docker-compose.alpha.yml` | Alpha 镜像测试 | 从 `ghcr.io/media-marshal/*` 拉取镜像 |
| `docker-compose.beta.yml` | 本地 beta 构建测试 | 本地构建镜像，可配置 Maven / npm 镜像源 |

### 数据库

后端使用 SQLite，数据库位于容器内：

```text
/data/media-marshal.db
```

默认 `docker-compose.yml` 使用 Docker volume：

```text
mm_data:/data
```

升级前建议备份该数据库文件或整个 volume。

### 目录挂载

默认配置：

```yaml
${MEDIA_DIRS:-/media}:/media
```

如果宿主机目录是：

```text
/mnt/nas
```

并设置：

```dotenv
MEDIA_DIRS=/mnt/nas
```

那么 Web UI 中应填写容器内路径：

```text
/media/Downloads
/media/Movies
/media/TV
```

## 环境变量

### 常用配置

| 变量 | 默认值 | 说明 |
|---|---|---|
| `MEDIA_DIRS` | `/media` | 宿主机媒体目录，挂载到容器 `/media` |
| `MEDIA_MARSHAL_PORTAL_PORT` | `3000` | Web UI 宿主机端口 |
| `MEDIA_MARSHAL_HTTP_PORT` | `8080` | 后端 API 宿主机端口 |
| `MEDIA_MARSHAL_IMAGE_TAG` | `latest` | 镜像 tag |
| `MEDIA_MARSHAL_CONFIDENCE_THRESHOLD` | `0.8` | 自动确认置信度阈值 |
| `MEDIA_MARSHAL_DEBUG` | `false` | 输出详细识别和匹配日志 |

### TMDB 与匹配调优

| 变量 | 默认值 | 说明 |
|---|---|---|
| `MEDIA_MARSHAL_TMDB_TIMEOUT_SECONDS` | `30` | TMDB 请求超时 |
| `MEDIA_MARSHAL_TMDB_CONFIRM_RETRY_ATTEMPTS` | `3` | 确认时 TMDB 详情重试次数 |
| `MEDIA_MARSHAL_TMDB_SEARCH_CACHE_TTL_MINUTES` | `360` | TMDB 搜索缓存时间 |
| `MEDIA_MARSHAL_TMDB_EMPTY_SEARCH_CACHE_TTL_MINUTES` | `10` | 空搜索结果缓存时间 |
| `MEDIA_MARSHAL_TMDB_DETAIL_CACHE_TTL_MINUTES` | `1440` | TMDB 详情缓存时间 |
| `MEDIA_MARSHAL_TMDB_CACHE_MAX_SIZE` | `5000` | TMDB 缓存最大条目数 |
| `MEDIA_MARSHAL_WATCHER_PARENT_FOLDER_CONTEXT_ENABLED` | `true` | 低置信度时参考父级发布目录 |
| `MEDIA_MARSHAL_WATCHER_PARENT_FOLDER_PARENT_ONLY_CONFIDENCE_CAP` | `0.75` | 仅父目录召回候选的置信度上限 |
| `MEDIA_MARSHAL_WATCHER_PARENT_FOLDER_MUTUAL_BOOST` | `0.05` | 文件名与父目录互证加分 |

### 文件发现与校验

| 变量 | 默认值 | 说明 |
|---|---|---|
| `MEDIA_MARSHAL_WATCHER_DEBOUNCE_SECONDS` | `3` | 文件事件防抖，避免处理仍在复制的文件 |
| `MEDIA_MARSHAL_WATCHER_MISSING_SOURCE_CHECK_SECONDS` | `60` | 源文件缺失巡检间隔 |
| `MEDIA_MARSHAL_WATCH_RULE_PREFLIGHT_ENABLED` | `true` | 保存规则前校验路径和文件操作能力 |

### 邮件通知

| 变量 | 默认值 | 说明 |
|---|---|---|
| `MEDIA_MARSHAL_EMAIL_ENABLED` | `false` | 是否启用低频邮件通知 |
| `MEDIA_MARSHAL_EMAIL_RECIPIENT` | 空 | 收件人 |
| `MEDIA_MARSHAL_MAIL_HOST` | 空 | SMTP 主机 |
| `MEDIA_MARSHAL_MAIL_PORT` | `587` | SMTP 端口 |
| `MEDIA_MARSHAL_MAIL_USERNAME` | 空 | SMTP 用户名 |
| `MEDIA_MARSHAL_MAIL_PASSWORD` | 空 | SMTP 密码 |

说明：当前系统设置页中的邮件开关暂未开放，邮件通知如需使用请通过环境变量配置，并在正式使用前自行验证。

### 本地构建加速

| 变量 | 默认值 | 说明 |
|---|---|---|
| `MAVEN_MIRROR_URL` | 空 | beta / 本地 Docker 构建时使用的 Maven 镜像源 |
| `NPM_REGISTRY` | 空 | beta / 本地 Docker 构建时使用的 npm registry |

## 本地开发

### 启动 parser sidecar

```bash
docker compose up parser -d
```

### 启动后端

```bash
cd backend
mvn spring-boot:run
```

### 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端开发服务默认访问：

```text
http://localhost:3000
```

### 常用检查

```bash
cd backend
mvn test
```

```bash
cd frontend
npm run type-check
npm run build
```

## 安全边界

Media Marshal 当前面向个人自托管和可信内网部署：

- 首版没有用户认证。
- 文件系统浏览接口会暴露容器内可见目录结构。
- 路径规则可以移动、复制、硬链接或符号链接媒体文件。
- 系统重置不会删除媒体文件，但会清空数据库中的配置、规则、任务和候选记录。

建议：

- 仅在本机、家庭局域网、VPN 或可信内网中使用。
- 不要把 Web UI 或后端 API 直接暴露到公网。
- 如必须公网访问，请自行配置反向代理认证、访问控制或 VPN。
- 首次接入正式媒体库前，先使用测试目录验证路径模板和文件操作策略。

## 版本亮点

### v0.2.8

- 低置信度时参考父级发布目录中的中文名 / 英文名。
- 修复英文别名命中中文 TMDB 条目时置信度偏低的问题。
- 父目录召回候选不会直接自动确认。
- 仪表盘任务列表支持按文件路径和匹配标题搜索。

### v0.2.7

- 优化多季剧集的年份匹配。
- 支持同名 `.md5` 文件作为附属文件跟随处理。

### v0.2.6

- 支持连续多集解析。
- 路径模板支持 `prefix`、`suffix`、`separator`、`repeatPrefix`、`repeatSuffix` 参数。
- 待确认队列展示集数范围。

### v0.2.5

- 待确认任务支持编辑识别信息。
- 支持保存后重新 TMDB 匹配。
- 失败任务按错误码聚合并显示失败次数。
- 待确认任务展示分辨率。

### v0.2.4

- 首次初始化页面。
- 系统重置。
- 统一版本号和 ReleaseNote 展示。
- 媒体资产识别与电影蓝光原盘目录支持。
- 待确认队列批量确认 / 批量搜索 / 批量跳过。
- 中文 / 双语标题匹配优化。

## 项目结构

```text
media-marshal/
  backend/                 Java 21 + Spring Boot 后端
  frontend/                Vue 3 + TypeScript + Element Plus 前端
  parser/                  Python FastAPI + guessit sidecar
  docs/                    文档资料
  docker-compose.yml       默认本地构建部署
  docker-compose.alpha.yml Alpha 镜像部署
  docker-compose.beta.yml  Beta 本地构建部署
  .env.example             环境变量模板
  VERSION                  项目统一版本号
```

## 技术栈

| 层 | 技术 |
|---|---|
| Backend | Java 21, Spring Boot 3.3, JPA, SQLite |
| Frontend | Vue 3, TypeScript, Vite, Pinia, Element Plus, vue-i18n |
| Parser | Python 3, FastAPI, guessit |
| Metadata | TMDB API |
| Realtime | WebSocket / STOMP |
| Deployment | Docker, Docker Compose |

## English Summary

Media Marshal is a self-hosted media organizer for Emby, Jellyfin, and Plex users.

It watches download or staging folders, parses filenames with guessit, searches TMDB for movie / TV metadata, sends uncertain matches to a manual review queue, and organizes files into your media library using configurable path templates.

Current focus:

- Regular movie and TV episode files
- Manual review and batch confirmation
- Editable recognition info before confirmation
- Configurable movie / TV path templates
- Move, copy, hard link, and symbolic link operations
- Associated subtitle / NFO / poster / MD5 handling
- Movie Blu-ray directory support
- Docker Compose deployment

Current limitations:

- No built-in authentication
- Not intended for direct public internet exposure
- ISO organization is not fully supported yet
- TV Blu-ray discs are not supported yet
- Webhook discovery is reserved for a future release

## 许可证

[GPL-3.0](LICENSE)

## 致谢

- [guessit](https://github.com/guessit-io/guessit)
- [TMDB](https://www.themoviedb.org/)
- Emby / Jellyfin / Plex 社区用户的媒体库命名实践
