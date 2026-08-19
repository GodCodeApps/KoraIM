# KoraIM Server 服务端使用指南

KoraIM Server 是为 KoraIM Android 客户端量身定制的高性能 TCP 即时通讯服务端。采用 Node.js 原生底层网络库开发，支持 **NDJSON 通信协议**、**单调递增游标增量同步（Cursor Sync）** 以及 **SQLite / MySQL 双存储引擎无缝切换**。

---

## 目录
- [一、环境要求](#一样环境要求)
- [二、快速开始](#二快速开始)
- [三、配置文件说明 (config.json)](#三配置文件说明-configjson)
- [四、存储引擎切换与启动方式](#四存储引擎切换与启动方式)
  - [1. SQLite 模式（默认，零配置开箱即用）](#1-sqlite-模式默认零配置开箱即用)
  - [2. MySQL 模式（支持生产高并发与多节点）](#2-mysql-模式支持生产高并发与多节点)
  - [3. 环境变量一键覆盖（Docker / 云端部署）](#3-环境变量一键覆盖docker--云端部署)
- [五、数据持久化表结构](#五数据持久化表结构)
- [六、网络通信协议说明](#六网络通信协议说明)
- [七、自动化测试](#七自动化测试)

---

## 一、环境要求

- **Node.js**：`>= 16.0.0`
- **npm**：`>= 8.0.0`
- **（可选）MySQL**：`5.7+` 或 `8.0+`（仅在使用 MySQL 模式时需要）

---

## 二、快速开始

### 1. 进入服务端目录并安装依赖
```bash
cd im-server
npm install
```

### 2. 启动服务（默认使用 SQLite 本地数据库）
```bash
npm start
```
控制台输出如下即表示启动成功：
```text
[+] SQLite database initialized: .../im-server/kora_im.db
KoraIM Server running on port 8090 [Storage Engine: SQLITE]
```

---

## 三、配置文件说明 (config.json)

服务端核心配置文件位于 `im-server/config.json`：

```json
{
  "port": 8090,
  "dbType": "sqlite",
  "syncPageSize": 100,
  "sqlite": {
    "filename": "./kora_im.db"
  },
  "mysql": {
    "host": "127.0.0.1",
    "port": 3306,
    "user": "root",
    "password": "",
    "database": "kora_im",
    "connectionLimit": 10
  }
}
```

### 参数详解：
| 参数字段 | 类型 | 默认值 | 描述 |
| :--- | :--- | :--- | :--- |
| `port` | Number | `8090` | TCP 服务端监听端口 |
| `dbType` | String | `"sqlite"` | 数据库存储引擎，支持 `"sqlite"` 或 `"mysql"` |
| `syncPageSize` | Number | `100` | 客户端断线重连单次增量同步拉取的消息条数上限 |
| `sqlite.filename` | String | `"./kora_im.db"` | SQLite 本地数据库文件存放路径（自动开启 WAL 模式） |
| `mysql.host` | String | `"127.0.0.1"` | MySQL 服务端主机 IP / 域名 |
| `mysql.port` | Number | `3306` | MySQL 服务端连接端口 |
| `mysql.user` | String | `"root"` | MySQL 数据库登录用户名 |
| `mysql.password` | String | `""` | MySQL 数据库登录密码 |
| `mysql.database` | String | `"kora_im"` | 数据库名称（服务端启动时若不存在会自动创建） |
| `mysql.connectionLimit` | Number | `10` | 连接池最大连接数 |

---

## 四、存储引擎切换与启动方式

### 1. SQLite 模式（默认，零配置开箱即用）
- **特点**：无需安装 MySQL，单文件数据库，极度适合本地开发调试或单机轻量部署。
- **启动方法**：
  确保 `config.json` 中的 `"dbType": "sqlite"`，然后运行：
  ```bash
  npm start
  ```

---

### 2. MySQL 模式（支持生产高并发与多节点）
- **特点**：支持多台 IM Server 实例连接同一数据库、行级锁高并发写入与自动事务回滚。
- **启动步骤**：
  1. 确保已启动 MySQL 服务；
  2. 修改 `config.json`：
     ```json
     {
       "dbType": "mysql",
       "mysql": {
         "host": "127.0.0.1",
         "port": 3306,
         "user": "root",
         "password": "你的数据库密码",
         "database": "kora_im"
       }
     }
     ```
  3. 执行启动命令（**服务端会自动创建 `kora_im` 库与全部数据表**）：
     ```bash
     npm start
     ```

---

### 3. 环境变量一键覆盖（Docker / 云端部署）

所有配置项均支持通过环境变量直接覆盖，无需修改代码或文件：

#### 示例 1：指定端口启动
```bash
PORT=9090 npm start
```

#### 示例 2：一键切换为 MySQL 启动
- **Linux / macOS / Git Bash**：
  ```bash
  DB_TYPE=mysql MYSQL_HOST=127.0.0.1 MYSQL_USER=root MYSQL_PASSWORD=secret MYSQL_DATABASE=kora_im npm start
  ```
- **Windows PowerShell**：
  ```powershell
  $env:DB_TYPE="mysql"; $env:MYSQL_HOST="127.0.0.1"; $env:MYSQL_USER="root"; $env:MYSQL_PASSWORD="secret"; npm start
  ```

---

## 五、数据持久化表结构

服务端在启动时会自动校验并初始化以下 4 张核心数据表：

```text
┌─────────────────────────────────────────────────────────────┐
│ 1. sessions (会话表)                                         │
│    session_id (PK), user_a, user_b, last_message_id,        │
│    last_msg_preview, last_msg_time, updated_at              │
├─────────────────────────────────────────────────────────────┤
│ 2. messages (全局消息中心表)                                │
│    message_id (PK), session_id, sender_id, receiver_id,     │
│    msg_type, attachment (TEXT/JSON), extra, status, send_time│
├─────────────────────────────────────────────────────────────┤
│ 3. sync_events (收件箱同步流表)                              │
│    id (PK), user_id, cursor, event_type, message_id,        │
│    payload_json, created_at  [UNIQUE(user_id, cursor)]      │
├─────────────────────────────────────────────────────────────┤
│ 4. user_cursors (用户游标发号器表)                          │
│    user_id (PK), max_cursor, last_acked_cursor, updated_at  │
└─────────────────────────────────────────────────────────────┘
```

> **设计亮点**：消息正文 `attachment` 和 `extra` 采用通用 `TEXT` 存储。Android 客户端新增任何自定义消息类型（如红包、名片、位置、语音、视频等），**数据库和后端均无需修改任何表结构或代码**！

---

## 六、网络通信协议说明

传输格式为 **NDJSON（Newline-Delimited JSON）**，即每包 JSON 数据末尾以换行符 `\n` 结尾。

| 帧类型 (`type`) | 方向 | 核心字段 | 功能说明 |
| :--- | :--- | :--- | :--- |
| `login` | 客户端 ➔ 服务端 | `{ "type": "login", "account": "user123" }` | 用户上线并绑定长连接 Socket |
| `message` | 客户端 ➔ 服务端 | `{ "type": "message", "messageId": "...", "payload": { ... } }` | 发送即时聊天消息 |
| `ack` | 服务端 ➔ 客户端 | `{ "type": "ack", "messageId": "...", "success": true, "sessionId": "..." }` | 消息入库成功的确认回包 |
| `message` (推送) | 服务端 ➔ 接收方 | `{ "type": "message", "messageId": "...", "cursor": 12, "payload": { ... } }` | 接收方在线时的实时推送 |
| `sync` | 客户端 ➔ 服务端 | `{ "type": "sync", "cursor": 5 }` | 断线重连后请求拉取大于游标的增量离线消息 |
| `sync_result` | 服务端 ➔ 客户端 | `{ "type": "sync_result", "events": [...], "nextCursor": 8, "hasMore": false }` | 下发离线增量消息列表 |
| `sync_ack` | 客户端 ➔ 服务端 | `{ "type": "sync_ack", "cursor": 8 }` | 客户端确认已成功消费该游标 |
| `ping` / `pong` | 双向 | `{ "type": "ping" }` / `{ "type": "pong" }` | 链路保活心跳 |

---

## 七、自动化测试

工程内置了端到端全链路自动化测试套件（模拟客户端连接、离线发送、增量游标同步、在线推送）：

```bash
# 运行自动化测试
npm test
```

测试输出：
```text
=== Starting IM Server Persistence & Sync Tests ===
[Step 1] Alice connects and sends an offline message to Bob...
  -> Alice received ACK
[Step 2] Bob connects and syncs offline messages (cursor=0)...
  -> Bob received sync_result with 1 offline event
[Step 3] Bob sends sync_ack...
[Step 4] Alice sends a live message to online Bob...
  -> Bob received real-time pushed message
=== All Tests Passed Successfully! ===
```

---

## 八、数据库一键重置与清空

在联调或需要清空历史消息重新测试时，可执行以下命令一键重置数据库：

```bash
npm run clean-db
# 或者
npm run clear-db
```

- **SQLite 模式**：自动清空 `sessions`、`messages`、`sync_events`、`user_cursors` 数据表并释放文件空间。
- **MySQL 模式**：自动连接当前配置的 MySQL 数据库，截断（`TRUNCATE`）并重置所有数据表。

