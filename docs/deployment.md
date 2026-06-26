# Deployment

本文档说明如何部署 `wechat-observatory`。公开部署时请使用 HTTPS、反向代理和强密码。

## 环境变量

| 变量 | 必填 | 说明 |
| --- | --- | --- |
| `BRIDGE_HTTP_ADDR` | 否 | HTTP 监听地址，默认 `:8088` |
| `BRIDGE_ADMIN_PASSWORD` | 是 | Web 管理台和管理 API 密码 |
| `BRIDGE_DEFAULT_DEVICE` | 否 | 默认设备名 |
| `BRIDGE_DEVICES` | 无 MySQL 时必填 | 初始设备，格式 `device|wxid|display|timeout`，`wxid` 可留空 |
| `BRIDGE_API_KEYS` | 无 MySQL 时建议填 | 初始 API Key，格式 `key|device|nickname` |
| `BRIDGE_MYSQL_DSN` | 生产建议填 | MySQL DSN |
| `BRIDGE_MYSQL_AUTO_MIGRATE` | 否 | 是否启动时自动迁移，生产建议 `false` |
| `BRIDGE_MEDIA_DIR` | 否 | 图片、语音、文件等附件保存目录 |

## Docker Compose

复制并编辑配置：

```bash
cp deploy/docker/.env.example deploy/docker/.env
```

至少修改：

```text
BRIDGE_ADMIN_PASSWORD=your-strong-admin-password
BRIDGE_MYSQL_DSN=wechat:change-me@tcp(127.0.0.1:3306)/app_wechat_observatory?charset=utf8mb4&parseTime=True&loc=Local
```

构建镜像：

```bash
docker compose -f deploy/docker/docker-compose.yml build
```

执行数据库工具：

```bash
docker compose -f deploy/docker/docker-compose.yml --profile tools run --rm gateway-db
```

启动服务：

```bash
docker compose -f deploy/docker/docker-compose.yml up -d gateway
```

检查状态：

```bash
curl -fsS http://127.0.0.1:8088/healthz
```

管理台地址：

```text
http://<server-host>:8088/admin/
```

## 更新版本

```bash
docker compose -f deploy/docker/docker-compose.yml build
docker compose -f deploy/docker/docker-compose.yml --profile tools run --rm gateway-db
docker compose -f deploy/docker/docker-compose.yml up -d gateway
```

## k3s 参考

`deploy/k3s` 提供基础示例：

- `namespace.yaml`
- `config.example.yaml`
- `secrets.example.yaml`
- `deployment.yaml`
- `service.yaml`

使用前复制示例文件并改成真实配置：

```bash
cp deploy/k3s/config.example.yaml deploy/k3s/config.yaml
cp deploy/k3s/secrets.example.yaml deploy/k3s/secrets.yaml
```

不要提交真实的 `config.yaml` 和 `secrets.yaml`。

## 媒体文件

模块上传 `media_base64` 后，服务端会把文件写到 `BRIDGE_MEDIA_DIR`，并在消息里保存 `media_url`。

生产环境建议：

- 给 `BRIDGE_MEDIA_DIR` 配持久化卷。
- 通过备份策略保护媒体目录。
- 如果要接 CDN，对外只暴露受控下载地址，避免直接公开原始目录。

## 网络建议

- 不要把管理台裸露到公网。
- 至少放在 VPN、内网、堡垒机或反向代理鉴权之后。
- API Key 泄露后应立即停用或删除。
- 删除 API Key 会注销对应模块身份，手机端需要填写新的 API Key 后重新注册。
