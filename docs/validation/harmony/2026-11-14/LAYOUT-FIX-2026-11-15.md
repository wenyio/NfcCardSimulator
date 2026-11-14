# 页签布局与滑动同步修复

2026-11-15，ALN-AL00 真机；使用原签名覆盖安装，保留已有卡包和日志。本轮仅修改下列三份 ArkTS 源文件，其他历史问题不在本轮完成范围内。

## 修改与原因

| 项目 | 确认结果 | 修复 |
| --- | --- | --- |
| 首页分类切换后贴左 | 点击 Amex 后，其文字从 x=253px 跳到 x=52px；scrollToIndex 默认 START 且无平滑动画 | 改用平滑 AUTO 对齐，完整可见的项不滚动，超出视口才做最小滚动 |
| 详情页签过宽、紧贴内容 | 修复前页签文字范围横跨 x=0～1260，白色栏铺满屏幕，下方卡片没有间隔；100% 宽加左右 margin 未形成期望内缩 | 用外层容器的左右 padding 限定页签宽度；下方留出 12vp，调试卡片也使用相同左右内缩 |
| 演示卡 APDU 上方双倍间距 | 无场景时 ScenarioPanel 不渲染内容，但调用处 12vp margin 仍占位，再叠加规则卡的 12vp | 将场景间距放进仅在可见时创建的场景卡根容器，隐藏时不占位 |
| 滑动时页签变化迟缓 | 详情原来等 onAnimationStart 后单独执行 300ms 指示线动画，没有使用拖动中的位移 | onContentDidScroll 逐帧计算连续页码 index - position，指示线随实际内容位移移动，标签按最近页高亮，落定时校准 |
| 首页分类高亮迟缓 | 原来仅靠最终 onChange 更新分类样式 | 点击立即反馈，拖动按实际位移越过半页时更新高亮；与最终 pageIndex 分开，避免将每帧值回写为翻页指令 |

修改文件：pages/Index.ets、pages/CardDetailPage.ets、components/ScenarioPanel.ets。未改 HCE、卡包格式或 APDU 规则。修改前副本位于项目 .cache/layout-fix-20261115/；该目录属于本机备份，不应入 Git。

开发前已加载 DevEco 自带技能：C:/Users/dzz19/AppData/Local/Huawei/DevEcoStudio26.0/deveco-code/v1/data/skills/hmos-arkui-develop-skill/SKILL.md，并查阅布局、渲染、状态规则及当前 SDK 的滚动回调签名。Tabs.onContentDidScroll 从 API 23 提供，本工程兼容 SDK 为 26；position 是页宽比例，mainAxisLength 单位为 vp，计算连续页码不需要再除一次宽度。

## 构建与真机结果

- Hvigor debug assembleHap、ArkTS 编译、打包及原配置签名通过，最终 BUILD SUCCESSFUL。日志：项目 .cache/layout-fix-20261115/build.log；构建仍有既有废弃接口和异常处理警告。命令行必须将 DevEco jbr/bin 放在 PATH 前部，避免系统 Java 8 导致签名容器解析失败。
- hdc install -r 成功，应用正常启动；BCTC 34 张卡、演示包和自建包仍在。未卸载、清空应用或修改已有卡片。
- 首页点击“全部→Amex”后，Amex 保留在 x=253px，未跳到左侧；点击右边部分可见的 Mastercard 后做最小滚动，Visa 可从分类条末端选择。快速横滑从 Amex 正常进入 Discover。
- 演示卡详情的页签与信息卡、规则卡均为 x=52～1208px，左右各 16vp；页签下边至首张卡片为 39px，约 12vp。演示卡信息卡至 APDU 卡片由原来的约 78px（24vp）恢复为 39px（12vp）。
- 有场景的 BCTC D00101 仍显示信息、测试场景、APDU 三张卡，间距一致；展开运行条件，切到调试后快速反向滑回，展开内容保留。
- 详情慢拖连续帧中，指示线随页面连续移动，过半时高亮从详情切到调试；快滑反向、短距离取消、连续点击详情／调试／日志均回到正确页面与标签。
- Discover 调试页发送原 PPSE，完整回复仍为 6F2D840E325041592E5359532E4444463031A51BBF0C1861164F07A000000152301050084843452044454D4F8701019000，TLV 正常；日志页正常。

## 证据与限制

- 修复前：spacing-category-selected-before.json、spacing-category-before-live.jpeg、spacing-demo-before.json、spacing-demo-before-live.jpeg。
- 布局修复后：spacing-demo-after-live.jpeg、spacing-detail-final.json、spacing-debug-final.json、spacing-scenario-final-live.jpeg。
- 动态跟随：detail-follow-final-contact.png、detail-follow-final-00～14.jpeg 与 timing.json，覆盖约 3.75 秒慢拖；帧 01～11 可观察内容位移与指示线一起变化。
- 首页：spacing-category-auto-after.json、spacing-home-fast-final.json／jpeg、spacing-home-end-final.json、spacing-home-last-category.json、home-demo-follow-final-00～14.jpeg。
- 场景保持与取消：spacing-reverse-final.json、spacing-cancel-final.json、spacing-repeated-final.json。

连续截图的采样通常约 0.4～0.5 秒，部分命令更慢，因此只证明中间状态及落定状态，不宣称测得帧率、触摸延迟毫秒数或每一帧都无掉帧。首页前两次慢拖落在列表底部／纵向内容边界，未成功切页，未作为通过证据；随后改用单卡演示分类的空白区与快速横滑确认。

本轮没有重新验证真实 NFC 射频、默认付款列表、文件导入或所有历史修复项；这些仍按 REPORT.md 的原始边界记录。设备验证结束回到演示卡包列表。Git 管理评估见 Android 工程 docs/CROSS-PLATFORM-GIT.md，本轮未搬迁或初始化仓库。
