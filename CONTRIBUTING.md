# 贡献指南

欢迎通过 GitHub Issue 报告问题，或通过 Pull Request 提交修改。报告问题时请说明平台、系统与应用版本、复现步骤、预期和实际结果；涉及 HCE 时补充终端型号及经过脱敏的 APDU 样本。

## 开发与验证

Android 环境和构建入口见 [README](README.md#构建)，鸿蒙见 [harmony/README](harmony/README.md)。两端共用卡包格式和匹配语义，修改这些行为时应同步检查另一平台及 examples 中的自编样本。源码入口见 [阅读指南](docs/CODE-READING-GUIDE.md)，写作要求见 [AGENTS.md](AGENTS.md)。

Android 修改提交前，使用 JDK 17 执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:exportDebugApk
```

单元测试与模拟器无法替代真实手机的 NFC 路由、射频时序和终端交互验证。Pull Request 应说明改动原因、完成的验证和仍需设备验证的部分。

## 提交内容

只提交有权按本项目 Apache-2.0 许可证分发的代码和素材，保留原有版权声明。示例使用自编或已获授权的数据；不要提交真实卡号、个人信息、第三方测试卡包、原始交易日志、签名私钥或密码。分享复现样本前应先脱敏。

本机 SDK 路径、签名配置、构建缓存和产物由 .gitignore 排除。Release 签名由开发者自行创建并保存在本机，流程见 [签名说明](docs/RELEASE-SIGNING.md)。
