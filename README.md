# ZCHD-Component

Android组件仓库项目

#### im - UI框架

#### imCore - im核心（websocket层,db层）

## 初始化启动服务
    IMClient.init(context: Context, host: String, port: Int)

## 设置当前用户id

     ImSdkImpl.setAccount("test1")

## 设置当前用户id

     ImSdkImpl.setAccount("test1")

## 新增消息类型
     
     MsgViewHolderFactory.register(ImageAttachment::class.java,MsgImageViewHolder::class.java)
     com.kora.imcore.attachment.MsgAttachment下添加新的Attachment

## 消息数据结构说明

为了方便扩展和直观查阅，现将基础消息和目前支持的几类消息体（Text、Image）的数据结构梳理如下：

### 1. 基础消息结构 (Message/IMMessage)

所有消息最外层都遵循统一的基础数据库表/实体结构 `Message`。其中 `attachment` 字段用来存储各个具体消息类型的 JSON 字符串。

| 字段名 | 类型 | 说明 | 示例 |
| :--- | :--- | :--- | :--- |
| **id** | Long | 本地数据库自增主键 | `1`, `2` |
| **messageId** | String | 消息的全局唯一 ID (UUID) | `"56c4d7e2-..."` |
| **sessionType** | Int | 会话类型 (单聊/群聊等) | `1` (SessionType.None) |
| **sessionId** | String | 会话标识ID | `"session123456789"` |
| **account** | String | 发送者的账号 ID | `"test2"`, `"server_bot"` |
| **type** | Int | 消息具体类型 (文本/图片/视频等) | `0` (文本), `1` (图片) |
| **direct** | Int | 消息方向 (发送还是接收) | `0` (MsgDirection.OUT), `1` (MsgDirection.IN) |
| **status** | Int | 消息状态 (发送中/成功/失败) | `1` (MsgStatus.SUCCESS) |
| **time** | Long | 消息发送的时间戳 (ms) | `1786544136262` |
| **attachment** | String | **[核心]** 附带的消息内容体（JSON格式）| `{"content":"你好"}` 或 `{"path":"/xx","width":100,"height":100}` |
| **extra** | String | 扩展预留字段 | `""` |

---

### 2. 文本消息结构 (TextAttachment)
对应 `type = MsgType.TEXT`。

| JSON 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| **content** | String | 文本消息的具体内容 |

**`attachment` 存储示例：**
```json
{"content":"那你弄"}
```

---

### 3. 图片消息结构 (ImageAttachment)
对应 `type = MsgType.IMAGE`。

| JSON 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| **path** | String | 图片的本地绝对路径或网络 URL |
| **width** | Int | 图片的原始宽度 (像素) |
| **height** | Int | 图片的原始高度 (像素) |

**`attachment` 存储示例：**
```json
{
  "path": "/storage/emulated/0/DCIM/Camera/ECommerce1786072459187.jpeg",
  "width": 720,
  "height": 1280
}
```
