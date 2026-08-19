package com.kora.imcore.event

import com.kora.imcore.db.Message
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * IM 内部事件总线，集中管理所有需要跨层传递的实时事件。
 *
 * 提供三个事件流：
 * - [incomingMessages]：新收到的消息（推送/同步），UI 层用来刷新聊天列表
 * - [messageUpdates]：消息状态变更（发送成功/失败），UI 层用来更新消息气泡
 * - [connectionState]：TCP 连接状态变更，UI 层用来显示连接指示器
 *
 * 设计要点：
 * - 消息流使用 [MutableSharedFlow]，设置 64 缓冲区，满时丢弃最旧的（防背压阻塞 Netty 线程）
 * - 连接状态使用 [MutableStateFlow]，新订阅者立即收到当前状态
 * - 内部 internal，外部统一通过 [IMClient] 访问
 */
internal object IMEventHub {
    /** 新收到的消息流（缓冲 64 条，满时丢弃最旧的） */
    private val _incomingMessages = MutableSharedFlow<Message>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** 消息状态更新流（缓冲 64 条，满时丢弃最旧的） */
    private val _messageUpdates = MutableSharedFlow<Message>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** 连接状态流，初始为 Disconnected */
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    val incomingMessages = _incomingMessages.asSharedFlow()
    val messageUpdates = _messageUpdates.asSharedFlow()
    val connectionState = _connectionState.asStateFlow()

    /** 发射新消息事件（由 [IMRuntime.incoming] 调用） */
    fun emitIncoming(message: Message) { _incomingMessages.tryEmit(message) }

    /** 发射消息更新事件（由 [IMRuntime.updated] 调用） */
    fun emitUpdate(message: Message) { _messageUpdates.tryEmit(message) }

    /** 更新连接状态（由 [IMService] 和 [ImServiceProxy] 调用） */
    fun setConnectionState(state: ConnectionState) { _connectionState.value = state }
}
