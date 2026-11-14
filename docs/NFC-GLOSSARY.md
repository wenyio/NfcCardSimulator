# NFC 与代码术语表

按需查阅；运行流程和报文示例见 [阅读指南](CODE-READING-GUIDE.md)。中文采用常用译名，英文便于对照规范。

## NFC 与报文

| 缩写 | 英文全称 | 中文名称 | 含义 |
| --- | --- | --- | --- |
| NFC | Near Field Communication | 近场通信 | 近距离无线通信技术 |
| HCE | Host Card Emulation | 主机卡模拟 | 手机应用处理卡片命令，系统负责射频传输 |
| RF | Radio Frequency | 射频 | 无线信号与连接层 |
| UID | Unique Identifier | 唯一标识符 | 卡片底层标识；本项目不提供任意设置能力 |
| EMV | Europay, Mastercard and Visa | 欧陆卡、万事达卡与维萨 | 名称源自创始组织，现指相关支付芯片卡规范体系 |
| ICC | Integrated Circuit Card | 集成电路卡 | 芯片卡 |
| AID | Application Identifier | 应用标识符 | SELECT 的选择目标，也用于 HCE 路由 |
| PPSE | Proximity Payment System Environment | 近距离支付系统环境 | 非接触支付发现入口，名称为 2PAY.SYS.DDF01 |
| APDU | Application Protocol Data Unit | 应用协议数据单元 | 卡片应用层的通信报文 |
| C-APDU / R-APDU | Command APDU / Response APDU | 命令 / 响应应用协议数据单元 | 终端发命令，卡片返回数据及状态字 |
| T / C | Terminal / Card | 终端 / 卡片 | 日志中的通信双方 |
| CLA | Class byte | 类别字节 | 命令头第 1 字节 |
| INS | Instruction byte | 指令字节 | 命令头第 2 字节 |
| P1 / P2 | Parameter 1 / Parameter 2 | 参数 1 / 参数 2 | 命令头第 3、4 字节，含义随指令变化 |
| Lc | Length of Command Data | 命令数据长度 | 数据域字节数 |
| Le | Expected Response Data Length | 期望响应数据长度 | 短格式 00 表示最多 256 字节，扩展格式 0000 表示最多 65,536 字节 |
| SW / SW1 / SW2 | Status Word / Status Word 1 / Status Word 2 | 状态字 / 状态字节 1 / 2 | 响应末尾两字节，由脚本提供 |
| GPO | Get Processing Options | 获取处理选项 | 常见命令头 80A8，输入按 PDOL 排列 |
| GAC / GENERATE AC | Generate Application Cryptogram | 生成应用密文 | 常见命令头 80AE；GAC 是本项目简写 |
| AC | Application Cryptogram | 应用密文 | 交易相关密码学结果，本项目只返回配置值 |
| BER | Basic Encoding Rules | 基本编码规则 | 本项目使用其中限定的 TLV 编码形式 |
| TLV | Tag-Length-Value | 标签—长度—值 | 带类型和长度的数据；构造型值可包含子 TLV |
| FCI | File Control Information | 文件控制信息 | SELECT 回复中的控制信息 |
| DOL | Data Object List | 数据对象列表 | 只声明标签及所需长度，不含字段值 |
| PDOL | Processing Options Data Object List | 处理选项数据对象列表 | 标签 9F38，定义 GPO 输入 |
| CDOL | Card Risk Management Data Object List | 卡片风险管理数据对象列表 | CDOL1（8C）和 CDOL2（8D）定义两次 GENERATE AC 输入 |
| TTQ | Terminal Transaction Qualifiers | 终端交易属性 | 标签 9F66 |
| TVR | Terminal Verification Results | 终端验证结果 | 标签 95 |
| CVM | Cardholder Verification Method | 持卡人验证方法 | 9F34 为验证方法结果 |
| UN | Unpredictable Number | 不可预知数 | 标签 9F37，终端提供的交易输入 |

指令名称本身不是缩写：

| 英文名称 | 中文名称 | 本项目识别的命令头 |
| --- | --- | --- |
| SELECT | 选择 | 00A4 |
| READ RECORD | 读取记录 | 00B2 |
| GET CHALLENGE | 获取挑战数 | 0084 |
| INTERNAL AUTHENTICATE | 内部认证 | 0088 |
| EXTERNAL AUTHENTICATE | 外部认证 | 0082 |
| GET DATA | 获取数据 | 80CA |

## 标签与状态字

标签是十六进制编号。以下为诊断器涉及的数据；长度以实际 DOL 定义为准。

| 标签 | 英文名称 | 中文名称 |
| --- | --- | --- |
| 6F / A5 | FCI Template / FCI Proprietary Template | FCI 模板 / 专用模板 |
| 4F / 50 | Application Identifier / Application Label | 应用标识符 / 应用标签 |
| 83 | Command Template | 命令模板，GPO 中包裹 DOL 字段值 |
| 9F02 / 9F03 | Amount, Authorised / Amount, Other | 授权金额 / 其他金额 |
| 9F1A / 5F2A | Terminal Country Code / Transaction Currency Code | 终端国家代码 / 交易货币代码 |
| 9A / 9C | Transaction Date / Transaction Type | 交易日期 / 交易类型 |
| 9F35 / 9F33 | Terminal Type / Terminal Capabilities | 终端类型 / 终端能力 |
| 9F34 | CVM Results | 持卡人验证方法结果 |
| 9F21 | Transaction Time | 交易时间 |
| 9F45 | Data Authentication Code | 数据认证码 |
| 9F4C | ICC Dynamic Number | ICC 动态数 |

| 状态字 | 常用含义 | 本项目用途 |
| --- | --- | --- |
| 9000 | 命令成功 | 诊断器从命中且成功的回复学习定义，不代表整笔交易成功 |
| 6700 | 长度错误 | 空命令、十六进制或总长度检查失败 |
| 6985 | 使用条件不满足 | 未启用、未选卡或内置 GPO 错误路径 |
| 6D00 | 指令不受支持或无效 | 默认 fallback，也可能因步骤或数据不匹配而返回 |
| 6F00 | 无精确诊断的失败 | 服务异常兜底 |

## Android 与存储

| 缩写或名称 | 英文全称/原名 | 中文名称 | 含义 |
| --- | --- | --- | --- |
| APK | Android Package | Android 应用安装包 | 编译、打包并签名后的安装文件 |
| Activity / Service | Activity / Service | 页面组件 / 服务组件 | 分别承载页面生命周期与系统服务入口 |
| UI | User Interface | 用户界面 | 视图及交互，主线程更新 |
| Handler / Looper | Handler / Looper | 消息处理器 / 消息循环器 | 向指定线程队列投递任务 |
| SAF | Storage Access Framework | 存储访问框架 | 系统文档选择与创建机制 |
| URI | Uniform Resource Identifier | 统一资源标识符 | 系统授权访问的文档标识 |
| JSON | JavaScript Object Notation | JavaScript 对象表示法 | 卡片、卡包、草稿和日志的文本格式 |
| UTF-8 | Unicode Transformation Format, 8-bit | 8 位 Unicode 转换格式 | 文本编码，字节数与字符数可能不同 |
| BOM | Byte Order Mark | 字节顺序标记 | 文本起始标记，解析时移除 |
| ASCII | American Standard Code for Information Interchange | 美国信息交换标准代码 | PPSE 名称对应字节的字符编码 |
| SHA-256 | Secure Hash Algorithm 256-bit | 256 位安全散列算法 | 文件指纹；不是加密或来源认证 |
| UUID | Universally Unique Identifier | 通用唯一标识符 | 新卡、新包及会话的标识来源 |
| AtomicFile | AtomicFile | 原子文件 | 文件提交与回滚工具 |
| SharedPreferences | SharedPreferences | 偏好设置 | 保存选卡等少量配置，apply 异步写盘 |
| Bundle / Binder | Bundle / Binder | 键值状态容器 / 进程间通信机制 | 状态传输大小有限，长草稿另存文件 |
| dp / sp / px | Density-independent Pixel / Scale-independent Pixel / Pixel | 密度无关像素 / 缩放无关像素 / 像素 | 分别常用于布局、字号、实际像素 |
| MiB | Mebibyte | 二进制兆字节 | 1 MiB = 1,048,576 字节 |

项目变量 `key`、`id`、`sessionRevision` 不是协议缩写；含义见模型和仓库注释。位运算与长度编码的逐行解释见 `BerTlv.kt`、`DolDiagnostics.kt`。
