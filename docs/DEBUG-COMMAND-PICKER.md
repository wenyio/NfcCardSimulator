# 手动 APDU 调试页调整

## 行为

- 命令下拉来自当前卡片规则，保留原顺序及重复步骤，默认第一条。显示序号、可明确识别的命令含义和四字节命令头；未知命令沿用规则名，空规则名显示规则序号。
- 支持 SELECT、GPO、READ RECORD、GENERATE AC、GET CHALLENGE、INTERNAL AUTHENTICATE、EXTERNAL AUTHENTICATE 和 GET DATA 的标准命令头；不猜测私有 CLA。
- 下拉仅填充完整 APDU，输入可编辑；点击“发送到脚本”才调用原 ScriptEngine。选择器不持有引擎或步骤游标，不会跳步、强制命中或自动选择下一条。
- 标题右侧圆圈 i 提供 48dp 点击区域，无障碍名称为“调试使用说明”。弹窗含填充与发送、独立会话与顺序检查、固定回复限制三点，“知道了”关闭。
- 移除标题下固定提示和底部说明卡片。发送、重置、回复、TLV 及日志路径维持原实现。
- 命令名称、选择控件、说明弹窗拆为 DebugCommandLabel、DebugCommandSelector、DebugHelp，核心逻辑含中文注释。

## 验证

- JVM：52 项通过，0 失败、0 错误，包括标准指令识别、未知/私有命令回退及重复步骤。
- Android 模拟器：8 项通过，78.116 秒，覆盖 DebugPageTest（2）、ScenarioMetadataTest（1）、NavigationTest（5）。
- 验证第一条初始化、选择不发送/不写日志、跳选步骤不命中、失败不推进、手动修改后发送、重置、独立引擎、回复/TLV、日志、空规则降级、说明弹窗与关闭。
- Lint：0 errors、19 warnings；构建及 git diff --check 通过。
- 已检查 work/debug-command-picker.png 和 work/debug-picker-detail.png，详情内标题、说明图标、下拉与输入布局正常。
- 日志：work/debug-picker-build.log、work/debug-picker-device.log。
- 本轮未进行真实手机/P19 射频验证。

## 安装包

本轮保留版本 0.3.12，构建包含当时工作区已有的卡包管理修改。另存安装包，便于与其他构建区分：

- artifacts/NfcCardSimulator-v0.3.12-debug-command-picker.apk
- SHA-256：7212767ee6de218ed16dc874c898b6e87b9676fdb7371941b993f4cd5a3b01f8
