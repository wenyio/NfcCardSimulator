# 鸿蒙工程约定

- 遵守根目录 AGENTS.md 的写作约定。
- 修改任何 .ets 前必须加载 hmos-arkui-develop-skill，并按其索引查阅相关 API 和规则。本机位置为 C:/Users/dzz19/AppData/Local/Huawei/DevEcoStudio26.0/deveco-code/v1/data/skills/hmos-arkui-develop-skill/SKILL.md；其他设备需查找实际安装路径。
- Android 对照源码在 ../app，共用格式说明与样本在 ../docs 和 ../examples。
- 签名配置仅保存在被忽略的 .sign/sign-config.json5；不要把密码、证书路径或私钥写入通用 build-profile.json5。构建说明见 README.md。
- 覆盖安装保留数据；未经用户授权不卸载或清空设备上的卡包与日志。
