# Adapter Quickstart v1

本文档面向外部机器人框架、业务系统和插件作者。目标是让别人能够稳定适配 `wechat-observatory`，而不是让本项目额外内置很多上层网关。

## 接入边界

- 手机模块负责微信内的真实收发能力。
- Go 网关负责鉴权、队列、媒体落盘、消息协议化和公开 API。
- 外部适配器只依赖 `/api/v1` 和 `/api/media/...`，不要依赖手机模块私有接口。
- API Key 绑定设备；设备完成模块注册并上报当前 `owner_wxid` 后，适配器发送消息时才可以省略 `device` 和 `owner_wxid`。
- Web 管理台仍然保留，但它不是外部系统的集成协议。

## 认证

外部适配器优先使用 API Key：

```http
X-Bridge-API-Key: <your-api-key>
```

WebSocket 或部分客户端不方便传 header 时，可以使用查询参数：

```text
/api/v1/ws?api_key=<your-api-key>
```

管理后台密码只用于管理台和本地排障，不建议外部适配器长期使用。

## 推荐启动流程

1. 调用 `GET /api/v1/capabilities`，确认当前协议版本、消息类型能力和安全限制。
2. 调用 `GET /api/v1/modules/status`，确认当前 API Key 能看到 `runtime_status=ready` 的模块。
3. 调用 `GET /api/v1/messages?after_id=0&limit=100` 做首次补拉。
4. 保存响应里的 `next_cursor`。`cursor_field` 当前固定为 `id`。
5. 建立 `GET /api/v1/ws` 实时连接。
6. WebSocket 断线后，用最后处理成功的消息 ID 调用 `GET /api/v1/messages?after_id=<last_seen_id>` 补拉，再重连。

适配器应在业务处理成功后再推进本地 cursor，避免收到事件后业务失败导致消息丢失。
如果 `modules/status` 返回的 `ready` 模块数为 0，通常是 API Key 绑定的设备和正在运行的手机模块不一致，或者手机模块尚未完成注册/轮询。此时先修正 API Key 与设备绑定，再继续同步消息或发送。

## 示例代码

仓库内提供一个最小 Python 示例：

```bash
export WECHAT_OBSERVATORY_BASE_URL="http://127.0.0.1:8088"
export WECHAT_OBSERVATORY_API_KEY="<your-api-key>"

python3 examples/python/adapter_quickstart.py capabilities
python3 examples/python/adapter_quickstart.py status
python3 examples/python/adapter_quickstart.py sync --after-id 0 --limit 20
python3 examples/python/adapter_quickstart.py send-text --target "<target-wxid-or-room-id>" --text "hello" --wait
```

示例默认只输出 `id`、`kind`、`direction`、`chat_kind`、文本长度、媒体数量、appmsg 类型等结构摘要，不打印完整 `wxid`、群 ID、聊天原文、API Key、原始 XML 或媒体 base64。


## 协议验收套件

接入方写适配器前，先用仓库内脚本跑一遍公开协议验收。默认模式只读，不发送微信消息：

```bash
export WECHAT_OBSERVATORY_BASE_URL="http://127.0.0.1:8088"
export WECHAT_OBSERVATORY_API_KEY="<your-api-key>"

python3 scripts/public_api_contract_check.py
```

默认检查内容：

- `GET /api/v1/capabilities`：协议版本、发送端点、WebSocket 能力。
- `GET /api/v1/messages`：统一信封、cursor 字段、`cursor_conflict` 错误。
- `GET /api/v1/modules/status`：模块状态结构。
- `GET /api/v1/ws`：`hello`、`replay`、`ping/pong`。
- `GET /api/media/...`：样本媒体读取和跨设备媒体路径拒绝探针。
- `GET /docs/openapi.json`：公开路径和错误码是否完整。

接入上线前建议额外要求 ready 模块和近期安全样例覆盖：

```bash
python3 scripts/public_api_contract_check.py \
  --require-ready-module \
  --require-fixture all-safe-live \
  --message-limit 100 \
  --message-pages 3
```

只校验目标 ID、不发送消息：

```bash
python3 scripts/public_api_contract_check.py \
  --target-wxid "<target-wxid-or-room-id>" \
  --target-kind room \
  --target-name-exact "<精确群名或联系人名>" \
  --require-target-contact
```

如果还不知道目标 ID，可以先用联系人搜索做人工确认；真实发送必须回填稳定的 `--target-wxid`，并带上精确显示名校验：

```bash
python3 scripts/public_api_contract_check.py   --target-query "test"   --target-name "test"   --target-kind room
```

不要用宽泛关键词自动选第一个群。`--target-name-contains` 只能作为 dry-run 或排查时的辅助保护，不能防止多个群名都包含同一个词；真实发送必须使用 `--target-wxid` + `--target-name-exact` + `--require-target-contact`，并且脚本必须能从联系人表反查到这个目标。

生成发送 payload 摘要、不入队、不发送：

```bash
python3 scripts/public_api_contract_check.py \
  --target-wxid "<target-wxid-or-room-id>" \
  --target-kind room \
  --target-name-exact "<精确群名或联系人名>" \
  --require-target-contact \
  --dry-run-send \
  --send-profile safe-basic
```

`safe-basic` 是安全基础发送 profile，等价于 `text,image,file,link,location`。这些类型不需要预先提供 source message；脚本会给图片生成 320x180 的可见 PNG，给文件生成很小的内置文本样本。`video`、`voice`、`emoji`、`quote`、`mini-program`、`chat-history` 需要真实媒体或 source record 时，再单独传对应参数。

真实发送必须显式加 `--confirm-send`。建议先只测文本，再逐步增加类型：

```bash
python3 scripts/public_api_contract_check.py \
  --target-wxid "<target-wxid-or-room-id>" \
  --target-kind room \
  --target-name-exact "<精确群名或联系人名>" \
  --require-target-contact \
  --confirm-send \
  --send-kinds text
```

多类型发送验收：

```bash
python3 scripts/public_api_contract_check.py \
  --target-wxid "<target-wxid-or-room-id>" \
  --target-kind room \
  --target-name-exact "<精确群名或联系人名>" \
  --require-target-contact \
  --confirm-send \
  --send-profile safe-basic \
  --require-send-success
```

`image` 默认使用脚本生成的 320x180 可见 PNG，`file` 默认使用一段小文本；`video` 需要传 `--video-file` 或 `--video-media-url`。
脚本不会输出 API Key、密码、token、cookie、raw XML 或媒体 base64。

## 能力发现

```bash
curl -H "X-Bridge-API-Key: <your-api-key>"   "http://127.0.0.1:8088/api/v1/capabilities"
```

关键字段：

| 字段 | 用途 |
| --- | --- |
| `protocol_version` | 当前公开协议版本，现阶段是 `v1`。 |
| `envelope.fields` | `MessageEnvelope v1` 的稳定字段说明，使用真实公开信封字段名；嵌套字段用 `media[].url`、`appmsg.title`、`location.latitude` 这类路径表达。 |
| `capabilities[]` | 每种消息类型的入站、出站状态和必要字段。 |
| `transports[]` | HTTP 查询、WebSocket、媒体 URL 等通道能力。 |
| `limits` | 媒体、base64、日志脱敏等安全边界。 |

## 拉取消息

```bash
curl -H "X-Bridge-API-Key: <your-api-key>"   "http://127.0.0.1:8088/api/v1/messages?after_id=0&limit=100"
```

示例结构：

```json
{
  "ok": true,
  "protocol_version": "v1",
  "messages": [
    {
      "id": "123",
      "event_id": 123,
      "chat_record_id": 456,
      "device": "phone-a",
      "direction": "recv",
      "kind": "text",
      "message_type": 1,
      "chat_id": "<wxid-or-room-id>",
      "chat_kind": "direct",
      "from_wxid": "<sender-wxid>",
      "to_wxid": "<owner-wxid>",
      "text": "hello",
      "create_time": 1710000000,
      "chat_display_name": "只用于展示"
    }
  ],
  "next_cursor": 123,
  "next_cursor_param": "after_id",
  "cursor_field": "id",
  "has_more": false
}
```

稳定规则：

- `id` / `event_id` 用于去重和 cursor。
- `chat_id` 是会话稳定 ID，私聊通常是好友 wxid，群聊通常是 `xxx@chatroom`。
- `chat_display_name` 只用于页面展示，不能作为发送目标。
- `media[]` 只暴露媒体 URL、MIME、文件名、大小和 `opaque` 标记，不输出 `media_base64`。
- `media[].opaque=true` 表示这是微信本地 opaque 原始附件，不应假设浏览器能直接预览；表情应优先使用 `appmsg.title`（MD5）、`appmsg.url`（CDN URL）和 `appmsg.file_name`。
- `appmsg`、`location` 是结构化字段；不要回退解析 `raw_xml`。
- `unsupported[]` 表示当前协议还不能稳定表达的字段，适配器必须容忍。

## 实时消息

推荐外部机器人框架使用 WebSocket：

```text
ws://127.0.0.1:8088/api/v1/ws?api_key=<your-api-key>&replay=0
```

服务端会先推送 `hello`：

```json
{
  "ok": true,
  "type": "hello",
  "protocol_version": "v1"
}
```

收到微信消息时推送：

```json
{
  "ok": true,
  "type": "message",
  "protocol_version": "v1",
  "event": {
    "id": "124",
    "kind": "image",
    "chat_id": "<wxid-or-room-id>",
    "chat_kind": "room",
    "media": [
      {
        "kind": "image",
        "mime": "image/jpeg",
        "name": "image.jpg",
        "url": "/api/media/<redacted>.jpg",
        "size": 1024
      }
    ]
  }
}
```

WebSocket 是实时通道，不是唯一事实源。断线、重启或业务处理失败后，以 `/api/v1/messages?after_id=...` 补拉为准。

## 发送文本

```bash
curl -X POST   -H "X-Bridge-API-Key: <your-api-key>"   -H "Content-Type: application/json"   -d '{"wx_ids":["<target-wxid-or-room-id>"],"text":"hello"}'   "http://127.0.0.1:8088/api/v1/messages/text"
```

响应示例：

```json
{
  "ok": true,
  "protocol_version": "v1",
  "kind": "text",
  "outbox_id": 1001,
  "outbox_ids": [1001],
  "status_url": "/api/v1/outbox/1001",
  "outbox": {
    "id": 1001,
    "device": "phone-a",
    "target_wxid": "<target-wxid-or-room-id>",
    "kind": "text",
    "status": "pending",
    "status_url": "/api/v1/outbox/1001"
  },
  "outboxes": [
    {
      "id": 1001,
      "device": "phone-a",
      "target_wxid": "<target-wxid-or-room-id>",
      "kind": "text",
      "status": "pending",
      "status_url": "/api/v1/outbox/1001"
    }
  ]
}
```

发送接口返回只代表任务已入队。多目标发送时看 `outbox_ids` / `outboxes` 跟踪每个目标；`outbox_id` / `outbox` 保留为兼容字段，指向第一个目标。真实结果以手机模块 ACK 后的 outbox 状态为准；真实微信本地消息 ID 会出现在终态 outbox 的 `chat_record_id` 字段里。

## 查询发送状态

```bash
curl -H "X-Bridge-API-Key: <your-api-key>"   "http://127.0.0.1:8088/api/v1/outbox/1001"
```

终态通常是：

| 状态 | 含义 |
| --- | --- |
| `sent` | 手机模块确认执行成功。 |
| `failed` | 手机模块确认失败，查看 `last_error`。 |

中间态可能包括 `queued`、`pending`、`leased`。外部适配器应设置自己的超时，不要无限等待。

## 发送媒体和卡片

普通接入优先使用具体类型接口：

| 类型 | 接口 | 最少字段 |
| --- | --- | --- |
| 文本 | `POST /api/v1/messages/text` | `wx_ids` + `text` |
| 图片 | `POST /api/v1/messages/image` | `wx_ids` + `media_url` 或 `media_base64` |
| 视频 | `POST /api/v1/messages/video` | `wx_ids` + `media_url` 或 `media_base64` |
| 语音 | `POST /api/v1/messages/voice` | `wx_ids` + `media_url` 或 `media_base64` |
| 文件 | `POST /api/v1/messages/file` | `wx_ids` + `media_url` 或 `media_base64`，建议带 `media_name` |
| 表情 | `POST /api/v1/messages/emoji` | `wx_ids` + `source_chat_record_id` 或 `emoji_md5` |
| 位置 | `POST /api/v1/messages/location` | `wx_ids` + `location_latitude` + `location_longitude` |
| 引用 | `POST /api/v1/messages/quote` | `wx_ids` + `text` + `quote_msg_id` |
| 链接 | `POST /api/v1/messages/link` | `wx_ids` + `source_chat_record_id` 或 `appmsg_title` + `appmsg_url` |
| 小程序 | `POST /api/v1/messages/mini-program` | `wx_ids` + `source_chat_record_id` 或 `mini_program_username` + `mini_program_page_path` |
| 聊天记录 | `POST /api/v1/messages/chat-history` | `wx_ids` + `recorditem_xml` 或 `source_chat_record_ids` |

复杂场景再使用 `POST /api/v1/messages/action`。它暴露完整 action 字段，适合调试协议或做高级适配。

## 目标 ID 规则

发送目标只能传稳定 ID：

- 私聊：好友 `wxid`。
- 群聊：群 `room_id`，通常形如 `xxx@chatroom`。

昵称、备注、群名会变化，只能用于展示、搜索和人工确认，不能作为发送目标。

## 安全规则

适配器和日志里不要输出：

- 真实 `wxid`、群 ID、API Key、密码、cookie、token。
- 聊天原文全文、媒体 base64、原始 XML。
- 支付、红包、转账的敏感详情。

公开协议不会返回 `api_key`、`raw_xml`、`media_base64`、`raw_provider` 等内部字段。确实需要排障时，应在受控环境临时查看服务端日志。

## 最小处理循环

```text
cursor = load_cursor(default=0)

for message in GET /api/v1/messages?after_id=cursor:
    if already_processed(message.id):
        continue
    handle(message)
    mark_processed(message.id)
    cursor = max(cursor, int(message.id))
    save_cursor(cursor)

connect /api/v1/ws
on message:
    handle exactly like pulled messages
on disconnect:
    repeat pull with after_id=cursor, then reconnect
```

这套方式保持简单：HTTP 做可靠补拉，WebSocket 做实时体验，外部系统不需要理解手机 Hook 细节。
