# KoraIM 客户端架构

## 组件边界

`IMClient` 是宿主应用唯一需要依赖的公开门面，本身不再持有数据库实现、Binder 细节或监听器集合。

```text
App / imui
    │ Flow + suspend
    ▼
IMClient
    ├── ConnectionManager ── LocalBinder ── IMService ── Netty
    ├── MessageRepository ── MessageDao ── SQLite
    ├── UserRepository ───── UserDao / IMUserInfoProvider
    └── IMEventHub ───────── SharedFlow / StateFlow
```

## Service 与 Binder 决策

`IMService` 明确运行在宿主应用进程内，Manifest 设置为 `exported=false`，使用普通 `LocalBinder`。项目不再启用 AIDL。

原因是当前消息数据库、事件流和 SDK 运行时均在应用进程内。保留 AIDL 会让接口看起来支持跨进程，但进程被杀、双向事件回调、Parcelable 版本和 Binder 死亡恢复均没有完整实现。

如果以后需要独立 IM 进程，应作为单独架构版本实现：

1. 给 Service 配置独立 `android:process`。
2. 定义稳定的 Parcelable 协议 DTO，而不是传 JSON 字符串。
3. 增加双向 AIDL callback、客户端注册和注销。
4. 实现 Binder death recipient、重新绑定与状态恢复。
5. 将数据库所有权和 Flow 事件桥接明确放在一个进程内。

## 公开异步 API

- `connectionState: StateFlow<ConnectionState>`：连接状态。
- `incomingMessages: SharedFlow<Message>`：新收到的消息事件。
- `messageUpdates: SharedFlow<Message>`：ACK、失败等消息状态变化。
- `observeMessages(sessionId): Flow<List<Message>>`：会话持久化消息列表。
- `observeLastMessage(sessionId): Flow<Message>`：会话最后一条消息。
- `sendMessage`、`getMessagePage`、`getUserInfo`：挂起函数。

UI 应在 `repeatOnLifecycle` 中收集 Flow，避免 Fragment View 销毁后继续更新界面。
