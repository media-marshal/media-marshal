# Media Marshal 架构文档

> 本文档为开发者参考文档，描述项目整体架构、模块职责、关键设计决策和编程规范。

---

## 目录结构

```
media-marshal/
├── backend/          Java Spring Boot 主服务
├── frontend/         Vue 3 + Element Plus Web UI
├── parser/           Python FastAPI + guessit 解析 sidecar
├── docker-compose.yml           生产部署编排
├── docker-compose.dev.yml       开发环境覆盖配置
└── ARCHITECTURE.md              本文档
```

---

## 服务架构图

```
┌─────────────────────────────────────────────────────────┐
│  用户浏览器                                               │
│  Vue 3 + Element Plus                                   │
│  - 仪表盘 / 待确认队列 / 设置                             │
└──────────────┬──────────────────────┬───────────────────┘
               │ REST /api/*          │ WebSocket /ws
               ▼                      ▼
┌─────────────────────────────────────────────────────────┐
│  Java Spring Boot（media-marshal:8080）                  │
│                                                         │
│  FileWatcherService  ──►  MediaProcessPipeline           │
│                              │                          │
│                    ┌─────────┼──────────┐               │
│                    ▼         ▼          ▼               │
│             ParserClient  TMDB      RenameService       │
│             (HTTP)        Matcher   NfoGenerator        │
│                    │                                    │
│              SettingsService  ◄── AppSetting (SQLite)   │
│              EventPublisher  ──► /topic/tasks (WS)      │
│              EmailNotification                          │
└──────────────┬──────────────────────────────────────────┘
               │ HTTP GET /parse?filename=
               ▼
┌─────────────────────────────────────────────────────────┐
│  Python FastAPI（parser:8000）                           │
│  guessit 文件名解析                                       │
└─────────────────────────────────────────────────────────┘
```

---

## 核心流程（MediaProcessPipeline）

```
文件发现
  │
  ▼
Step 1: 创建 MediaTask（状态: PENDING）
  │
  ▼
Step 2: guessit 解析文件名 → ParseResult
  │
  ▼
Step 3: 判断媒体类型（MOVIE / TV_SHOW）
  │
  ▼
Step 4: TMDB 搜索 → List<MatchResult>（按置信度降序）
  │
  ├─ 置信度 ≥ 阈值 ──────────────────────────┐
  │                                          │
  └─ 置信度 < 阈值                            │
      │                                      │
      ▼                                      ▼
    状态: AWAITING_CONFIRMATION         Step 6: 重命名文件
    WebSocket 推送 + 邮件通知                 │
    等待用户在队列页确认                        ▼
                                        Step 7: 生成 NFO
                                            │
                                            ▼
                                       状态: DONE
                                       WebSocket 推送
```

---

## 模块说明

### backend/

| 包 | 职责 |
|----|------|
| `config/` | Spring 配置类（WebSocket、CORS、Mail） |
| `controller/` | REST 控制器，仅做参数校验和服务委托 |
| `service/pipeline/` | 核心业务流程编排（MediaProcessPipeline） |
| `service/watcher/` | Java WatchService 文件监控 |
| `service/parser/` | guessit sidecar HTTP 客户端 |
| `service/matcher/` | MetadataMatcher 接口 + TMDB 实现（适配器模式） |
| `service/rename/` | FileOperationStrategy 接口 + 各实现（策略模式） |
| `service/nfo/` | NFO XML 文件生成 |
| `service/settings/` | 三源配置优先级管理（环境变量 > yml > DB） |
| `websocket/` | STOMP 事件发布 |
| `notification/` | 邮件通知 |
| `model/entity/` | JPA 实体（MediaTask、AppSetting） |
| `model/dto/` | 数据传输对象（ParseResult、MatchResult、ApiResponse） |
| `repository/` | Spring Data JPA Repository |

### frontend/

| 目录 | 职责 |
|------|------|
| `src/views/` | 页面组件（Dashboard、Queue、Settings） |
| `src/components/` | 共享 UI 组件（AppLayout 等） |
| `src/stores/` | Pinia 状态管理 |
| `src/api/` | Axios HTTP 封装 |
| `src/websocket/` | STOMP WebSocket 客户端 |
| `src/i18n/` | vue-i18n 国际化（zh/en） |
| `src/types/` | TypeScript 类型定义 |
| `src/router/` | Vue Router 路由配置 |

### parser/

单文件 FastAPI 应用，封装 guessit。
无状态、无持久化，仅做文件名解析。

---

## 关键设计模式

### 策略模式（文件操作）

`FileOperationStrategy` 接口定义 `execute(source, target)` 合约。
`RenameService` 通过配置项 `operation.strategy` 动态选择实现。
新增操作类型只需：①新建实现类 + `@Component` ②无需修改任何现有代码。

### 适配器模式（元数据来源）

`MetadataMatcher` 接口屏蔽数据源差异。
当前实现：`TmdbMetadataMatcher`。
未来可添加豆瓣等数据源，只需实现接口并注入不同配置。

### 配置优先级

```
环境变量（MEDIA_MARSHAL_*）
    ↓ 未设置时
application.yml（media-marshal.*）
    ↓ 未设置时
数据库（app_setting 表，Web UI 写入）
    ↓ 未配置时
代码中的 defaultValue
```

所有模块通过 `SettingsService.get(key, defaultValue)` 读取配置，不直接使用 `@Value`。

---

## 数据库

- **类型**：SQLite（文件路径：Docker 卷 `/data/media-marshal.db`）
- **ORM**：Spring Data JPA + Hibernate Community Dialects
- **DDL**：`ddl-auto: update`（开发），生产考虑切换为 `validate` + Flyway

### 主要表

| 表 | 实体类 | 说明 |
|----|--------|------|
| `media_task` | `MediaTask` | 每个文件的处理任务记录 |
| `app_setting` | `AppSetting` | 系统配置（Web UI 可修改的配置项） |

---

## API 设计

所有接口返回统一结构：
```json
{ "success": true/false, "message": "...", "data": {...} }
```

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/tasks` | 查询任务列表，支持 `?status=` 过滤 |
| GET | `/api/tasks/{id}` | 任务详情 |
| DELETE | `/api/tasks/{id}` | 删除任务记录 |
| GET | `/api/queue` | 待确认任务列表 |
| POST | `/api/queue/{id}/confirm` | 人工确认（指定 TMDB ID） |
| POST | `/api/queue/{id}/skip` | 跳过任务 |
| GET | `/api/settings` | 查询所有配置（敏感项脱敏） |
| PUT | `/api/settings/{key}` | 更新配置项 |

WebSocket：
- 端点：`ws://host/ws`（STOMP）
- 订阅：`/topic/tasks`（接收任务状态变更推送）

---

## 开发规范

详见各模块的 `.cursor/rules/` 文件。
