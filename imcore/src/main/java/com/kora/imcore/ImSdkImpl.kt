package com.kora.imcore

import com.kora.imcore.attachment.MsgAttachment
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * IM SDK 全局配置持有者。
 *
 * 管理两项全局状态：
 * - **当前账号** [mAccount]：所有 IM 操作都基于此账号
 * - **自定义消息附件解析器** [msgAttachmentList]：通过 SPI（ServiceLoader）机制
 *   自动发现并加载 [MsgAttachment] 的实现类，支持扩展自定义消息类型
 *
 * 使用顺序：
 * ```kotlin
 * ImSdkImpl.setAccount("user123")  // 先设置账号
 * IMClient.init(context, host, port)  // 再初始化（内部会调用 ImSdkImpl.init()）
 * ```
 */
object ImSdkImpl {
    /** 当前登录账号（@Volatile 保证多线程可见性） */
    @Volatile
    private var mAccount: String? = null

    /** 自定义消息附件解析器列表（CopyOnWriteArrayList 保证并发安全） */
    private val msgAttachmentList = CopyOnWriteArrayList<MsgAttachment>()

    /**
     * 初始化附件解析器。
     * 通过 Java SPI 机制（ServiceLoader）自动扫描并加载所有 [MsgAttachment] 实现。
     * 上层模块只需在 META-INF/services 中声明实现类即可自动注册。
     */
    fun init() {
        msgAttachmentList.clear()
        val loader: ServiceLoader<MsgAttachment> = ServiceLoader.load(MsgAttachment::class.java)
        msgAttachmentList.addAll(loader.toMutableList())
    }

    /** 获取所有已注册的附件解析器 */
    fun getMsgAttachmentList(): List<MsgAttachment> = msgAttachmentList

    /** 设置当前登录账号（必须在 [IMClient.init] 之前调用） */
    fun setAccount(account: String?) {
        mAccount = account
    }

    /** 获取当前登录账号 */
    fun getAccount(): String? = mAccount

}
