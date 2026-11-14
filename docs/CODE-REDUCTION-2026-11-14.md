# 首轮代码精简

2026-11-14，基于 d418b80 的工作区实施。保留原有功能、存储格式、依赖版本及 NFC 会话规则。

## 改动

- BER-TLV 查看与 DOL 诊断共用 BerTlv 的递归和边界检查；解析结果携带深度，保持原有缩进展示。整段解析成功后才向调用方返回结果，损坏尾部不会产生部分 DOL 定义。查看器原有错误提示和 SW 展示保留。
- UiKit 提供 systemBars 和 shell，首页、编辑页、卡包管理页复用窗口配色、工具栏与内容布局；各页保留自己的边距、滚动和生命周期。
- 卡片与规则编辑共用错误提示展示。
- 删除未使用的 Hello World 布局、black/white 颜色和与默认配置相同的夜间主题；保留启动屏使用的 startup_background。
- 保留实际使用的 Material 和分页依赖。ConstraintLayout 仍经 Material 传递引入，未将删除直接依赖声明作为缩包收益。

## 结果

同一环境、同一构建配置，比较修改前后重新构建的签名 Release：

| 指标 | 修改前 | 修改后 | 变化 |
|---|---:|---:|---:|
| 主程序 Kotlin 行数（含注释、空行） | 3,067 | 3,040 | -27 |
| Release APK 字节数 | 1,886,000 | 1,884,964 | -1,036（约 0.055%） |
| lint 错误 | 0 | 0 | 0 |
| lint 警告 | 28 | 25 | -3 |

生产源码与 XML 合计净减少 61 行；新增和修复测试后，全仓本次代码差异净减少 34 行，不含本文档。主要收益为消除重复维护，APK 缩小约 1 KiB。

## 验证

- testDebugUnitTest：55 项通过，包含新增的嵌套展示、长格式长度、合法前缀后损坏尾部用例。
- lintDebug、assembleDebugAndroidTest、exportDebugApk、exportReleaseApk：成功。
- 模拟器 emulator-5554：18 个不同设备用例均有通过记录，覆盖导航、前后台模拟启停、重建、草稿、匹配模式、卡包管理、JSON 菜单、调试收发及加载器。
- 首轮设备回归 16/18 通过。两处失败为测试立即断言后台重命名结果、测试仍匹配本地化前的帮助文案；修复等待条件和文案断言后，两个失败项及编辑保存共 3 项复测通过。测试等待在测试线程执行，不阻塞页面主线程。
- 检查卡包管理、编辑页面截图；Release v2 签名校验通过，覆盖安装及冷启动成功。
- 本轮未验证真实 NFC 射频收发；模拟器测试不能替代真机联调。

## 本地产物

- artifacts/NfcCardSimulator-v1.0.0-release.apk
- artifacts/NfcCardSimulator-v1.0.0-debug.apk
- artifacts/NfcCardSimulator-v1.0.0-release-mapping.txt（本次 Release 对应映射）
- work/code-reduction-before.apk（本轮修改前的对照包）
- work/code-reduction-build.log
- work/code-reduction-device-tests.log
- work/code-reduction-device-recheck.log

Release SHA-256：749f717c440b549f2cb7e1a7cad5a569c2f52c071d8e8274b3c5c35f64e26987。

## 第二轮：旧接口与规则 JSON 转换

在首轮未提交工作区上继续实施：

- 测试改用实际编辑保存入口 saveEditedCard，显式指定自建卡包；删除仅供测试的 SimulatorStore.importCards、CardPackRepository.saveCards。
- 删除仅供测试的 CardFileImporter 包装，来源追溯数据 ImportEvidence 保留并放入同名文件；来源测试改用实际 CardPackCodec 导入路径。
- 新增 RuleCodec，统一导出、整卡草稿和规则子页草稿的字段转换。严格导入校验仍由 ScriptCodec 执行；对外格式仍使用 match，非 MASK 导出仍省略 mask。规则子页草稿保存所有掩码输入，兼容此前 mode 字段的草稿。
- 加强设备用例：非法 APDU 在重建后保持原输入、保存被拒绝，修正后可正常保存。

第二轮主程序 Kotlin：3,040 → 3,024 行，净减少 16 行；两轮累计减少 43 行。新增 3 项规则转换单元测试后，单元测试共 58 项，全部通过。

第二轮 Release APK：1,884,964 → 1,884,928 字节，减少 36 字节；两轮累计减少 1,072 字节。体积基本持平，主要收益为清除无用入口和集中维护字段映射。

lintDebug：0 错误、25 警告；Debug、Release 及测试 APK 构建通过。模拟器 14 项设备测试全部通过，覆盖内置只读、编辑复制/恢复/校验、历史日志、来源元数据、调试和 JSON 菜单、保存后重载与删除。Release v2 签名校验通过，覆盖安装及冷启动成功。真实 NFC 射频验证边界与首轮相同。

第二轮记录：work/code-reduction-round2-build.log、work/code-reduction-round2-test-build.log、work/code-reduction-round2-device.log；修改前对照包为 work/code-reduction-round2-before.apk。artifacts 中 APK 和映射文件已更新为第二轮结果。

当前 Release SHA-256：55c189fe15a9146d921031f0d325655afd85b23f91c2c5e4c0355be7b45385ee。

## 第三轮：对象校验与编辑模式

- ScriptValidation 集中规则、卡片和集合的校验与规范化；JSON 导入、编辑保存、规则保存、交易日志转卡及仓库提交使用相同字段规则。元数据限制和 SHA-256 小写规范化由 CardMetadataCodec.validate 共用。
- 编辑与仓库保留原有序列化后的 2 MB 限制，因此仍需生成 JSON 计算大小，但不再把文本重新解析为对象来校验。持久化改用直接构建的 JSON 对象，去除额外的文本中转。
- ScriptCodec 暴露卡片及集合 JSON 对象构建，CardPackCodec 直接组合卡包对象；草稿保存也直接取卡片对象。
- EditorMode.NEW/COPY/EDIT 表达编辑状态，Activity 按模式选择本地化标题资源；读取旧草稿时兼容原中文 title 字段，新草稿保存 editorMode。
- 新增对象规范化、非法字段、数量/长度/大小边界、生成 ID、编辑模式和旧草稿兼容回归；设备用例验证仓库拒绝无效保存后内存及磁盘均保持原数据。

本轮不是源码或体积缩减：主程序 Kotlin 3,024 → 3,068 行，增加 44 行；Release APK 1,884,928 → 1,885,796 字节，增加 868 字节。相对最初 3,067 行和 1,886,000 字节，三轮累计为 Kotlin 增加 1 行、APK 减少 204 字节。新增代码用于对象校验接口及兼容处理，收益为减少运行时 JSON 解析和维护耦合；本轮未测量耗时，不给出性能提升比例。

验证：63 项单元测试、16 项模拟器设备测试全部通过；lintDebug 0 错误、25 警告；Debug/Release/测试 APK 构建成功；Release v2 签名、覆盖安装和冷启动通过。构建中曾遇到 Windows 占用 classes.dex，重试后成功，无需源码修复。真实 NFC 射频仍未验证。

记录：work/code-reduction-round3-final-build.log、work/code-reduction-round3-device.log；修改前对照包为 work/code-reduction-round3-before.apk。artifacts 中 APK 和映射已更新为第三轮结果。

最新 Release SHA-256：cbd30d855029eff01424fd96dd7b2d6b99c2db90dddf8c84acb9aa1426aefde7。

## 后续修正：移除新增的旧草稿兼容

用户明确当前应用无需向后兼容，删除上述新增兼容分支：CardEditorDraft 直接读取 editorMode、packId 和 initialPackId，不再解析中文 title 或回退旧卡包字段；RuleCodec 只读取 match，不再接受规则子页旧 mode 字段。页面标题继续使用 strings.xml，状态仅保存枚举名称。同步删除旧格式兼容测试，保留三种模式恢复及未完成输入恢复验证。

本次 62 项单元测试通过（删除 1 项旧格式兼容测试）；Debug/Release 构建、导出及 Release 签名校验通过。未重跑设备测试。日志：work/no-draft-compat-build.log。APK 和对应映射已更新，Release 为 1,885,484 字节，比上次减少 312 字节。

最新 Release SHA-256：7d45d7f6816974fa9e141b66548e45217fa1f1422888409da1ba5b7f0d885719。
