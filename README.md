# KoraIM

KoraIM 是由 GodCodeApps 开源的 Android IM 客户端框架。项目目前处于早期预览阶段，包含核心通信与本地存储模块、聊天 UI Kit，以及用于本地联调的 Node.js TCP 服务端。

## 模块

- `imcore`：TCP 长连接、消息 ACK、本地 SQLite 存储、用户资料缓存。
- `imui`：文本、图片、语音消息展示和聊天输入面板。
- `app`：Android 接入示例。
- `server`：仅供开发联调的 Node.js 示例服务，不是生产级 IM 服务端。

详细组件边界与 Service 决策见 [客户端架构](docs/architecture.md)。

## 快速开始

1. 启动测试服务：

   ```bash
   cd server
   npm start
   ```

2. 在示例 App 中配置测试服务器地址，然后初始化：

   ```kotlin
   ImSdkImpl.setAccount("test2")
   IMClient.init(applicationContext, "192.168.1.5", 8090)
   ```

3. 应用退出登录或彻底停止 IM 功能时释放资源：

   ```kotlin
   IMClient.release()
   ```

## 传输协议

当前协议使用 UTF-8、每行一个 JSON 帧。客户端发送 `message` 帧，服务端持久化或接受后应返回相同 `messageId` 的 `ack` 帧。只有收到 ACK，客户端才会把消息标记为发送成功。

```json
{"type":"message","messageId":"uuid","payload":{"messageId":"uuid"}}
{"type":"ack","messageId":"uuid"}
```

该协议仍是预览版，后续版本会增加鉴权、心跳、协议版本、离线同步和送达/已读回执。

## 当前限制

- 示例服务器仅用于本地开发，不包含账号鉴权、持久化和消息路由。
- 暂未发布 Maven Central 坐标。
- API 在 `1.0.0` 之前可能发生不兼容变更。

## 构建检查

```bash
./gradlew assembleDebug test lint
```

## 许可证

Copyright 2026 GodCodeApps

本项目基于 [Apache License 2.0](LICENSE) 开源。
