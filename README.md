# KoraIM

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-purple.svg)](https://kotlinlang.org)

**KoraIM** 是由 **GodCodeApps** 开源的轻量级、高性能、易扩展的 Android 即时通讯（IM）SDK 框架。架构设计上实现了 **通信内核（imcore）** 与 **UI 组件（imui）** 的彻底解耦，帮助 Android 开发者在 **5 分钟内快速搭建具备微信级交互体验的聊天系统**。

---

## 🌟 核心特性

- 🚀 **极简集成**：只需两步初始化即可拥有完整的收发消息、离线存储、会话管理与全套 UI。
- 💬 **微信级交互体验**：
  - **无缝软键盘/面板切换**：动态记忆真实软键盘高度，杜绝切换表情/更多面板时的闪烁跳动与多层堆叠。
  - **录音 HUD 实时波形**：按住说话弹出半透明 HUD，支持 6 级麦克风实时音量跳动波形与上滑取消视觉反馈。
  - **富媒体气泡支持**：内置文本、Emoji、图片自适应宽高比、语音动态宽度、视频弹窗播放、3D 翻转拆红包、居中提示消息。
  - **网络状态条**：会话列表顶部展示类似微信的网络连接状态栏（连接中/重连中/断开警告），支持一键跳转网络设置。
- 🛡️ **高可靠通信引擎**：
  - **TCP 长连接与应用层心跳保活**：定时保活监测，防止 NAT 超时断连。
  - **智能重连机制**：具备指数退避策略与网络状态变化感知，断网恢复秒级重连。
  - **端到端 ACK 可靠确认**：严格的消息 ID 追踪，发送超时自动标记为失败，支持一键重发。
- 🔄 **全响应式数据流**：基于 SQLite + Kotlin Coroutines & StateFlow，底层数据变更自动驱动 UI 秒级响应刷新。
- 🧩 **高可扩展 SPI 设计**：多媒体文件上传采用 SPI 抽象，无缝对接任意 OSS、S3 或自建文件服务器；支持 3 步注册自定义消息卡片。

---

## 📦 模块分层

```
KoraIM
├── imcore        # 【核心通信库】TCP 长连接管理、心跳保活、ACK 确认、SQLite 本地持久化、响应式数据流
├── imui          # 【UI 组件库】微信交互风格输入框、消息气泡列表、录音 HUD、会话列表、开箱即用 Base Fragment
├── app           # 【接入示例 Demo】包含单聊会话、消息列表、多媒体发送的完整实现演示
└── server        # 【本地联调服务】基于 Node.js 实现的轻量 TCP 示例服务端（仅供本地开发联调）
```

---

## 🚀 5 分钟快速接入教程

### 1. 声明权限与 Activity 配置

在你的 `AndroidManifest.xml` 中添加网络与多媒体所需权限，并务必为聊天 Activity 添加 `windowSoftInputMode="adjustResize"`：

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- 基础网络权限 -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    
    <!-- 语音与多媒体权限 -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    <!-- Android 12 及以下读写存储权限 -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />

    <application ...>
        <!-- 聊天页面配置 adjustResize 确保键盘与输入面板无缝切换 -->
        <activity
            android:name=".ChatActivity"
            android:windowSoftInputMode="adjustResize" />
    </application>
</manifest>
```

### 2. Application 中初始化 SDK

在 `Application.onCreate` 中初始化 `IMClient` 并配置媒体上传 Provider（如 OSS/COS/自建文件服务器）：

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // 1. 初始化 IM 核心引擎 (配置服务器 IP 和端口)
        IMClient.init(this, host = "192.168.1.100", port = 8090)

        // 2. 配置多媒体文件上传 Provider (SPI 插件化设计)
        ImUIKit.setMediaMessageProvider(object : IMMediaMessageProvider {
            override suspend fun uploadImage(request: ImageUploadRequest): ImageUploadResult {
                val remoteUrl = uploadToOss(request.localPath)
                return ImageUploadResult(remoteUrl)
            }

            override suspend fun uploadVideo(request: VideoUploadRequest): VideoUploadResult {
                val videoUrl = uploadToOss(request.localVideoPath)
                val coverUrl = uploadToOss(request.localCoverPath)
                return VideoUploadResult(videoUrl, coverUrl)
            }

            override suspend fun uploadVoice(request: VoiceUploadRequest): VoiceUploadResult {
                val voiceUrl = uploadToOss(request.localPath)
                return VoiceUploadResult(voiceUrl)
            }
        })
    }
}
```

### 3. 会话列表接入 (`IConversationListFragment`)

继承 `IConversationListFragment`，重写 3 个简单方法即可展示带有未读红点、失败标记的会话列表：

```kotlin
class MyConversationListFragment : IConversationListFragment() {

    // 解析会话标题（例如从你本地的用户资料缓存库查询昵称）
    override fun resolveTitle(conversation: Conversation): String {
        return "用户 ${conversation.sessionId}"
    }

    // 解析会话头像 URL
    override fun resolveAvatar(conversation: Conversation): String {
        return "https://example.com/avatar/${conversation.sessionId}.png"
    }

    // 点击会话项跳转到聊天页面
    override fun onConversationClick(item: ConversationListItem) {
        val intent = Intent(requireContext(), ChatActivity::class.java).apply {
            putExtra("session_type", item.conversation.sessionType)
            putExtra("session_id", item.conversation.sessionId)
            putExtra("peer_id", item.conversation.sessionId)
        }
        startActivity(intent)
    }
}
```

### 4. 聊天页面接入 (`IMessageFragment`)

在你的 `ChatActivity` 中挂载继承自 `IMessageFragment` 的 Fragment：

```kotlin
class MyChatFragment : IMessageFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 设置顶部聊天标题
        setTitle("与 $peerId 的聊天")
        
        // 监听返回按钮
        onBackClick { activity?.finish() }
    }
}
```

---

## 📖 核心 API 说明

### 1. 通信与连接控制 (`IMClient`)

| API 方法 | 作用说明 |
|:---|:---|
| `IMClient.init(context, host, port)` | 初始化 SDK 并建立 TCP 长连接与心跳机制 |
| `IMClient.login(account, token)` | 切换账号登录 |
| `IMClient.logout()` | 退出当前登录并断开长连接 |
| `IMClient.release()` | 彻底释放网络资源与数据库连接 |
| `IMClient.connectionState` | `StateFlow<ConnectionState>` 实时监听网络连接状态 (`Connected`, `Connecting`, `Reconnecting`, `Disconnected`) |

### 2. 消息操作与响应式监听 (`IMClient`)

| API 方法 | 作用说明 |
|:---|:---|
| `IMClient.sendMessage(message)` | 发送消息（自动持久化、更新会话并等待服务端 ACK，超时自动标记失败） |
| `IMClient.saveMessage(message)` | 本地插入或更新消息实体 |
| `IMClient.observeMessages(sessionId)` | 响应式监听某个会话的全量消息列表 Flow（用于 UI 自动刷新） |
| `IMClient.observeP2PMessages(peerId)` | 响应式监听与指定用户的点对点消息 Flow |
| `IMClient.markAsRead(sessionId, type)` | 标记某个会话的消息为已读，清除未读角标 |
| `IMClient.deleteMessage(messageId)` | 删除单条消息 |

### 3. 会话与未读数统计 (`IMClient`)

| API 方法 | 作用说明 |
|:---|:---|
| `IMClient.observeConversations()` | 响应式监听所有会话列表 Flow（按最后一条消息时间降序） |
| `IMClient.observeUnreadTotal()` | 响应式监听全局总未读数 Flow（用于 App 底部 Tab 栏红点角标） |
| `IMClient.deleteConversation(sessionId, type)` | 删除指定会话及其本地聊天记录 |

### 4. 快捷消息工厂 (`MessageBuilder`)

`MessageBuilder` 提供便捷的方法构造开箱即用的消息实体：
- `MessageBuilder.createTextMessage(sessionId, sessionType, msg = "你好")`
- `MessageBuilder.createImageMessage(sessionId, sessionType, localPath = "...", ...)`
- `MessageBuilder.createVoiceMessage(sessionId, sessionType, localPath = "...", duration = 5)`
- `MessageBuilder.createVideoMessage(sessionId, sessionType, localVideoPath = "...", ...)`
- `MessageBuilder.createRedPacketMessage(sessionId, sessionType, id = "...", greetings = "恭喜发财")`
- `MessageBuilder.createTipMessage(sessionId, sessionType, tip = "你领取了对方的红包")`

---

## 🎨 3 步扩展自定义业务消息气泡

如果需要扩展例如“商品卡片”、“优惠券”等自定义消息类型，无需修改 SDK 源码，只需简单 3 步：

### 第 1 步：定义自定义 Attachment 实体
```kotlin
data class GoodsAttachment(
    var goodsId: String = "",
    var goodsName: String = "",
    var price: String = "",
    var imageUrl: String = ""
) : MsgAttachment {
    constructor(json: String) : this() {
        // 从 json 反序列化
    }
    override fun toJson(forNetwork: Boolean): String = Gson().toJson(this)
    override fun getMsgType(): Int = 1001 // 自定义消息 Type (> 1000)
}
```

### 第 2 步：编写自定义气泡 ViewHolder
```kotlin
class MsgGoodsViewHolder(itemView: View) : MsgViewHolderBase(itemView) {
    override fun getLayout(): Int = R.layout.item_msg_goods_card

    override fun bindViewHolder(view: View, message: IMMessage) {
        val goods = message.getAttachment() as GoodsAttachment
        view.findViewById<TextView>(R.id.tv_goods_name).text = goods.goodsName
        view.findViewById<TextView>(R.id.tv_goods_price).text = "¥${goods.price}"
        Glide.with(view).load(goods.imageUrl).into(view.findViewById(R.id.iv_goods_pic))
    }
}
```

### 第 3 步：注册到气泡工厂
```kotlin
MsgViewHolderFactory.register(GoodsAttachment::class.java, MsgGoodsViewHolder::class.java)
```

---

## 🛠️ 本地联调服务端

工程自带了一个用于本地联调的 Node.js TCP 演示服务端：

```bash
# 进入服务端目录
cd server

# 启动本地 TCP 消息中继服务 (默认监听 8090 端口)
npm start
```

---

## 📄 开源许可证

KoraIM 基于 **[Apache License 2.0](LICENSE)** 协议开源。欢迎提交 Issue 与 Pull Request！

Copyright 2026 GodCodeApps
