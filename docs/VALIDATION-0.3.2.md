# 0.3.2 验证记录

2026-11-13：分类 Tab、设置菜单、详情开关和工具栏颜色调整。

- testDebugUnitTest、lintDebug、exportDebugApk、assembleDebugAndroidTest 成功；27 项单元测试通过；Lint 0 错误、23 警告。
- QA 模拟器全部 12 项设备测试通过。新增横向分类 Tab/筛选及开关同步、关闭开关停止模拟测试；既有导航、日志清除、编辑和导入回归通过。
- 实际打开设置菜单并读取界面，确认仅“NFC 设置 / 默认支付设置”两项；截图确认深蓝工具栏、白色图标和原有浅色页面。QA 启动期间仍有 System UI 无响应弹窗，关闭后重新检查菜单截图。
- APK：artifacts/NfcCardSimulator-v0.3.2-debug.apk，5,869,025 字节。
- SHA-256：ECD46ECEB48BCE2B51A28BC8E880DD7076B2C47923CFA503B7CF014151F71E35。

没有真实 NFC 手机连接，实际 AID 注册和系统设置返回后的射频恢复仍需真机验证。
