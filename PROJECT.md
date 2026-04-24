# MediaMarshal 项目核心信息文档

## 项目定位

**一句话描述**：为 Emby / Jellyfin / Plex 用户提供媒体文件自动整理工具——识别、重命名、生成 NFO 元数据，Docker 原生部署，Web UI 操作。

**核心差异化**：FileBot（强命名）+ tinyMediaManager（强刮削）的整合优化版，比 MoviePilot 更轻量、更聚焦、国际化更好。

**不做的事**：不做内容下载，不做 PT 站集成，不做播放器。

---

## 核心功能（MVP 范围）

1. 监控指定目录，有新文件时自动触发识别流程
2. 自动判断视频是电影还是剧集
3. 对接 TMDB API，搜索匹配影片信息
4. 按标准命名规范重命名文件（支持用户自定义模板）
5. 生成 NFO 元数据文件（兼容 Emby / Jellyfin / Plex）
6. 低置信度匹配进入"待人工确认"队列，不静默失败

---

## 已确定的技术决策

| 项目 | 决策 | 理由 |
|------|------|------|
| 后端语言 | Java（可考虑 Kotlin） | 用户最擅长，完成度优先于生态契合 |
| 前端 | Vue + shadcn/ui 风格组件 | 用户熟悉，现代化 UI |
| 文件名解析 | guessit（Python sidecar 微服务） | 最成熟的视频文件名解析库 |
| 元数据来源 | TMDB API v3 | 免费、稳定、全球覆盖 |
| 部署方式 | Docker + Docker Compose | NAS 用户首选 |
| 文件监控 | Java WatchService | 原生，无额外依赖 |
| NFO 规范 | Kodi/Emby 通用 XML 格式 | 三大媒体服务器均兼容 |
| License | GPL-3.0 | 防商业闭源，与 Jellyfin/Sonarr 生态一致 |
| 数据源架构 | 插件/适配器模式 | 方便未来接入豆瓣等其他数据源 |

---

## guessit Sidecar 方案

```yaml
# docker-compose.yml 结构
services:
  media-marshal:     # Java 主服务
    depends_on:
      - parser
  parser:            # Python guessit 微服务
    # FastAPI 封装 guessit，Java 通过 HTTP 调用
    # 单次解析延迟约 1-3ms，进程常驻无冷启动开销
```

未来 v2.0 可考虑用 Java 重写解析逻辑，彻底去掉 Python 依赖。

---

## 项目基础设施

| 资源 | 地址 / 状态 |
|------|------------|
| GitHub Organization | github.com/media-marshal |
| GitHub Repository | github.com/media-marshal/media-marshal |
| 域名 | media-marshal.com（已注册） |
| Repo Description | "Automatically organize your media library — identify, rename, and generate NFO metadata for movies and TV shows. Docker-native, Web UI, TMDB-powered." |

---

## 命名规范

| 场景 | 格式 | 示例 |
|------|------|------|
| 品牌展示名 | `Media Marshal` | README 标题、Landing Page |
| GitHub / Docker 仓库 | `media-marshal` | github.com/media-marshal/media-marshal |
| Docker Hub namespace | `mediamarshal` | mediamarshal/media-marshal:latest |
| Java 主类 | `MediaMarshal` | PascalCase |
| 环境变量 | `MEDIA_MARSHAL_` | MEDIA_MARSHAL_TMDB_KEY |
| Docker Compose service | `media-marshal` | kebab-case |

---

## 推进路线图

```
第 1-6 周    Phase 1   核心逻辑：文件名解析 + TMDB 匹配 + 重命名流程
第 7-12 周   Phase 2   MVP 可用：Docker 启动 + Web UI + 文件监控 + NFO 生成
                        → 开始 build in public（开发日志）
第 3-5 月    Phase 3   Alpha：小圈子内测，打磨识别准确率
                        → 发 r/selfhosted、Jellyfin Discord、V2EX
第 5-7 月    Phase 4   v1.0 发布：全渠道宣发
                        → Reddit / Hacker News Show HN / Product Hunt
```

---

## 主要竞品参考

| 工具 | 与本项目关系 |
|------|------------|
| MoviePilot | 功能最接近，但绑定 PT 下载、配置极复杂、仅中文社区 |
| FileBot | 命名强但收费、无 Web UI、无 NFO 生成 |
| tinyMediaManager | 刮削强但无法服务端无头运行、无文件监控 |
| Sonarr / Radarr | 下载管理器，不适合整理已有本地库 |
| fixarr | 有 Web UI 但无文件监控、不生成 NFO |

---

## 国际化策略

- i18n 从第一行代码开始（vue-i18n）
- README 中英文双语
- 核心识别逻辑不写死中文特有规则，做成可配置解析规则
- 命名格式做成用户可配置模板，内置多套预设
