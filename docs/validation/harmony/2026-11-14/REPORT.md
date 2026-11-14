# 鸿蒙版 UI 对照验证记录

2026-11-15 后续已按用户新授权修改页签布局、隐藏场景的间距与滑动同步，详见 [布局修复及真机验证](LAYOUT-FIX-2026-11-15.md)。下文为前期问题记录，其“未修改源码”仅描述当时的验证阶段。

验证日期：2026-11-14 至 2026-11-15（Asia/Shanghai，验证跨午夜）。本轮仅记录问题，未修改任何应用源码或 `.ets`，未重新构建或安装应用。

## 环境与范围

- 鸿蒙：ALN-AL00 真机，HarmonyOS 7.0.0.111(SP10C00E111R4P3)，1260 × 2720；应用 `com.iscolt.cardsimulator.harmony`，界面显示 v1.0.0。通过用户指定的 hdc 操作。
- Android 对照：已有 NfcCardSimulator_QA 模拟器，1080 × 1920；已安装的 `com.iscolt.cardsimulator`，界面显示 v1.0.0。系统语言为英语，卡片内容为中文。
- 使用同一张内置 Discover 连通性演示卡，对照首页、详情、规则展开、手动调试、日志及复制编辑页面；鸿蒙另外验证了保存、未保存修改确认、JSON 查看、删除确认。
- 截图与 UI 树采用同名文件保存。不同分辨率、语言和系统外观造成的合理差异不直接判为缺陷。

## 已发现的问题

### UI-01：多个表单控件内容不可见（P1）

复现：打开 Discover 演示卡，进入“调试”；再进入“复制后编辑”检查字段。

实际表现：调试页的命令下拉框、C-APDU 输入框看起来是大片空白；编辑页的卡片名称、卡包、卡组织、AID、说明、执行方式和兜底回复也不可见。UI 树能读取相应值，点击选择和发送仍能工作，说明数据存在，但未正常显示给用户。

预期：控件边界、当前选项及输入内容应可辨认，输入后可以核对。该问题直接影响编辑和调试的可用性。Android 对照页能显示这些内容。

证据：[鸿蒙调试](harmony-debug-gpo.png)、[Android 调试](android-debug-gpo.png)、[鸿蒙编辑](harmony-editor.png)、[Android 编辑](android-editor.png)。对应 JSON/XML 中保留了实际文字。

定位线索（未确认根因）：`components/EditorField.ets` 的 TextInput/TextArea、`components/DebugPanel.ets` 的 Select/TextInput 未显式指定文字和背景颜色；`entryability/EntryAbility.ets` 使用 COLOR_MODE_NOT_SET，而面板使用固定白底。疑似系统颜色模式与固定浅色面板混用，应后续分别在浅色/深色模式复测。本轮未切换设备外观设置。

### UI-02：详情页未选中页签对比度不足（P2）

复现：打开卡片详情，观察“详情／调试／日志”；切换后继续观察未选中的两个页签。

实际表现：未选中页签文字接近白色，背景为浅灰，几乎看不清；选中项显示蓝色，可以辨认。三个页签仍可点击。

预期：未选中页签保持清晰的文字对比度。Android 对照页可辨认三个页签。

证据：[鸿蒙详情](harmony-detail.png)、[鸿蒙调试](harmony-debug-gpo.png)、[Android 详情](android-detail.png)。定位线索：`pages/CardDetailPage.ets` 的三个默认 `.tabBar(...)` 与固定浅色背景组合，可能与 UI-01 同源。

### UI-03：编辑页重复显示字段标题（P3）

复现：打开“复制后编辑”，查看 AID、说明及未匹配回复区域。

实际表现：“AID（每行一个）”下面再次显示“AID”；“说明”下面再次显示“卡片说明”；“未匹配时回复（十六进制）”下面再次显示“未匹配时回复”。重复信息增加页面高度。

预期：每个字段保留一处主要标题，标题中保留必要格式提示。Android 对照的 AID、说明区域只有一处标题。

证据：[鸿蒙编辑](harmony-editor.png)、[Android 编辑](android-editor.png)。源码线索：`pages/CardEditorPage.ets` 外层 Text 标题与 `components/EditorField.ets` 内部 fieldLabel 同时显示。

### UI-04：上下系统栏为黑色，未与应用外观衔接（P2）

复现：前台打开鸿蒙版首页，观察顶部状态栏和底部手势导航区；与 Android 首页对照。

实际表现：鸿蒙顶部状态栏和底部手势区均为黑色，顶部与深蓝工具栏形成明显色块边界，底部与浅色内容区也有明显黑色分隔。首页、详情和编辑页截图均可见。Android 版上下系统栏使用深蓝色，顶部与工具栏同色。

预期：鸿蒙版的系统栏颜色、内容安全区和图标明暗应统一适配，达到与 Android 版相近的整体效果。这里记录的是视觉衔接差异，不能仅凭截图断言 Android 使用了完整的边到边布局。

证据：[鸿蒙首页系统栏](harmony-app-system-bars.png)、[Android 首页系统栏](android-system-bars.png)。源码核对：Android `ui/UiKit.kt` 的 systemBars 明确设置 statusBarColor/navigationBarColor 为 #152538；鸿蒙 `entryability/EntryAbility.ets` 的窗口创建逻辑未见对应系统栏或全屏布局配置。

### UI-05：“NFC 设置”仅打开设置主页（P2）

复现：首页右上角齿轮 → NFC 设置。

实际表现：进入系统“设置”主页，页面显示账号、WLAN、星闪和蓝牙等分类，没有直达 NFC 开关页面。

预期：优先打开 NFC 专页；若目标系统不支持对应跳转，应明确告知用户后续手动路径。

证据：[实际落地页面](harmony-nfc-settings-confirmed.png)及同名 JSON。源码确认：`nfc/NfcController.ets` 的 nfcSettings 调用 launchSettings，目标为 com.huawei.hmos.settings.MainAbility，未传 NFC 目标信息。

### UI-06：“默认支付设置”仅打开设置主页（P2）

复现：首页右上角齿轮 → 默认支付设置。

实际表现：同样进入系统“设置”主页，没有直达默认支付应用选择页。当前应用又要求先设为默认支付应用，因此用户无法通过此入口直接完成启动前置条件。

预期：优先打开默认支付设置页或系统选择流程；若系统无法直达，应提供明确的手动设置路径。

证据：[实际落地页面](harmony-payment-settings-target.png)及同名 JSON。源码确认：paymentSettings 与 nfcSettings 调用同一个 launchSettings，目标完全相同。两个入口均已分别点击验证，未修改系统配置。

### UI-07：鸿蒙版应用图标未同步 Android 品牌图案（P2）

2026-11-15 已修复，并同步应用内操作图标；见 [图标同步与验证](ICON-SYNC-2026-11-15.md)。以下保留原始问题记录。

复现：返回真机桌面，观察并排的两个“NFC 卡模拟器”图标。

实际表现：鸿蒙原生版图标呈近乎空白的浅色方块；旁边带卓易通角标的 Android 版显示深蓝底、蓝白卡片和非接触波纹图案。不是同一图案经过不同圆角裁切造成的差异。

预期：鸿蒙版保持 Android 版的卡片／NFC 图案与品牌配色，按鸿蒙图标规范适配。

证据：[同屏桌面对照](harmony-launcher-icon.png)及同名 JSON。资源核对：Android `res/mipmap-anydpi/ic_launcher.xml` 引用 ic_launcher_foreground/ic_launcher_background，前景定义卡片和波纹，背景为 #152538；鸿蒙 AppScope 与 entry 的 layered_image.json 均引用自己的 foreground/background 资源。启动窗口图标是否一致需另行验证，本条仅确认桌面应用图标。

### UI-08：首页列表区域不支持左右滑动切换分类（P2）

2026-11-15 补充验证：从“全部”分类，在列表下方空白区分别向左、向右滑动，分类与六张卡片列表均未变化；点击 Amex 标签后正常筛选为一张 Amex 卡。此处指列表内容区域的分页切换，不是顶部分类条本身横向滚动。

源码确认：首页 Index.ets 使用横向 Scroll 展示分类按钮，点击时修改 filter，内容是单个纵向 Scroll，没有横向分页容器或切分类手势。Android CardsPage.kt 使用 swipePages 为分类建立页面。

证据：harmony-home-blank-swipe-left/right 的 PNG、JSON，以及 harmony-home-click-amex。首次在卡片边缘注入横滑曾进入卡片详情，已改用空白区隔离点击干扰；该首次操作不作为分类验证依据。

预期：列表区域左右滑动切换相邻分类，与标签选择同步；保留纵向滚动、卡片点击与更多菜单。

### UI-09：详情页页签指示线未随内容滑动同步移动（P2）

2026-11-15 补充验证：详情页支持从“详情”向左滑到“调试”。连续采集 18 帧，03 帧中调试内容已占据大部分屏幕，而蓝色选中文字和指示线仍停留在“详情”；04 帧中页面落到调试，选中状态和指示线才切换。确认存在滑动过程中指示器不跟手的现象。

证据：detail-motion-contact.png、detail-motion-00 至 17.jpeg、detail-motion-timing.json、harmony-detail-after-swipe.json。采样间隔约 0.44～0.56 秒，且截图命令会引入开销，不能据此给出精确延迟，也尚未独立确认用户所述“内容完全落定后仍等待一会”的时长。后续应采用录屏或帧级性能工具复测。

源码线索：CardDetailPage.ets 使用 Tabs(index: activeTab)，只在 onChange 中写回 activeTab；Android SwipePages.kt 的 onPageScrolled 持续按位置和偏移更新标签指示器。此为排查方向，不能直接认定 onChange 回写就是根因。

验收：慢拖、快速甩动、中途取消、反向拖动和点击页签时，内容、选中状态与指示线协调变化；页面落定后不出现额外补动，切页不重置脚本会话、用户输入或规则展开。

补充说明：本次连续帧中默认控件文字、未选中标签和系统栏已与首次截图外观不同，未修改源码，也未核实是否安装版本或系统外观变化。UI-01、UI-02、UI-04 保留原始证据，修复前必须在当前版本与浅色／深色模式下复核，不应视为在所有环境下必现。Android 本次补测遇到应用无响应，不能把该次操作作为流畅度或滑动时序基准，分类分页机制已由源码确认。

### UI-10：导入卡包后首页未及时显示目标卡包（P2）

2026-11-15 用户反馈：导入成功后没有默认进入刚导入的卡包。本轮确认 Android 的预期行为：SimulatorStore.kt 的 importPack 在仓库保存成功后调用 switchPack(saved.id)，保存 activePack、清除选卡并停止模拟；首页导入完成调用 showList，管理页导入完成调用 finish，返回首页由 Activity Result 再调用 showList。因此应自动显示导入后的卡包列表，不自动进入某张卡详情或启动模拟。

鸿蒙并非完全缺少切包逻辑：SimulatorStore.ets 的 importPack 同样调用 switchPack(saved.id)，两个入口也分别调用 refresh 或返回首页。已确认存在通知顺序缺口：switchPack 先调用 setEnabled(false)，经 log → notifyChanged 同步刷新监听页面，此时 activePackId 仍为旧值；之后才更新 activePackId 和偏好存储，末尾没有再次通知。Index 的刷新将当前包复制到 packName/cards 等 @State，普通 activePackId 字段本身不触发这些快照更新。首页直接导入有完成回调 refresh，可补上刷新；管理页导入只有关闭弹层和 pathStack.pop，没有显式刷新首页，Index 也未提供导航返回时的专门刷新处理。

判断：Android 自动切包已由源码确认；鸿蒙状态更新与通知顺序缺口已确认，是“管理页导入后仍显示旧列表”的直接排查方向。本轮未重新导入文件独立复现，不将该路径推断写成真机复现结果，也不把用户报告泛化为所有导入入口均失败。实际安装版本与源码是否一致仍需核对。

验收：从首页与卡包管理页分别导入新包、更新已有包，成功后立即显示保存结果对应的卡包标题与卡片列表；切换到另一包时分类重置为全部，重新启动仍使用目标包。取消或解析／保存失败不切包；不自动选择卡片或启动模拟。修复应统一提交后的状态通知和页面返回刷新，避免仅重复写入 activePackId 掩盖旧快照问题。

### UI-11：卡片列表长标题被单行省略（P2，当前安装表现需复核）

2026-11-15 用户反馈：卡片列表标题显示不全，变成省略号。源码确认：首页 Index.ets 的 Text(card.name) 使用 maxLines(1) 与 TextOverflow.Ellipsis，且右侧更多菜单占据固定宽度，长卡名无法换行。Android CardsPage.kt 的名称使用 UiKit.label 创建 TextView，未限制单行或设置省略，行高随换行增长。这是两端明确的显示策略差异，鸿蒙具备文字换行能力。

本轮设备截图存在差异：harmony-title-current.json 读取到首页 BCTC 列表及完整名称，但同次 uitest screenCap 保存的 PNG 却是卡片详情，显示顶部标题被省略，不能拿它证明列表截断。随后使用唯一文件路径 snapshot_display 取得的 harmony-title-live.jpeg 确为首页，其中 D00301、D00401、D00501 的长名称已换行显示，没有复现这些条目的省略。两次采集未修改应用代码，也没有更换安装包；截图与 UI 树可能不同步，当前安装内容与本地源码的对应关系尚未核实。因此记录用户反馈及已确认的源码约束，保留“当前安装版列表截断待复测”的边界；详情工具栏的省略也不等同于本条列表缺陷。

修复验收：参考 Android，卡片列表名称可随可用宽度换行，卡片高度自适应，完整名称可辨认；覆盖长中文、英文、无空格连续标识及大字体，保证更多菜单与卡片点击正常。不要直接修改名称数据、缩小至难读字号或用固定高度裁掉换行内容。顶部卡包标题与详情工具栏另行评估，不机械地套用列表布局。

## HCE-02：缺少 payment-aid 声明，默认支付配置无法完成（P1）

2026-11-15 用户反馈：已卸载 Android 版，系统默认付款选项仍未出现鸿蒙版应用。该列表缺失现象由用户直接观察；本轮没有在系统默认付款页面独立截图复现。

已确认：源码 entry/src/main/module.json5 的 EntryAbility 声明包含 HOST_APDU_SERVICE action 和 NFC_CARD_EMULATION 权限，但没有 payment-aid metadata。hdc 的 bm dump -n com.iscolt.cardsimulator.harmony 同样显示已安装包 EntryAbility.metadata 为 []，证明缺失存在于设备上的安装包，非仅本地源码。精简证据保存为 hce-installed-declaration-evidence.txt。

公开文档 getPaymentServices 明确说明：声明 HCE 功能且声明 payment-aid 的应用会包含在支付服务列表中。本机 SDK @ohos.nfc.cardEmulation.d.ts 也给出 abilities[].metadata 下 payment-aid 的声明格式。当前动态注册 AID 不能补上安装包缺失的支付服务声明，而且应用在调用动态注册前已经被默认支付检查拦截。

判断：已确认存在支付服务声明缺失，足以解释当前列表缺项，属于优先修复项。保留默认支付检查的同时，必须先使应用具备被发现和选择的配置条件。修复后仍需真机确认实际出现在默认付款列表、可选中及 isDefaultService 返回情况；不能仅凭补齐配置就宣布列表或射频问题全部解决。Android 卸载本身不会补齐鸿蒙应用声明，暂无证据表明问题由两个版本共存造成。

修复方向：按当前 SDK 文档给 HCE ability 配置有效的 payment-aid，核对独立 HCE skill/action 的匹配条件，静态发现入口与选卡时的动态业务 AID 职责分开。可参考 Android 当前使用的 PPSE 入口，但须查证鸿蒙格式、路由与多 AID 约束；不要盲目静态注册全部卡组织 AID。升级安装后用 bm dump 确认声明进入包，再到系统页面验收。

参考：[支付服务列表说明](https://github.com/openharmony/docs/blob/master/zh-cn/application-dev/reference/apis-connectivity-kit/js-apis-cardEmulation-sys.md)。getPaymentServices 属系统接口，不应让普通应用调用它或为此申请系统权限。

## HCE-01：非默认支付应用是否允许前台模拟（待真机射频验证，保留现有逻辑）

用户反馈：早期 HCE 验证版可能无需设为默认钱包也能模拟，但用户尚未实测；要求证据不足时保留 Android 版逻辑。

已确认的事实：

1. 当前版本确实由应用主动拦截：`nfc/NfcController.ets` 的 start 在 isDefault() 为 false 时先抛出“请到设备设置中将本应用设为默认支付应用”，未执行后面的 ensureRoutes，也没有调用系统 HceService.start。因此现有界面的失败提示不能证明系统拒绝了非默认应用的 HCE 请求。status/isReady 也使用了该条件。
2. 本机 SDK `ets/api/@ohos.nfc.cardEmulation.d.ts` 对 HceService.start 的说明是启动 HCE、设置前台优先并动态注册 AID，未列出“必须为默认支付应用”的前提；isDefaultService 是独立查询接口。
3. OpenHarmony 公开接口文档同样说明 start 包括“设置当前应用为前台优先，动态注册 AID 列表”。公开实现中 HceSession::StartHce 检查权限和调用方身份，CeService::StartHce 检查 NFC 开启后设置 foregroundElement_ 和 dynamicAids_，该调用链中未见必须默认支付应用的检查。

判断：有依据认为“前台 HCE 必须先成为默认钱包”并非鸿蒙公开接口的普遍前提，当前移植版本的前置条件可能比系统需要的更严格。但 OpenHarmony 公开实现不等于此华为真机固件，也不能证明 PPSE／支付 AID 在本机的真实路由结果。尚未核对早期验证版源码及其运行日志，也未在非默认状态下完成终端 APDU 收发。

处理：本条作为待验证的平台行为差异记录，不宣称已确认“非默认钱包可模拟”，不移除现有默认支付检查，遵循用户要求保留 Android 版逻辑。后续需在明确授权的验证版本中保持非默认支付状态，记录 start 返回情况、是否收到 PPSE／业务 AID 命令、实际回复及前后台行为；仅 start 成功不足以判定射频验证通过。

参考：[公开 API 文档](https://github.com/openharmony/docs/blob/master/zh-cn/application-dev/reference/apis-connectivity-kit/js-apis-cardEmulation.md)、[HceSession 实现](https://github.com/openharmony/communication_nfc/blob/master/services/src/ipc/card_emulation/hce_session.cpp)、[CeService 实现](https://github.com/openharmony/communication_nfc/blob/master/services/src/card_emulation/ce_service.cpp)。以上为核对当时的 master 内容，可能随后更新。

## 本轮验证通过

| 流程 | 观察结果 |
| --- | --- |
| 首页及详情导航 | 鸿蒙可进入内置演示卡详情，显示 AID、说明和三条规则 |
| 规则展开 | SELECT PPSE 可展开显示完整命令和回复 |
| 命令选择 | 选择第 2、3 条命令后，UI 树中的输入值对应变化 |
| SELECT PPSE | 两端完整回复逐字节一致，以 9000 结束，TLV 展示一致 |
| SELECT AID | 两端完整回复逐字节一致，以 9000 结束，TLV 展示一致 |
| GPO | 两端均返回 6985，符合演示脚本预期 |
| 日志页 | 两端可打开日志页；鸿蒙可读取按会话组织的记录 |
| 复制并保存 | 鸿蒙复制演示卡，名称插入 QA_UI_20261114 标记后保存成功，详情显示新名称 |
| 放弃未保存修改 | 鸿蒙再次编辑，插入 UNSAVED 后返回，弹出确认；放弃后原保存名称未被覆盖 |
| 查看 JSON | 鸿蒙弹层显示已保存卡片及规则 |
| 删除本轮测试卡 | 删除前显示名称确认，删除后自建卡片包为空；未删除已有卡片或日志 |

## 未验证与边界

- 真机提示“请到设备设置中将本应用设为默认支付应用”，已截图 `harmony-emulation-issue.png`。这是当前设备配置状态，未据此判定 HCE 实现失败；本轮未更改默认支付设置。
- 未进行真实终端 NFC 收发、射频时延、断场重置、锁屏和后台恢复验证。手动调试通过不代表射频验证通过。
- 未完整验证文件导入导出、卡包更新、每种匹配模式、规则编辑、异常格式、横屏、大字体、进程死亡后的草稿恢复。
- Android 模拟器启动早期曾出现 System UI 无响应，选择等待后恢复；该现象不计入鸿蒙应用缺陷。
- 本轮增加了验证脚本、截图、UI 树和本报告；设备会保留此次操作产生的正常日志。
