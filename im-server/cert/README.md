# TLS 证书目录

默认服务端配置要求以下文件存在：

```text
im-server/cert/server.crt
im-server/cert/server.key
```

仓库内已包含一套仅用于测试的自签名证书和私钥，提交后换电脑可以直接启动。Android `imcore` 内置并固定信任对应的测试证书。

这套证书和私钥只能用于测试，生产环境请替换为受信任 CA 签发的证书，并改为安全的证书配置方式。

如果暂时只想运行已有的明文 Node 测试：

```powershell
$env:TLS_ENABLED="false"
npm start
```
