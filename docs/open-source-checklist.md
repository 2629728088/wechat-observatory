# Open Source Checklist

公开发布前按这个清单过一遍。

## 必做

- [x] 已添加 MIT `LICENSE`。
- [ ] 修改所有真实密码、API Key、IP、域名、手机号、wxid。
- [ ] 确认 `.env`、真实 k3s secret、真实 Docker env 没有提交。
- [ ] 确认 README 可以让新用户跑起服务端、Web 和 Android 模块。
- [ ] 确认 README 标注当前适配微信 Android `8.0.75`。
- [ ] 建议补充 Gradle Wrapper，方便用户命令行构建 Android 模块。
- [ ] 确认文档说明 Root、LSPosed、微信 Hook 和账号风险。
- [ ] 确认仓库不包含真实聊天截图、真实日志、真实数据库导出。

## 建议命令

```powershell
go test ./...
cd web/admin
npm run build
cd ../..
cd android-module
.\gradlew.bat :app:assembleDebug
cd ..
```

如果仓库没有 `android-module/gradlew.bat`，请改用 Android Studio 构建，或先添加 Gradle Wrapper 后再运行命令行验证。

敏感信息扫描：

```powershell
rg -n "banishment|root:root|wxid_|phone number|password|secret|token|api_key" .
```

旧业务命名扫描时，把命令里的占位词替换成你的历史项目名：

```powershell
rg -n "old-project-name|old-business-name|old-role-name" .
```

命中项需要人工确认。示例变量名或测试占位值可以保留，真实值必须删除。

## 建议仓库说明

GitHub 仓库描述可以使用：

```text
WeChat Observatory is a self-hosted LSPosed-based WeChat gateway for observing messages, syncing contacts, and manually sending text from a Web console.
```

中文描述：

```text
微信看台：基于 LSPosed 的自托管微信网关，用于消息观测、通讯录同步和 Web 手动发送文本。
```

## 发布后

- [ ] 创建第一个 release。
- [ ] 明确标注已测试的微信版本和 Android/LSPosed 环境。
- [ ] 如果发布 APK，说明它只是示例构建，用户最好自行构建。
- [ ] 开启安全漏洞报告渠道。
