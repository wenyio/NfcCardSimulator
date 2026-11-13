# 0.1.0 验证记录

日期：2026-11-13。

- Debug APK 构建并导出，包名和 API 配置已用 aapt 核对。
- APK v2 签名验证通过，Android Debug 证书。
- 11 个本地测试通过：10 个脚本/配置测试，1 个工程模板测试。
- Android 36.1 x86_64 模拟器 3 项测试通过：应用上下文、手动 APDU 及日志、自定义卡持久化和删除。
- 旧 Espresso 输入注入不兼容此模拟器系统；已改为 UI 线程操作实际 View 进行冒烟测试。
- 模拟器初次启动有 System UI 无响应弹窗，关闭后重启应用并检查主页面，无应用崩溃记录。
- 主页面截图：work/qa/main.png。
- 未在 P19 安装或修改应用。无 HCE 手机，尚未实测真实 AID 路由、支付设置、锁屏和 NFC 时延。
- Lint 仅豁免 ExpiredTargetSdkVersion，因为按约定 targetSdk 31、自行分发；保留其他检查和非阻断告警。

构建指纹见 artifacts/build-info.json。
