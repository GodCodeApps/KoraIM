# KoraIM TLS 配置

KoraIM 默认使用 TLS 传输加密。TLS 只保护客户端与服务端之间的网络传输，客户端和服务端业务层仍然处理明文 JSON，数据库结构不变。

## 服务端

编辑 `im-server/config.json`：

```json
{
  "port": 8090,
  "tlsEnabled": true,
  "wireLogEnabled": true,
  "tls": {
    "certFile": "./cert/server.crt",
    "keyFile": "./cert/server.key",
    "minVersion": "TLSv1.2"
  }
}
```

证书路径相对于 `im-server` 目录。仓库内已包含一套仅用于测试的证书和私钥，因此克隆项目后可以直接启动。服务端证书和私钥必须存在，否则服务端会拒绝启动。也可以使用环境变量覆盖配置：

```powershell
$env:TLS_ENABLED="true"
$env:TLS_CERT_FILE="D:\cert\server.crt"
$env:TLS_KEY_FILE="D:\cert\server.key"
npm start
```

仅用于明文测试时：

```powershell
$env:TLS_ENABLED="false"
npm start
```

## Android 客户端

SDK API 的默认值是 TLS 开启：

```kotlin
IMClient.init(applicationContext, host, port, tlsEnabled = true)
```

如需在本地测试时查看传输内容，可额外传入 `wireLogEnabled = true`：

```kotlin
IMClient.init(applicationContext, host, port, tlsEnabled = true, wireLogEnabled = true)
```

客户端 Logcat 中：

- `TLS-CIPHER`：经过 TLS 加密后的字节，以十六进制打印；
- `TLS-PLAINTEXT`：经过 TLS 解密、进入 NDJSON 解码器前的明文字节。

服务端开启 `wireLogEnabled` 后，会打印 `[Wire][PLAINTEXT]`，表示服务端 TLS 解密后收到的业务明文。日志只用于本地测试，可能包含账号、消息内容和令牌，生产环境应关闭：

```powershell
$env:WIRE_LOG_ENABLED="false"
npm start
```

Demo 的开关位于 `app/src/main/java/com/kora/im/MainActivity.kt` 的 `SERVER_TLS_ENABLED`。

客户端和服务端必须使用相同模式：

```text
server.tlsEnabled = true  <=> client.tlsEnabled = true
server.tlsEnabled = false <=> client.tlsEnabled = false
```

切换模式后需要重启服务端，并让客户端重新初始化连接。TLS 握手完成后，客户端才会发送 login、sync 和消息帧。

## 证书注意事项

当前测试客户端固定信任 `imcore/src/main/res/raw/kora_im_dev_cert.crt`，并使用证书固定来支持局域网 IP 变化。该证书和私钥只适合测试，不能用于生产。生产环境应改用受信任 CA 证书、系统 CA 校验和匹配服务域名的证书。
