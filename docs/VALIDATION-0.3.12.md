# v0.3.12 独立卡包管理页验证

- 旧工作区已先提交：9730969（新增独立卡包管理与场景 JSON 导入切换）。
- 新增独立管理页，首页卡包名称直接进入该页，移除原选择弹窗；内置卡包/我的卡包分组，显示数量、两行说明、当前使用标记。
- 移除使用按钮，点击整张卡包切换并返回首页，保留当前使用标签；行尾菜单独立响应，直接导出、重命名、删除；右上角导入复用现有文件选择、预览和确认。使用卡包或导入成功后返回首页，普通返回也刷新首页。
- 代码拆为 Activity 导航/生命周期、Page 列表展示和 Actions 操作；核心导航和权限逻辑含中文注释。
- minSdk 26 / targetSdk 31 / compileSdk 36；versionCode 15。

## 验证

- JVM：50 项通过，0 失败、0 错误；work/pack-manager-build.log。
- Lint：0 errors、20 warnings；work/pack-manager-test-build.log 与 app/build/reports/lint-results-debug.txt。
- 模拟器：7 项通过，29.244 秒；work/pack-manager-device.log。覆盖工具栏进入管理页、切包返回刷新且不启动模拟、内置/自建权限、空名称校验、重命名、页面重建、删除取消与确认、删除当前包回退，以及原卡包导入预览/更新/隔离/迁移流程。
- 截图采集改用管理页 decorView 绘制，避免系统截图捕获到前一页的转场画面；重跑该导航测试通过（work/pack-manager-visual.log）。work/pack-manager.png 已查看，布局正常。
- Debug APK 已安装到模拟器；未进行真实手机/P19 射频验证。本轮未修改 APDU、AID 数据或匹配引擎。
- git diff --check 通过。

产物：artifacts/NfcCardSimulator-v0.3.12-debug.apk
SHA-256：d1b50494694e6cf422b7e75e538c62b3c6268c58041a1cc68b9a3bad59fab2c3

最终交互调整回归：7 项通过，41.6 秒（work/pack-direct-device.log）；确认首页直达管理页、整张卡包可点击、无使用按钮，菜单操作不触发切包。最终构建/Lint 通过（work/pack-direct-build.log），管理页截图已重新核对。
