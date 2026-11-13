# 启动白屏优化验证

2026-11-13，基于当前 0.3.13 工作区。

## 原因与修改

原先 SimulatorApp.onCreate 在主线程构造 SimulatorStore，首页绘制前同步读取、解析内置卡包、用户卡包及历史日志。默认窗口背景为白色，等待期间没有反馈。

- StoreLoader 在进程内启动一次后台加载，主线程通过可取消订阅接收完成结果。
- MainActivity 先绘制深蓝工具栏及居中加载提示，再构建卡片列表；页面销毁取消订阅，旋转保留待恢复状态。
- 系统补发的编辑/管理页结果在数据就绪后处理，SAF 启动器仍在 STARTED 前注册。
- 导出缓存改为需要恢复导出时才在后台读取。
- Android 12 系统启动屏及旧版本启动窗口配置浅灰底、卡片图标；没有人为设置最短停留时间。
- 模拟启用状态仍随进程重启清除，仓库完全加载后才能进入模拟。

## 验证

- testDebugUnitTest：53 项通过。
- lintDebug：0 错误、20 警告。
- assembleDebugAndroidTest、exportDebugApk：成功。
- StoreLoaderTest、NavigationTest、CardEditorNavigationTest：10 项设备测试通过；最终线程优先级调整后 StoreLoaderTest 2 项再次通过。
- 已确认加载页及加载完成的列表截图。

模拟器 emulator-5554，调试构建，保留应用数据，am force-stop 后 am start -W：

- 修改前 3 次首屏：7596 / 7798 / 9330 ms。
- 修改后稳定复测 3 次首帧：1708 / 1993 / 2095 ms。此处首帧可能是加载提示，不能等同卡片列表可操作。
- 最终版本复测：首帧 1570 ms，reportFullyDrawn 2582 ms，仓库后台加载 458 ms。
- 构建期间曾出现 37.7 秒首帧、17 秒仓库加载的异常高耗时，不作为稳定性能结果；模拟器与宿主负载使测量波动较大。

以上只能反映本次模拟器环境，真机及卓易通需要重新实测，不能承诺相同比例的提升。

构建：work/build-startup-final.log；设备回归：work/startup-device-tests.log；加载器最终回归：work/startup-final-device-tests.log；测量：work/startup-before.txt、work/startup-after.txt、work/startup-final-start.txt。
