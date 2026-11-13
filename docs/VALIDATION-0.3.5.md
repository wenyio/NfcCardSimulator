# 0.3.5 验证记录

- testDebugUnitTest、lintDebug、exportDebugApk、assembleDebugAndroidTest 构建成功。
- JVM 27 项测试通过；API 36.1 QA 模拟器 NavigationTest 5 项通过，覆盖分类筛选、切页会话不重置、开关实际状态、返回和后台停止、日志范围清除和页面恢复。
- Lint 0 错误、19 警告（依赖版本、资源国际化等提示）。
- adb 实际触摸检查：首页内容区、卡片表面左右滑动切换分类；详情从详情滑到调试、再快速滑到日志；Tab 选中随页面更新，卡片表面横滑未误打开详情。
- 检查固定控制卡的状态空心圆、圈叹号、开关布局；在日志页点击问题图标，弹窗显示完整的“当前设备没有 NFC”。真实 AID 拒绝原因沿用同一错误通道，当前无 NFC 模拟器不能制造真实路由注册失败。
- 启动模拟器出现 System UI 无响应，关闭弹窗后完成检查。真实手机的射频、支付路由仍需设备验证。

产物：artifacts/NfcCardSimulator-v0.3.5-debug.apk
大小：5,868,322 字节。
SHA-256：1a551e042b2396d0b95ca3a26b85b6c3154e1394c27f2b3bd5202eb4adb31ed0。
