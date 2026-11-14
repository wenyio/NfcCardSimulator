# Android 与鸿蒙的 Git 管理约定

2026-11-15 已将鸿蒙工程纳入本仓库 harmony/；Android 工程位置和原 Git 历史保留，仓库尚未配置 remote。两端分别构建，提交统一在根目录管理。

## 当前结构：同一仓库，平台工程各自构建

单仓指两个平台的源码、共同规格和回归材料由同一份 Git 历史管理。例如一次修改卡包导入格式，可以在同一个提交中更新 Kotlin、ArkTS、格式说明与测试样本，再分别构建 Android 和鸿蒙。它不要求两端使用同一种语言，也不要求共享 Gradle 与 Hvigor 构建脚本。

当前两端属于同一产品，卡包格式、APDU 匹配规则和功能行为需要持续对齐；本轮也需要频繁对照两端实现。相比独立发布和团队权限隔离，保持行为一致是当前更实际的需求，因此推荐放在 NfcCardSimulator 下统一 Git 管理。

已保留现有 Android 文件路径，在根目录新增 harmony/ 放置鸿蒙工程：

```text
NfcCardSimulator/
  .git/
  AGENTS.md
  app/                    # 现有 Android 工程
  gradle/
  build.gradle.kts
  harmony/                # 鸿蒙源码、资源和 Hvigor 工程配置
  docs/                   # 共用规格与平台差异说明
  examples/               # 卡包、交易文本与共同测试样本
```

这样保留 Android 历史与现有工具路径，避免同时搬动两个工程。若以后确有目录整理需求，再通过 git mv 将 Android 移到 android/，并同步调整脚本与 IDE 工程入口；当前没有必要为目录对称先做一次大迁移。

两端业务层目前分别是 Kotlin 和 ArkTS，单仓不会自动消除重复实现。优先共用格式说明和输入／预期输出样本，分别运行平台实现来检查一致性；不要为了共用代码引入不必要的跨语言桥接。平台 UI、HCE 生命周期和权限配置保留在各自工程内。

## 与双仓的取舍

| 方面 | 一个仓库 | 两个仓库 |
| --- | --- | --- |
| 跨平台行为变更 | 一个提交可完整表达两端与规格变更 | 需要关联两个提交，容易遗漏一端 |
| 发布与构建 | 按平台目录分别执行构建，标签可用 android-v…／harmony-v… | 构建和版本天然独立 |
| 规格与测试样本 | 保存一处，容易同时更新 | 需另定唯一来源和同步方式 |
| 权限与团队隔离 | 仓库访问权限统一 | 可分别开放源码、分配团队 |
| 当前迁移成本 | 导入尚无 Git 的鸿蒙工程即可 | 鸿蒙新建仓库更直接，但长期多一套同步流程 |

如果将来两端由不同团队维护、权限不同，或产品功能与发布时间长期独立，双仓会更合适。当前未见这些要求，不建议使用子模块：它仍是多个仓库，却额外增加提交指针和拉取步骤。

## 迁移与维护

1. 鸿蒙导入前已在 D:/Payment/NfcCardSimulator-backups/harmony-before-monorepo-20261115-023757.zip 保存独立备份并校验 ZIP；复制后逐文件 SHA-256 校验 501 个文件一致。原目录被运行中的进程占用，当前保留为旧副本，以 harmony/ 为后续唯一开发入口；重新打开 IDE 时选择新位置。
2. 根目录 .gitignore 已排除鸿蒙本地签名、IDE／构建缓存、依赖目录和原始截图。harmony/ 的通用 build-profile.json5 已移除签名材料，只引用 default；实际配置保留在被忽略的 .sign/sign-config.json5，入库模板位于 harmony/sign-config.example.json5。
3. 精选历史报告和证据归档到 docs/validation/harmony/2026-11-14；原始 UI 树、连续帧和本地探测脚本保留在被忽略的 harmony/screenshots/，报告中的原始文件名仍可在那里查找。
4. 本轮以现有鸿蒙源码为导入基线，包含此前已完成的 UI 修改；未创建提交、配置 remote 或改写历史。后续功能修改应独立提交，两端构建可按路径分别触发。
5. Android Studio 打开仓库根目录，DevEco Studio 打开 harmony/。具体 SDK、签名与命令见 harmony/README.md；共用规格与样本保留在 docs/、examples/，暂不批量重排已有文档。

迁移验证：Android :app:assembleDebug 通过；鸿蒙在新目录重新安装依赖，34 个构建任务全量执行，ArkTS 编译、打包及原签名 SignHap 全部通过。迁移仅调整工程位置与管理配置，未重新安装手机应用。
