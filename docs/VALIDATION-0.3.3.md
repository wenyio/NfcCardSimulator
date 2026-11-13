# 0.3.3 验证记录

- 构建：testDebugUnitTest、lintDebug、exportDebugApk、assembleDebugAndroidTest 成功。
- JVM：27 项测试通过；Lint：0 错误、17 警告（现有依赖、资源国际化等提示）。
- API 36.1 QA 模拟器 NavigationTest：5 项通过，覆盖分类筛选、详情 Tab 不重置会话、开关同步与关闭、返回/后台停止、卡片范围日志清除、详情恢复与截图。
- 单独截图导航测试再次通过（1 项），未重新运行无关的设备端编辑/导入测试。
- 手工检查：首页 Tab 间距、状态栏/工具栏同色且浅色图标、无重复卡名、紧凑规则行；规则展开可查看完整收发内容。
- 将 QA 字体缩放由 1.0 调至 1.5，确认长说明出现“展开说明”，点击后显示“收起说明”，随后恢复 1.0。
- 模拟器启动出现过 System UI 无响应弹窗；关闭后完成检查。模拟器无 NFC，以上结果不代表真实设备射频或 AID 路由测试。

产物：artifacts/NfcCardSimulator-v0.3.3-debug.apk

大小：5,846,567 字节。

SHA-256：67d9637376985a7d0a84cd66e7655facebea485a72c8b402775a95db5ef5e3d5。
