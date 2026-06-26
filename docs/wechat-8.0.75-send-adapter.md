# WeChat 8.0.75 Adapter Notes

本文档记录 Android 模块在微信 Android `8.0.75` 上的公开兼容性要点。微信内部类和方法会随版本变化，升级微信后需要重新验证。

## Message Hook

模块通过 LSPosed 作用域进入 `com.tencent.mm`，观察微信 WCDB `message` 表写入。

核心字段：

- `talker`
- `content`
- `isSend`
- `msgId`
- `createTime`
- `type`

模块会把这些字段规范化为 `/webhook/lsposed/message` 所需的消息事件。

## Send Path

在微信 Android `8.0.75` 上，文本发送优先使用较低层的发送 builder 路径：

```text
w11.s1.a(toUser) -> w11.r1.g/e/h(...) -> w11.r1.a().a()
```

如果主路径不可用，模块会尝试备用路径：

```text
tg3.t1.a() -> dk5.s5.fj(...)
```

两个路径都运行在微信进程内。模块只发送服务端出站队列中的文本，不解析业务命令。

## ClassLoader

微信可能通过 Tinker 加载补丁包。适配发送路径时必须使用运行时 `Application.getClassLoader()` 返回的 ClassLoader，而不是假设基础 `PathClassLoader` 一定包含所有类。

## ACK

当前发送路径可能无法立即返回微信本地消息 ID。模块会先 ACK `sent` 或 `failed`，本地消息 ID 精确关联可以后续通过自发消息数据库写入再匹配。

## Upgrade Checklist

升级微信版本后建议验证：

- 模块能正常注册当前 `wxid`。
- 收到文本消息可以上报。
- 发送文本可以 ACK 成功。
- 图片、语音、视频、文件消息至少能上报占位类型。
- 能读取媒体文件时可以上传 `media_base64`。
- 联系人和文件传输助手可以同步。

该文档只描述当前公开项目里的适配思路，不包含第三方 APK 反编译内容或私有设备验证记录。
