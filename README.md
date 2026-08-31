# KoraIM

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-Android-purple.svg)](https://kotlinlang.org)

KoraIM 是一个模块化的 Android 即时通讯示例工程，包含通信内核、聊天 UI、WebRTC 实时语音/视频通话、Android Demo 和 Node.js 联调服务端。

项目适合用于学习 IM 架构、快速搭建业务原型，以及二次开发自己的聊天 SDK。当前服务端是联调实现，正式上线前仍需补充鉴权、TLS、限流、监控、推送和 TURN 等生产能力。

## 效果预览

<div align="center">
  <img src="screenshot/Screenshot_20260822_084229.png" width="48%" />
  <img src="screenshot/Screenshot_20260822_084252.png" width="48%" />
  <br />
  <img src="screenshot/Screenshot_20260822_084336.png" width="48%" />
  <img src="screenshot/Screenshot_20260822_084402.png" width="48%" />
</div>

## 已实现功能

### 消息与会话

- 文本、Emoji、图片、语音、视频、文件、位置、名片、红包和提示消息
- 消息发送 ACK、失败状态和重新发送
- TCP 长连接、心跳、断线重连和网络状态监听
- SQLite 持久化、会话列表、分页历史消息和响应式数据流
- 基于游标的增量同步与离线消息补拉
- 会话未读数、总未读数和底部消息 Tab 的 `99+` 角标
- 正在输入状态
- 消息转发、引用、引用定位
- 两分钟内撤回、双方同步撤回结果及重新编辑入口
- 本地删除消息和删除会话
- SPI 自定义消息附件与自定义气泡

### 实时语音与视频

- 独立 `imcall` 模块，媒体基于 WebRTC，信令复用 `imcore` 长连接
- 一对一语音通话和视频通话
- 呼叫、响铃、接听、拒绝、取消、挂断、忙线和 45 秒无人接听
- 静音、扬声器、前后摄像头切换和通话计时
- 视频全屏远端画面和右上角本地预览
- 通话页复用聊天用户资料、昵称和头像
- 服务端维护通话占用状态，避免同一用户同时进入多路通话
- 通话结束由服务端生成唯一 `CALL` 消息，支持实时投递和离线同步
- 通话记录展示已取消、已拒绝、未接听、忙线、异常中断和通话时长
- 通话记录只允许本地删除，不支持转发、引用和撤回

> WebRTC 默认只配置 STUN，适合局域网和能够建立 P2P 连接的网络。复杂 NAT、跨运营商网络和生产环境必须部署 TURN 中继。锁屏来电、后台可靠唤醒和系统级来电通知仍需结合厂商推送或统一推送服务完善。

## 模块结构

```text
KoraIM
├── imcore      通信核心：TCP、信令、ACK、重连、增量同步、SQLite、消息与会话 API
├── imui        聊天 UI：会话列表、消息列表、输入面板、附件、气泡、引用与撤回交互
├── imcall      实时通话：WebRTC 音视频、通话状态机和通话 Activity
├── app         Android 示例：登录、联系人、单聊入口、底部未读角标和模块集成
└── im-server   Node.js 联调服务：消息持久化、离线同步、撤回和通话信令
```

推荐依赖方向：

```text
app ──> imui ──> imcore
 └────> imcall ─> imcore
```

`imcall` 不依赖 `imui`。它通过 `imcore` 获取用户资料和发送通话信令，业务 App 可以独立替换聊天 UI 或通话 UI。

## 环境与构建

- Android minSdk 24
- Java 11 字节码目标
- Android Gradle Kotlin DSL
- Node.js 联调服务端
- SQLite 或 MySQL 服务端存储（参见 `im-server/config.js`）

构建 Debug APK：

```bash
./gradlew :app:assembleDebug
```

Windows：

```powershell
.\gradlew.bat :app:assembleDebug
```

## 快速接入

### 1. 引入模块

`settings.gradle.kts`：

```kotlin
include(":imcore")
include(":imui")
include(":imcall")
```

业务模块：

```kotlin
dependencies {
    implementation(project(":imui"))
    implementation(project(":imcall"))
}
```

`imui` 和 `imcall` 都会暴露其所需的 `imcore` 能力，不需要让二者互相依赖。

### 2. 初始化消息与通话

必须先设置账号，再初始化 `IMClient`：

```kotlin
ImSdkImpl.setAccount(account)
IMClient.init(applicationContext, host = "192.168.1.6", port = 8090)
IMCall.init(applicationContext)
```

退出登录或销毁 SDK：

```kotlin
IMClient.release()
```

### 3. 配置用户资料

聊天列表、消息头像和通话页面共用 `IMUserInfoProvider`：

```kotlin
IMClient.userInfoProvider = object : IMUserInfoProvider {
    override fun getUserInfo(account: String): UserInfo? = findLocalUser(account)

    override fun fetchUserInfoFromServer(
        account: String,
        callback: (UserInfo?) -> Unit
    ) {
        fetchRemoteUser(account, callback)
    }
}
```

也可以主动更新缓存与本地用户表：

```kotlin
IMClient.updateUserInfo(userInfo)
IMClient.updateUserInfos(userInfoList)
```

### 4. 配置媒体上传

图片、文件、录音和普通视频消息需要业务侧提供上传实现：

```kotlin
ImUIKitImpl.setMediaMessageProvider(AppMediaMessageProvider())
```

发送媒体消息：

```kotlin
IMMediaMessageSender.send(message)     // 挂起直到上传和发送完成
IMMediaMessageSender.enqueue(message)  // 交给后台队列
IMMediaMessageSender.isMedia(message)  // 判断是否为媒体消息
```

### 5. 挂载会话列表与聊天页

```kotlin
class MessageFragment : IConversationListFragment() {
    override fun onConversationClick(conversation: Conversation) {
        startActivity(Intent(requireContext(), ChatActivity::class.java).apply {
            putExtra("session_type", conversation.sessionType)
            putExtra("session_id", conversation.sessionId)
            putExtra("peer_id", conversation.peerId)
        })
    }
}
```

```kotlin
class P2PChatFragment : IMessageFragment()
```

聊天 Fragment 参数：

```kotlin
Bundle().apply {
    putInt("session_type", SessionType.P2P)
    putString("session_id", sessionId)
    putString("peer_id", peerId)
}
```

### 6. 发起语音或视频通话

```kotlin
IMCall.startAudioCall(requireContext(), peerId)
IMCall.startVideoCall(requireContext(), peerId)
```

呼入页面由 `IMCall` 收到邀请后自动打开。通话页内部使用：

```kotlin
IMCall.accept()
IMCall.reject()
IMCall.hangup()
```

监听通话状态：

```kotlin
lifecycleScope.launch {
    IMCall.session.collect { session: CallSession? ->
        // OUTGOING / INCOMING / CONNECTING / CONNECTED / ENDED
    }
}
```

## 核心 API

### IMClient：连接与事件

| API | 说明 |
|---|---|
| `init(context, host, port)` | 初始化数据库、通信服务和增量同步 |
| `release()` | 断开连接并释放当前账号运行时资源 |
| `connectionState` | `StateFlow<ConnectionState>` 连接状态 |
| `incomingMessages` | 新消息 `SharedFlow<Message>` |
| `messageUpdates` | 消息状态、撤回等更新流 |
| `typingEvents` | 对方正在输入事件流 |
| `callSignals` | `SharedFlow<CallSignal>` 原始通话信令流 |
| `sendTyping(receiverId)` | 发送正在输入状态 |
| `sendCallSignal(signal)` | 发送底层通话信令；通常由 `IMCall` 调用 |

### IMClient：消息

| API | 说明 |
|---|---|
| `sendMessage(message)` | 入库、发送并等待 ACK |
| `saveMessage(message)` | 仅保存或更新本地消息 |
| `saveMessages(messages)` | 批量保存本地消息 |
| `getMessage(messageId)` | 同步查询本地消息 |
| `getMessageById(messageId)` | 挂起查询本地消息 |
| `getMessagePage(sessionId, page)` | 分页读取历史消息 |
| `observeMessages(sessionId)` | 观察指定 `sessionId` 的消息 |
| `observeP2PMessages(peerId)` | 观察与指定用户的 P2P 消息 |
| `observeLastMessage(sessionId)` | 观察会话最新消息 |
| `deleteMessage(messageId)` | 仅删除当前账号的本地记录 |
| `getDeletedMessageCount(sessionId)` | 查询当前会话可恢复的手动删除消息数量 |
| `restoreDeletedMessages(sessionId)` | 恢复当前会话全部手动删除消息且不增加未读数 |
| `recallMessage(messageId)` | 请求服务端撤回，返回 `RecallResult` |

撤回规则：只有当前账号发送、已成功、未撤回且发送时间不超过两分钟的消息可撤回。`MsgType.CALL` 通话记录不能撤回。

### IMClient：会话、未读和用户

| API | 说明 |
|---|---|
| `getP2PConversation(peerId)` | 查询与指定用户的 P2P 会话 |
| `getConversations()` | 查询当前账号全部会话 |
| `observeConversations()` | 观察会话列表 |
| `observeTotalUnreadCount()` | 观察全部会话未读总数 |
| `addUnreadCountListener(listener)` | Java 未读监听，返回可取消订阅 |
| `markConversationRead(sessionId)` | 清空指定会话未读数 |
| `deleteConversation(sessionId)` | 删除当前账号的本地会话及其消息 |
| `getUserInfo(account)` | 内存、SQLite、Provider 三级用户资料查询 |
| `updateUserInfo(info)` | 更新单个用户资料 |
| `updateUserInfos(infos)` | 批量更新用户资料 |

底部消息 Tab 角标示例：

```kotlin
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        IMClient.observeTotalUnreadCount().collect { count ->
            if (count == 0) bottomNav.removeBadge(R.id.tab_chat)
            else bottomNav.getOrCreateBadge(R.id.tab_chat).apply {
                number = count
                maxCharacterCount = 3 // 超过 99 显示 99+
            }
        }
    }
}
```

### IMCall

| API / 属性 | 说明 |
|---|---|
| `init(context)` | 初始化通话控制器并订阅 `imcore` 信令 |
| `startAudioCall(context, peerId)` | 发起一对一语音通话 |
| `startVideoCall(context, peerId)` | 发起一对一视频通话 |
| `accept()` | 接听当前呼入 |
| `reject()` | 拒绝当前呼入 |
| `hangup()` | 取消呼出或挂断当前通话 |
| `session` | `StateFlow<CallSession?>` 当前通话状态 |

`CallPhase` 包括：`IDLE`、`OUTGOING`、`INCOMING`、`CONNECTING`、`CONNECTED`、`ENDED`。

### MessageBuilder

当前快捷构造函数包括：

- `createTextMessage(...)`
- `createImageMessage(...)`
- `createVideoMessage(...)`
- `createFileMessage(...)`
- `createRedPacketMessage(...)`
- `createCardMessage(...)`
- `createLocationMessage(...)`
- `createTipMessage(...)`
- `createForwardedMessage(...)`

通话记录不由客户端构造；服务端在通话结束时生成 `MsgType.CALL`，用 `callId` 保证一次通话只有一条记录。

## 引用、撤回和通话记录规则

| 消息类型 | 转发 | 引用 | 撤回 | 本地删除 |
|---|---:|---:|---:|---:|
| 普通文本/媒体 | 支持（取决于附件） | 支持 | 发送后两分钟内 | 支持 |
| 提示消息 | 按业务配置 | 按业务配置 | 通常不支持 | 支持 |
| 通话记录 `CALL` | 不支持 | 不支持 | 不支持 | 支持 |

引用信息保存在消息 `extra.quote` 中；撤回由服务端校验发送者、时间窗口和消息状态，并通过实时推送与增量同步保证双方一致。

## 自定义消息类型

### 1. 定义 Attachment

附件必须实现 `MsgAttachment`，并提供可由反射调用的字符串构造函数：

```kotlin
class GoodsAttachment(json: String? = null) : MsgAttachment {
    var goodsId = ""
    var title = ""

    init {
        if (!json.isNullOrBlank()) JSONObject(json).also {
            goodsId = it.optString("goodsId")
            title = it.optString("title")
        }
    }

    override fun getMsgType(): Int = 1001

    override fun toJson(send: Boolean): String = JSONObject()
        .put("goodsId", goodsId)
        .put("title", title)
        .toString()
}
```

### 2. 注册 SPI

创建：

```text
src/main/resources/META-INF/services/com.kora.imcore.attachment.MsgAttachment
```

写入附件完整类名：

```text
com.example.chat.GoodsAttachment
```

### 3. 注册气泡

```kotlin
class MsgGoodsViewHolder(itemView: View) : MsgViewHolderBase(itemView) {
    override fun getLayout(): Int = R.layout.item_msg_goods

    override fun bindViewHolder(view: View, message: IMMessage) {
        val attachment = message.getAttachment() as? GoodsAttachment ?: return
        view.findViewById<TextView>(R.id.title).text = attachment.title
    }
}

MsgViewHolderFactory.register(
    GoodsAttachment::class.java,
    MsgGoodsViewHolder::class.java
)
```

服务端的 `attachment` 和 `extra` 使用通用 JSON 文本存储，新增普通业务附件通常不需要修改数据库表结构。

## 启动联调服务端

```bash
cd im-server
npm install
npm start
```

默认端口由 `im-server/config.js` 配置。Android Demo 中的服务器 IP 必须是手机能够访问的局域网地址，不能在真机上使用 `127.0.0.1` 指向开发电脑。

服务端当前处理：

- 登录和在线连接路由
- 消息 ACK 与消息持久化
- 在线推送和离线增量同步
- `sync_ack` 游标确认
- 消息撤回校验与广播
- `call_signal` 通话信令、忙线和断线结束
- 服务端通话记录生成与双方同步

## 测试建议

至少使用两个账号、两台设备或模拟器测试：

1. 在线文本与媒体消息。
2. 接收方离线后发送消息，再上线验证增量同步。
3. 撤回成功、超时撤回失败和双方 UI 更新。
4. 引用发送、点击定位和被引用消息撤回。
5. 语音/视频接听、拒绝、取消、挂断、忙线和无人接听。
6. 通话记录方向、双方文案、时长及离线同步。
7. 未读总数、进入聊天清零和 `99+` 角标。

## License

KoraIM 使用 [Apache License 2.0](LICENSE)。

Copyright 2026 GodCodeApps
