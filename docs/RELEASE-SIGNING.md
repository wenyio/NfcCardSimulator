# Release 签名

发布构建使用独立的本地证书，配置文件为根目录 signing.properties，格式见 signing.properties.example。路径相对项目根目录；Windows 路径使用正斜杠。

本次生成 RSA 3072 位 PKCS12 证书，别名 release，有效期 10000 天。私钥位于 .signing/release.p12，密码保存在 signing.properties；两者均被 Git 忽略。仓库外备份位于 D:/Payment/NfcCardSimulator-backups/release-signing-1.0.0。请妥善保管这两份文件，后续覆盖安装必须沿用同一证书。

使用 JDK 17 执行：

```powershell
.\gradlew.bat :app:testReleaseUnitTest :app:lintRelease
.\gradlew.bat :app:exportReleaseApk
```

产物：artifacts/NfcCardSimulator-v1.0.0-release.apk，包名 com.iscolt.cardsimulator。未配置本地签名时 Release 构建会明确失败。Debug 构建继续使用 Android 调试签名和独立包名。

如果设备已有同包名、其他证书签署的版本，需要先卸载后安装，卸载前可导出自己的卡包。
