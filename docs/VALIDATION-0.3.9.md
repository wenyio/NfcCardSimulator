# 0.3.9 验证记录

日期：2026-11-13。基于本地 b0b84cb（包含后续 Tab 排序和空分类操作）。

## 构建与核心测试
- JDK 17，Gradle 8.13。执行 :app:testDebugUnitTest :app:lintDebug :app:exportDebugApk :app:assembleDebugAndroidTest，BUILD SUCCESSFUL。
- JVM：37 项通过，0 失败、0 错误。新增覆盖旧 APDU 元数据转换及往返、原字节 SHA-256、重复导入保留来源、非法扩展字段、金额/随机数诊断、掩码命中提示、CDOL1/2、重置/重新 SELECT、冲突及损坏定义、Lc 越界、内容模式歧义。
- Lint：0 errors，19 warnings（依赖、国际化等现有类别）。
- 最终构建日志：work/build-0.3.9-verified.log；测试报告：app/build/reports/tests/testDebugUnitTest/index.html。

## 设备测试
- AVD NfcCardSimulator_QA，API 36.1；无真实手机和 P19 射频配对。
- ScenarioMetadataTest 1 项和 NavigationTest 5 项联合执行，OK (6 tests)，71.354 秒。
- 验证场景摘要、条件/来源默认折叠和展开、复制编辑后保留元数据、调试字段差异入日志、Activity 重建保留信息，以及列表过滤、详情退出/后台停止、切 Tab 会话保留、开关实际状态和本卡日志清除。
- 原导航测试按旧固定索引选择 Visa；已改为按 Tab 名称定位，适配此前已提交的排序调整。
- 首轮新增设备测试对 TextView.text 使用直接相等比较，未找到 Spannable 文本；修正为 toString 后通过。
- 模拟器启动出现 System UI 无响应弹窗；关闭系统弹窗后重跑，联合测试通过并重新截图，不将系统弹窗记作 App 崩溃。
- 截图：work/scenario-details-039.png、work/scenario-debug-039.png。
- 最终设备日志：work/device-0.3.9-verified.log。

## 产物
- artifacts/NfcCardSimulator-v0.3.9-debug.apk
- versionCode=12，versionName=0.3.9。minSdk 26 / targetSdk 31 / compileSdk 36。
- 大小：5,910,386 字节。
- SHA-256：a1e2a2e21336e6a9fd93023ffd2c6de9841606aae8d0a0bc158cf98a8aacf5f1。

## 边界
- 首轮自编 PDOL 回复 TLV 长度写错，严格解析拒绝；修正有效样本并保留真正截断样本后，37 项全部通过。
- 运行条件和预期结果是说明，未宣称实测认证结果；来源指纹不构成来源真实性证明。
- 未内置第三方卡片资产、未修改激活逻辑、未实现动态签名或密文。
- 未通过真实射频和 EMV 内核交易验证；BCTC 卡片迁移仍需逐卡核对命令匹配、DOL 条件和终端结果。
