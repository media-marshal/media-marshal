# Media Marshal 部署指南

> 本文档用于记录 Docker、Compose、NAS 等部署相关说明。

## 1. 部署方式概览

TODO:
- Docker Compose 生产部署
- Alpha 镜像部署
- Beta 本地构建部署
- 本地开发模式

## 2. Compose 文件说明

TODO:
- `docker-compose.yml`
- `docker-compose.alpha.yml`
- `docker-compose.beta.yml`
- 适用场景和差异

## 3. 环境变量

TODO:
- 必填变量
- 可选变量
- 端口配置
- 镜像 tag
- 构建加速参数

## 4. 目录挂载

TODO:
- 媒体目录挂载
- 数据目录挂载
- 容器内路径和宿主机路径的关系
- NAS 路径注意事项

## 5. 首次启动

TODO:
- 复制 `.env.example`
- 修改 `.env`
- 启动服务
- 检查容器状态
- 打开 Web UI

## 6. TMDB API Key 配置

首次启动后，在 Web UI 的初始化页面填写 TMDB API Key。后续可在“设置 → 系统设置”中修改 API Key、TMDB 返回语言和 TMDB HTTP 代理。

如果部署环境无法直连 TMDB API，可以配置 HTTP 代理：

```env
MEDIA_MARSHAL_TMDB_PROXY_ENABLED=true
MEDIA_MARSHAL_TMDB_PROXY_HTTP_URL=http://127.0.0.1:7890
```

说明：
- 第一版仅代理 TMDB API 请求，不代理海报图片、Parser、邮件或其它外部请求。
- 仅支持不带认证信息的 HTTP 代理，不支持 SOCKS、HTTPS 代理和需要用户名密码的代理。
- Web UI 中保存的代理配置优先于环境变量和 `application.yml`，关闭代理并保存后会强制直连。
- 修改代理配置后无需重启服务，新的 TMDB 搜索和详情请求会使用最新配置。

## 7. 升级与备份

TODO:
- 数据库位置
- 升级前备份
- 镜像 tag 切换
- 回滚建议

## 8. 群晖 / NAS 部署注意事项

TODO:
- Docker volume 文件事件可能不稳定
- 推荐发现模式
- 权限和挂载路径
- 硬链接限制

## 9. 故障排查

TODO:
- 服务无法启动
- 前端无法访问后端
- Parser 不可用
- TMDB 请求失败
- 文件无法移动 / 复制 / 链接
