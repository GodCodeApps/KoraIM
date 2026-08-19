package com.kora.imcore.event

/**
 * TCP 连接状态的密封接口，表示连接生命周期中的所有可能状态。
 *
 * 状态流转：
 * ```
 * Disconnected → Connecting → Connected
 *                     ↓            ↓
 *                   Failed    Reconnecting → Connecting → ...
 *                     ↓            ↓
 *                   （达到上限）  Failed
 * ```
 *
 * 通过 [IMEventHub.connectionState] 以 StateFlow 暴露给 UI 层。
 */
sealed interface ConnectionState {
    /** 已断开（初始状态/主动断开/网络丢失） */
    data object Disconnected : ConnectionState

    /** 正在连接中（首次连接或重连发起时） */
    data object Connecting : ConnectionState

    /**
     * 正在等待重连。
     * @param attempt 当前是第几次重连尝试
     * @param delaySeconds 本次重连的等待延迟（秒）
     */
    data class Reconnecting(val attempt: Int, val delaySeconds: Long) : ConnectionState

    /** 已成功连接到服务器 */
    data class Connected(val host: String, val port: Int) : ConnectionState

    /**
     * 连接失败（含失败原因）。
     * 可能的原因：连接超时、达到最大重连次数、网络异常等。
     */
    data class Failed(val reason: String) : ConnectionState
}
