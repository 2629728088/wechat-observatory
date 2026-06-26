# Development Status

`wechat-observatory` 当前已经整理为独立的微信网关项目。

## 已完成

- 服务端使用 Go 实现，入口为 `cmd/bridge`。
- 数据库工具入口为 `cmd/bridge-db`。
- Web 管理台使用 React 和 shadcn 风格组件，构建产物嵌入 Go 服务。
- Android 模块包名为 `cc.wechat.observatory`。
- Android 模块公开适配目标为微信 Android `8.0.75`。
- 模块认证只使用 API Key。
- 模块在微信进程内自动识别当前 `wxid`，用户不需要手动填写。
- 设备显示名由 Web 管理端设置。
- 支持 API Key 生成、启用、停用、删除。
- 删除 API Key 会注销对应模块身份。
- 支持消息入库、实时推送、联系人同步、媒体附件保存、Web 手动发送文本。
- 旧游戏业务、命令解析、自动回复和第三方协议登录不属于当前项目。

## 当前数据库表

- `bridge_api_keys`
- `bridge_devices`
- `bridge_message_events`
- `bridge_module_outbox`
- `bridge_module_runtime`
- `bridge_module_contacts`

## 推荐验证

```powershell
go test ./...
cd web/admin
npm run build
cd ../..
cd android-module
.\gradlew.bat :app:assembleDebug
cd ..
```

如果当前仓库没有 Gradle Wrapper，则 Android 构建需要通过 Android Studio 执行，或先补充 Wrapper。

## 后续可做

- 增加更多微信版本兼容性记录。
- 增强媒体附件上传和外部对象存储/CDN 支持。
- 增加更细粒度的管理台权限模型。
- 增加数据库备份和数据保留策略文档。
