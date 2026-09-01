# 快递查询

一个使用 Kotlin 与 Jetpack Compose 编写的 Android 本地快递查询应用。

此项目为独立 Android 本地数据版本：

- 不需要账户登录或自建后端。
- 运单、物流轨迹、备注、昵称和收件手机号保存在当前设备。
- 联网时直接调用 ALAPI 查询物流，直接调用快递鸟订阅/查询取件码。
- 不提供云端备份、设备管理、邮件通知、推送通知和自动更新。
- 卸载应用或清除应用数据会删除全部本地数据。

项目不附带任何真实凭据。请复制 `android/offline.env.example` 为
`android/offline.env`，再填写自己的接口参数。接口名称、请求地址和字段结构均已保留。

源码中的包名、签名、开发者姓名、邮箱、域名、地址和原应用图标均已替换为通用内容。

构建命令：

```powershell
cd android
Copy-Item offline.env.example offline.env
# 编辑 offline.env，填写自己的接口凭据
gradle assembleDebug
```

## 开源许可

本项目采用 [MIT License](LICENSE)。
