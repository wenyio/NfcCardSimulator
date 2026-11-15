# 从零读懂 NFC 卡模拟器

本应用通过 Android HCE 接收终端命令，按脚本选择固定回复，再交给系统发送。射频传输由系统负责；脚本不会重算动态密文或签名。术语见 [术语表](NFC-GLOSSARY.md)。

## 阅读顺序

源码根目录：`app/src/main/java/com/iscolt/cardsimulator/`。

| 顺序 | 文件 | 重点 |
| --- | --- | --- |
| 1 | `core/CardScript.kt`、`Hex.kt`、`ScriptEngine.kt` | 数据模型、字节表示、规则匹配 |
| 2 | `nfc/DetailSimulationSession.kt`、`NfcController.kt` | 页面状态与启动条件 |
| 3 | `hce/CardEmulationService.kt` | 真实命令入口与会话结束 |
| 4 | `SimulatorApp.kt`、`MainActivity.kt`、`data/StoreLoader.kt` | 启动、导航、生命周期 |
| 5 | `ui/debug/DebugPage.kt`、`diagnostics/` | 手动调试、报文解析、差异诊断 |
| 6 | `core/*Codec.kt`、`ScriptValidation.kt`、`data/` | 编解码、校验、保存 |
| 7 | `ui/cards/CardEditorActivity.kt`、`CardEditorDraft.kt`、`io/`、`importer/` | 草稿、编辑、导入导出 |

## 运行主线

```text
打开详情 → DetailSimulationSession.sync() 检查页面条件
         → NfcController.start() 检查设备并注册 AID
终端命令 → Android → CardEmulationService.processCommandApdu()
         → ScriptEngine.exchange() → 固定回复 → Android → 终端
连接丢失或服务切换 → onDeactivated() 丢弃引擎
```

区分三类状态：

| 状态 | 持有者 | 变化规则 |
| --- | --- | --- |
| 启动意图与页面条件 | `DetailSimulationSession` | 有详情卡、前台、未编辑、用户请求启动时才尝试启用 |
| 实际启用 | `SimulatorStore.enabled`、`NfcController` | 检查 NFC/HCE、开关和默认支付服务，注册成功才启用；不表示已连接终端 |
| 执行进度 | `ScriptEngine`、`DolDiagnostics` | 保存步骤及诊断定义；切卡、配置修订或服务失活使旧引擎失效 |

注册 AID 只设置路由，SELECT 回复仍由规则提供。服务声明在 `AndroidManifest.xml`，静态 PPSE 与解锁要求在 `res/xml/apdu_service.xml`。手动调试使用独立引擎，不推进真实 NFC 会话。

## 跟读一条命令

打开内置 `app/src/main/assets/demo-cards.json`，在卡片副本中实验。选择 PPSE 的命令示例：

```text
00 A4 04 00 | 0E | 325041592E5359532E4444463031 | 00
CLA INS P1 P2  Lc          2PAY.SYS.DDF01          Le
```

四字节头表示按名称选择；Lc=0E 表示数据长 14 字节，短格式 Le=00 表示期望最多 256 字节。内置规则用前缀匹配，允许末尾携带 Le。接着选择业务 AID；内置 GPO 规则以 `80A80000` 开头，回复 `6985`，演示在此结束。

| 模式 | 判断方式 |
| --- | --- |
| EXACT | 命令完全相等 |
| PREFIX | 输入以规则命令开头 |
| MASK | 总长度相同，逐字节比较双方与掩码的结果；FF 全比较，00 忽略 |

内容模式取第一条命中规则；顺序模式只检查当前步骤，命中才推进，失败或结束后返回 fallback。重复 SELECT 清除诊断定义，但不重置顺序步骤。回复包含 SW1/SW2；`9000` 仅表示本条命令成功。

PREFIX 规则可短至 2 字节，例如 `80AE` 只比较 CLA/INS；真实输入仍至少需要 4 字节命令头。宽泛回退应排在具体规则之后。使用这些通用规则表达第三方配置的例子见 [BCTC 配置兼容](BCTC-COMPATIBILITY.md)。

## TLV 与 DOL 的区别

**TLV 用来携带数据：字段是什么、值有多长、具体值是什么。DOL 用来列出数据要求：需要哪些字段、各占多少字节、按什么顺序提供。** 两者可以配合使用：先用 TLV 传递一份 DOL，再按这份清单发送实际值。

本节所有报文都用十六进制表示：**两个字符是一字节，长度计算的是字节数**。例如 `11 22 33 44` 是 4 字节，`0A` 表示十进制 10，`0C` 表示十进制 12。空格和竖线只用于分组阅读，不属于报文。

### 1. TLV：把字段和值一起传递

TLV（**Tag-Length-Value，标签—长度—值**）包含三部分。以不可预知数为例，可以把它理解为终端提供的随机数：

```text
9F37 | 04 | 11 22 33 44
标签   长度   具体值
```

- `9F37`：标签，表示“不可预知数”这个字段。标签本身占 2 字节。
- `04`：后面的值占 4 字节；不包含标签和长度自身。
- `11 22 33 44`：实际的字段值。

这条 TLV 总共占 `2 + 1 + 4 = 7` 字节。接收方读到标签就知道字段类型，读到长度就知道从哪里开始读取下一个字段。

### 2. DOL：先约定需要哪些值

DOL（**Data Object List，数据对象列表**）由一组组“标签＋所需长度”组成。例如：

```text
9F02 06 | 9F37 04
金额6字节  随机数4字节
```

它要求对方依次提供：第一项是 6 字节授权金额，第二项是 4 字节不可预知数。**清单里还没有金额或随机数的实际值。**

对方随后按约定只提交值：

```text
00 00 00 00 01 00 | 11 22 33 44
    金额：6字节      随机数：4字节
```

接收方按清单取前 6 字节作为金额，再取后 4 字节作为随机数。实际数据中不再重复 `9F02`、`9F37` 和各自长度，所以必须知道对应清单才能确定字段边界。金额这里只展示编码，实际金额单位取决于货币及小数位。

这里有两个不同长度：**清单本身长 6 字节**（两个标签各 2 字节、两个长度各 1 字节），而**它要求的实际值共 10 字节**（6＋4）。另外，DOL 每项的长度是单字节数值，不能套用 BER-TLV 的长格式长度规则。

### 3. 为什么一个例子里同时出现 TLV 和 DOL

PDOL（**Processing Options Data Object List，处理选项数据对象列表**）是一种 DOL，用来说明 GPO（**Get Processing Options，获取处理选项**）命令需要哪些终端数据。卡片通常在业务应用 SELECT 回复中，用标签 `9F38` 携带它：

```text
9F38 | 06 | 9F02 06 9F37 04
标签   长度   值：一份 PDOL 清单
```

外层是 TLV，里面的值是一份 DOL。外层 `06` 数的是清单自身的 6 字节；清单中的 `06`、`04` 分别要求后续提供 6 字节金额和 4 字节随机数。

同理，较短的例子 `9F38 03 9F37 04` 中，`03` 表示清单 `9F37 04` 本身长 3 字节，`04` 表示后续需要的随机数长 4 字节。**两处长度描述的对象不同。**

终端读懂上面的双字段清单后，将 10 字节实际值放入 GPO 的 `83` 命令模板中：

```text
80 A8 00 00 | 0C | 83 0A | 000000000100 | 11223344 | 00
   命令头     Lc   模板/长度    金额6字节    随机数4字节  Le
```

`83 0A ...` 又是一条 TLV：标签为 `83`，值长 10 字节，值就是依清单排列的金额和随机数。APDU 的 `Lc=0C` 则数整个数据域：`83` 的 1 字节＋`0A` 的 1 字节＋实际值的 10 字节，共 12 字节。末尾 Le 不在 Lc 计数内。

因此，这个过程是：**卡片给出 PDOL 清单 → 终端按清单准备值 → GPO 携带这些值**。`83` 的值内部没有各字段标签，不能继续把它当作 TLV 逐项解析。

### 4. 对应到本项目的代码

- `BerTlv.parse()` 拆解回复中的 TLV，找到 `9F38` 及其值。`9F38` 是普通数据标签，其值不会自动作为子 TLV 递归解析。
- `DolDiagnostics.observe()` 记录已经命中且回复 `9000` 的 PDOL 定义；`parseDol()` 将其拆成标签与长度项。
- `commandData()` 从 GPO 中取出 `83` 的值，`describe()` 再按 DOL 顺序拆分预期值与实际值，解释哪个字段不同。对十六进制字符串切片时，字节长度要乘 2。

这套解析用于差异诊断，不改变规则回复。内置连通性演示卡未提供这里的完整 PDOL 流程；可对照 `examples/dol-diagnostic-card.json` 和 `DolDiagnosticsTest` 阅读。

## 数据与线程

- 启动：仓库后台加载，首页订阅结果；同步 `await()` 会阻塞调用线程。
- 收发：HCE 回调默认在主线程，日志追加内存，约 300 ms 后合并写盘。
- 保存：卡包事务由 `PackWork` 后台执行；先校验、原子写盘，成功后更新内存。
- 文件：导入解析和目标文档写入在后台；`stage()` 的私有缓存写入在调用线程。
- 草稿：允许半成品，长内容放私有文件，Bundle 只存 token；正式保存才做完整校验。

卡片 key 由包 ID 与卡片 ID 组成；`sessionRevision` 用于使旧引擎失效。两者均不同于协议 AID。

## 验证与维护

结合 `ScriptEngineTest`、`DetailSimulationSessionTest`、`DolDiagnosticsTest`、`CardEditorDraftTest` 阅读相应边界。JDK 17 下运行：

```text
gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

真实路由、射频时延及终端结果需设备联调。函数注释解释职责和关键原因，术语统一查表，避免重复；保留当前生命周期恢复，不增加历史格式兼容，界面文字使用 `strings.xml`。
