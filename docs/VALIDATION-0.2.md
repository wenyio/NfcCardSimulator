# 0.2.0 本地验证记录

日期：2026-11-13。以下是本轮版本的证据，0.1.0 历史验证见 VALIDATION.md。

- JDK 17 / Gradle：testDebugUnitTest、lintDebug、exportDebugApk、assembleDebugAndroidTest 成功。
- 23 项 JVM 测试通过（含 1 项项目模板测试）：脚本/JSON 兼容，T:/C: 配对、续行、分隔、错误阻止、重复命令、交付示例，TLV 边界及嵌套限制，按步骤差异和失败不推进。
- API 36.1 QA 模拟器：6 项设备测试通过（含 1 项模板测试）。覆盖真实 View 的卡片复制、必填校验、重排、保存重载；错误日志禁用导入、正确日志生成顺序卡；日志 600 条边界、重载、清空；原有手动 APDU/日志及卡片持久化回归。
- Lint：0 错误、18 警告，主要为依赖新版本提示、模板资源未使用和 KTX/写法建议。保留 targetSdk 31 的既有单项 ExpiredTargetSdkVersion 排除。
- APK：artifacts/NfcCardSimulator-v0.2.0-debug.apk，5,846,424 字节，调试签名。
- SHA-256：39DE1979C4709A9419AD45BCB4F9B63CC01C5AC207A289313C0770D4CA542213。

设备测试首次暴露对话框显示回调异步初始化问题；已改为 show 后同步绑定按钮并重新通过全部 6 项。模拟器启动及界面检查期间另有 system / System UI / Pixel Launcher 无响应弹窗，属于当前 QA 环境可见问题，未把这一环境当作流畅度验证证据。自动测试通过不证明真实射频能力或终端认证结果。

未接入真实 Android HCE 手机，未改动 P19 设备。默认支付路由、实际 AID 注册、刷卡时延、断场、锁屏和完整交易结果仍待手机 + P19 联调。SAF 的不同文件提供器及不同厂商 NFC 行为仍需设备覆盖。
