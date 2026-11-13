# v0.3.11 卡包验证

> 历史记录：本文描述旧版行为。1.0.0 已移除第三方内置卡包，当前仅保留 6 张自编连通性演示卡。

- minSdk 26 / targetSdk 31 / compileSdk 36；versionCode 14。
- JVM：50 项通过，0 失败、0 错误。
- 设备：AVD 上 14 项通过，耗时 205.095 秒，覆盖卡包存储/旧数据迁移、跨包同 ID、整包替换、导入确认预览、顶部切换、编辑草稿、详情/后台停止、日志清除。记录 work/card-packs-device-final.log。
- Lint：0 errors、20 warnings；work/card-packs-final-verified-build.log 确认成功。BOM 字符仅改成等价 Unicode 转义，不改变运行字节码。
- 最终导出：work/card-packs-artifact-final.log，BUILD SUCCESSFUL。BCTC 资源按生成脚本统一顶层字段顺序后重新打包，APDU/AID/匹配配置未变。
- 34 张 BCTC 的 169 对命令/回复、AID、ordered、fallback 与前次提交逐项一致。生成脚本连续重跑输出字节一致。
- 截图 work/card-packs-home.png 已查看，卡包入口位于深蓝工具栏，首页没有新增下拉筛选行。

产物：artifacts/NfcCardSimulator-v0.3.11-debug.apk
大小：5939175 字节
SHA-256：ef8e7c9af0542ebbe84cfd85c1b44d23e0305809e68772c62de5aba42e249a1c

本轮验证界面与本地数据流程，未进行真实手机与 P19 射频配对验证。
