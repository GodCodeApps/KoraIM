package com.kora.imcore

import com.kora.imcore.db.Message
import com.kora.imcore.event.IMEventHub
import com.kora.imcore.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.kora.imcore.netty.SyncEvent
import android.util.Log

/**
 * IM 运行时单例，作为 Netty 网络层和本地数据层之间的桥梁。
 *
 * 职责：
 * - 持有当前账号的协程作用域 [scope]（基于 [SupervisorJob]，子任务失败不影响其他）
 * - 接收网络层的消息事件，异步持久化到数据库并通知 UI
 * - 管理增量同步游标 [syncCursor]
 *
 * 生命周期：
 * - [IMClient.init] 时设置 messages、ownerId、syncCursor
 * - [IMClient.release] 时调用 [reset] 取消旧协程并重建 scope
 */
internal object IMRuntime {
    private var job = SupervisorJob()

    /** 协程作用域，所有消息处理任务在此 scope 中执行（IO 线程） */
    var scope = CoroutineScope(job + Dispatchers.IO)
        private set

    /** 消息仓库，提供消息的 CRUD 操作 */
    lateinit var messages: MessageRepository

    /** 当前登录账号 ID */
    var ownerId: String = ""

    /** 增量同步游标，记录已同步到的位置，断线重连后从此处继续同步 */
    @Volatile var syncCursor: Long = 0L

    /**
     * 处理收到的新消息（来自服务端推送或同步）。
     * - 如果消息带有 sessionId → 调用 confirm 更新本地已有记录（或插入新记录）
     * - 否则 → 直接插入
     * - 最后通过 [IMEventHub] 通知 UI 层
     */
    fun incoming(message: Message) {
        scope.launch {
            if (message.sessionId.isNotBlank()) messages.confirm(message, ownerId)
            else messages.upsert(message)
            IMEventHub.emitIncoming(message)
        }
    }

    /**
     * 处理消息状态更新（发送中/成功/失败的 ACK 回调或本地状态变更）。
     * - 如果消息带有 sessionId → 调用 confirm 更新消息状态并同步更新会话列表（包括失败状态与消息类型）
     * - 否则 → 直接更新消息表
     * - 最后通过 [IMEventHub] 通知 UI 层刷新消息气泡状态
     */
    fun updated(message: Message) {
        scope.launch {
            if (message.sessionId.isNotBlank()) {
                messages.confirm(message, ownerId)
            } else {
                messages.upsert(message)
            }
            IMEventHub.emitUpdate(message)
        }
    }

    fun recalled(message: Message) {
        scope.launch {
            messages.markRecalled(message, ownerId)
            IMEventHub.emitUpdate(message)
        }
    }

    /**
     * 处理增量同步响应。
     * 1. 将同步事件批量写入数据库
     * 2. 更新同步游标
     * 3. 通知 UI 层新消息
     * 4. 调用 [onCommitted] 回调（回复 sync_ack + 继续拉取）
     */
    fun synced(events: List<SyncEvent>, cursor: Long, onCommitted: () -> Unit) {
        scope.launch {
            Log.i("KoraIM_Sync", "applyStart owner=$ownerId cursor=$cursor events=${events.size}")
            try {
                messages.applySync(ownerId, events, cursor)
                syncCursor = cursor
                events.forEach { event ->
                    event.payload?.let {
                        if (event.eventType == "recall") IMEventHub.emitUpdate(it) else IMEventHub.emitIncoming(it)
                    }
                }
                Log.i("KoraIM_Sync", "applySuccess owner=$ownerId cursor=$cursor events=${events.size}")
                onCommitted()
            } catch (error: Throwable) {
                Log.e(
                    "KoraIM_Sync",
                    "applyFailed owner=$ownerId cursor=$cursor events=${events.size} " +
                        "items=${events.joinToString(limit = 20) { "${it.eventType}:${it.payload?.messageId.orEmpty()}@${it.cursor}" }}",
                    error
                )
            }
        }
    }

    /**
     * 重置运行时状态。
     * 取消旧的协程作用域（确保旧账号的异步任务不再执行），
     * 然后创建新的 scope，防止快速切换账号时协程泄漏到错误的上下文中。
     */
    fun reset() {
        job.cancel()
        job = SupervisorJob()
        scope = CoroutineScope(job + Dispatchers.IO)
        ownerId = ""
        syncCursor = 0L
    }
}
