# 鸿蒙工程

本目录是独立的 DevEco Studio / Hvigor 工程，Git 由上级 NfcCardSimulator 统一管理。Android 工程仍在仓库根目录，共用卡包说明与样本分别位于 ../docs、../examples。

## 打开与构建

使用 DevEco Studio 打开本目录。当前工程使用 HarmonyOS SDK 26.0.0，包名 com.iscolt.cardsimulator.harmony；先用 IDE 同步依赖，或在本目录执行 ohpm install。

本机 PowerShell 命令如下；其他机器按实际 DevEco 安装位置调整工具路径。环境变量仅作用于当前终端：

    $devecoRoot = 'D:/Program Files/Huawei/DevEco Studio'
    $env:DEVECO_SDK_HOME = "$devecoRoot/sdk"
    $env:JAVA_HOME = "$devecoRoot/jbr"
    $env:PATH = "$devecoRoot/jbr/bin;$devecoRoot/tools/node;" + $env:PATH
    & "$devecoRoot/tools/ohpm/bin/ohpm.bat" install
    & "$devecoRoot/tools/node/node.exe" "$devecoRoot/tools/hvigor/bin/hvigorw.js" --mode module -p product=default -p module=entry@default -p buildMode=debug assembleHap --no-daemon

产物位于 entry/build/default/outputs/default/。构建输出的未签名包可用于编译检查；安装真机需要与设备匹配的开发签名。

## 本地签名与设备

通用 build-profile.json5 只引用名为 default 的签名，不含证书路径和密码。优先在 DevEco 的签名界面完成本地配置，或参考 [签名模板](sign-config.example.json5)，将实际配置保存为 .sign/sign-config.json5；该目录被 Git 忽略。迁移保留了原有本地配置，可继续用原签名升级安装。证书仍保存在原来的用户配置目录中。

如果缺少本地签名，Hvigor 可能跳过签名并输出提示；不能把 assembleHap 通过视为可安装包已生成。签名时须使用 DevEco 自带 Java，系统旧 Java 可能无法读取新格式密钥库。

hdc 不在本机 PATH，位于 D:/Program Files/Huawei/DevEco Studio/sdk/default/openharmony/toolchains/hdc.exe。先执行 list targets；覆盖安装使用 install -r 并保留数据。hdc 在 Windows 上对正斜杠绝对文件路径处理有差异，可进入产物目录后使用文件名安装。

## 资源与验证

- tools/sync_android_icons.py 从 ../app/src/main/res 读取既有 Android 品牌图案，依赖 Pillow。
- 共用规格与示例以 ../docs/CARD-PACKS.md、../examples 为准；平台 UI、HCE 和系统设置行为仍需分别验证。
- 历史真机截图、UI 树和连续帧保留在本机 screenshots/，默认不入 Git；精选报告归档在 ../docs/validation/harmony/。
- .idea、.hvigor、.preview、oh_modules、build、.cache 和签名材料均为本地文件，不入库。
