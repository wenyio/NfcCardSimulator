# NFC 卡模拟器

NFC HCE 脚本工具，Android 与鸿蒙源码在同一仓库管理，分别构建。Android 正式包名 `com.iscolt.cardsimulator`，调试包名 `com.iscolt.cardsimulator.debug`；鸿蒙包名 `com.iscolt.cardsimulator.harmony`。

Android Studio 打开仓库根目录；DevEco Studio 打开 [harmony](harmony/)。鸿蒙构建与签名见 [鸿蒙工程说明](harmony/README.md)，目录与 Git 管理见 [跨平台管理约定](docs/CROSS-PLATFORM-GIT.md)。下文的系统版本、构建命令与手机设置流程针对 Android，鸿蒙的平台行为以其工程说明与验证记录为准。

源码入门：[阅读指南](docs/CODE-READING-GUIDE.md) · [术语表](docs/NFC-GLOSSARY.md)。

1.0.0：minSdk 26 / targetSdk 31 / compileSdk 36，Kotlin + 原生 Android，JDK 17，Gradle 8.13。

## 已实现

- 卡片列表、筛选、新建、复制、编辑、删除；独立卡片 / APDU 编辑页、草稿恢复，卡组织下拉选择与本地新增。
- 顶部卡包名称直达独立管理页（新建、分组、数量、使用状态、行尾操作）、JSON 整包导入/更新/导出；新建/复制默认归入当前用户包，编辑可移动所属卡包；卡片 ID 按包隔离，原子保存。
- HCE 支付服务，启动时注册当前 AID 和 PPSE；切卡自动停止模拟。
- EXACT 完整匹配、MASK 掩码匹配、PREFIX 前缀匹配；按内容或按步骤执行。
- 独立手动 APDU 会话及收发日志。
- 可选测试场景、运行条件、导入来源与 SHA-256 追溯。
- 按当前会话的 PDOL/CDOL 定义解释 GPO/GAC 字段差异，掩码命中仍提示固定回复限制。
- T:/C: 交易文本导入、预览、AID 提取与错误定位。
- 按步骤未匹配的字节差异、回复 BER-TLV 查看。
- 最近 600 条本地持久化日志，按手动/NFC 会话分组，可导出文本。
- NFC/HCE 能力检查、默认支付设置入口。
- 首页与详情左右滑页、自动模拟开关及集中问题提示。

内置 6 张自编连通性演示卡，仅验证 PPSE、AID 选择和 GPO 错误路径。应用不再附带第三方测试卡配置，交易脚本由用户自行新建或导入。固定回复不会重算动态 EMV 密文或签名。

导航和自动模拟规则见 [docs/LOCAL-0.3.md](docs/LOCAL-0.3.md)。导入格式见 [docs/LOCAL-0.2.md](docs/LOCAL-0.2.md)。自编 Discover 错误路径模板位于 examples/discover-error-scenarios.json；T:/C: 示例见 examples/discover-session.txt。正常交易场景需要有效测试日志；ICC/Galitt 原始日志适配和动态 EMV 密文/签名计算尚未实现。项目仅做本地模拟。

编辑页与卡组织行为见 [docs/CARD-EDITOR.md](docs/CARD-EDITOR.md)。

后续任务见 [docs/ROADMAP.md](docs/ROADMAP.md)。

## 构建

使用 JDK 17，运行 `gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:exportDebugApk`。

生成 artifacts/NfcCardSimulator-v1.0.0-debug.apk，使用标准 Android 调试签名。Release 使用独立本地签名，配置与备份说明见 [docs/RELEASE-SIGNING.md](docs/RELEASE-SIGNING.md)。

## 手机使用

1. 安装到支持 NFC HCE 的 Android 8.0 及以上手机。
2. 开启 NFC，通过“支付设置”将本应用设为默认支付应用。
3. 点击卡片进入详情，设备条件满足时自动开始模拟；详情可手动暂停，也可先调试脚本。
4. 保持屏幕解锁，将手机 NFC 区域贴近终端。
5. 返回列表自动停止；切到后台或进入设备设置也会暂停。停止后收到命令会回复 6985。系统默认支付选择不会自动恢复，结束后可在设置中切回其他支付应用。

仅连接 P19 终端不能完成手机端 HCE 验证，仍需模拟卡手机与终端配对。模拟器可验证界面和规则，不能替代真实射频联调。

## 脚本

卡包使用 schemaVersion=2，顶层 pack（id、name、description）与 cards 数组。完整示例见 [examples/card-pack.json](examples/card-pack.json)，流程与限制见 [docs/CARD-PACKS.md](docs/CARD-PACKS.md)。标准单卡对象、卡片数组和 schemaVersion=1 的独立卡片文件可导入为新包，这是当前单卡导出的配套格式。

卡片字段：id、name、scheme、aids（5–16 字节 AID 列表）、description、ordered、fallback、rules。
规则字段：command、response、match、label；MASK 模式另需 mask。

- response 包含完整回复数据及 SW1/SW2，不自动追加 9000。格式错误返回 6700，未知命令返回 fallback，默认 6D00。
- ordered=false 按规则列表寻找第一条匹配项。ordered=true 仅比较当前步骤，成功才前进，未匹配不前进；结束后返回 fallback。射频断开或取消选择后重置。
- EXACT 要求完整命令相等。PREFIX 显式允许后续字节不同，需谨慎使用。
- MASK 要求 command 和 mask 等长，逐字节比较“实际值 & mask == 期望值 & mask”。FF 全比较，00 忽略。实际命令也须等长。
- 掩码和前缀不会重新计算回复中的密文或动态签名。
- 不解析 cardID/cardImg/cardType/AID/APDU 等旧字段，不迁移旧 cards.json。独立 T:/C: 交易文本导入功能继续保留。
- 单文件最大 2 MB、200 张卡，每卡最多 1000 条规则。卡片 ID 仅要求包内唯一，更新卡包须确认整包替换。最多 100 个用户包、2000 张用户卡，累计持久化数据最大 20 MB。

## 验证边界

单元测试覆盖顺序、失败不推进、重置、掩码、完整匹配、格式错误、JSON 往返、无效配置、旧格式拒绝及演示卡。真实手机的默认支付路由、锁屏、射频时延及内核结果需设备测试。

0.3.9 数据扩展与使用示例见 [docs/LOCAL-0.3.9.md](docs/LOCAL-0.3.9.md)，自编诊断卡见 examples/dol-diagnostic-card.json。

Release 构建：`gradlew.bat :app:exportReleaseApk`，生成已签名的 `artifacts/NfcCardSimulator-v1.0.0-release.apk`。
