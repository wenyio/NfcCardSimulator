# 0.3.1 验证记录

2026-11-13：界面提示去重、顶部添加菜单和日志分范围清除。

- testDebugUnitTest、lintDebug、exportDebugApk、assembleDebugAndroidTest 成功；27 项单元测试通过，Lint 0 错误、23 警告。
- QA 模拟器全部 11 项设备测试通过，新增本卡日志清除按钮测试和清除持久化测试：其他卡及旧记录保留、清除后的新日志可继续记录、全清后重载为空。原有导航、自动会话、编辑与导入回归通过。
- 首页状态使用 GONE，不保留占位；首页按钮行已收进顶部加号菜单。详情失败原因仍在顶部显示一次。
- APK：artifacts/NfcCardSimulator-v0.3.1-debug.apk，6,844,395 字节。
- SHA-256：06D60EFDD844F32398477E2C2F33DE0C9745852F0ADFD43765D06CF43B13C6DA。

本轮未连接真实 HCE 手机或 P19，射频验证边界同 0.3.0。
