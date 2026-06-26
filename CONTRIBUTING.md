# Contributing

欢迎提交 Issue 和 Pull Request。

## 项目边界

`wechat-observatory` 是纯微信网关：

- 可以做：消息观测、联系人同步、媒体展示、Web 手动发文本、API Key 和设备管理。
- 不做：游戏业务、命令解析、自动回复、第三方协议登录、扫码登录替代方案。

## 开发环境

```powershell
go test ./...
cd web/admin
npm install
npm run build
cd ../..
cd android-module
.\gradlew.bat :app:assembleDebug
```

如果当前检出没有 `android-module/gradlew.bat`，请使用 Android Studio 构建 Android 模块，或先提交 Gradle Wrapper。

## 提交前

- 保持代码和文档中的示例值为占位值。
- 不提交真实 `.env`、真实 API Key、真实聊天数据或构建产物。
- 修改模块协议时，同步更新 `docs/api.md` 和 `docs/module-contract.md`。
- 修改 Android 配置项时，同步更新 `docs/android-module.md`。
- 修改部署变量时，同步更新 `.env.example` 和 `docs/deployment.md`。

## Pull Request

PR 描述建议包含：

- 改动目的。
- 影响的端：Backend、Web、Android、Docs。
- 验证命令和结果。
- 是否涉及协议或数据库迁移。
