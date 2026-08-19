package com.kora.imcore.provider

import com.kora.imcore.db.UserInfo

/**
 * 用户信息提供器接口，上层 App 实现此接口来为 IM SDK 提供用户资料。
 *
 * SDK 内部的 [UserRepository] 按以下顺序查找用户信息：
 * 内存缓存 → SQLite → [getUserInfo]（本地） → [fetchUserInfoFromServer]（远程）
 *
 * 使用方式：
 * ```kotlin
 * IMClient.userInfoProvider = object : IMUserInfoProvider {
 *     override fun getUserInfo(account: String): UserInfo? {
 *         // 从 App 的本地缓存/数据库查找
 *     }
 *     override fun fetchUserInfoFromServer(account: String, callback: (UserInfo?) -> Unit) {
 *         // 从 App 的服务端 API 拉取
 *         api.getUserProfile(account) { result ->
 *             callback(result?.toUserInfo())
 *         }
 *     }
 * }
 * ```
 */
interface IMUserInfoProvider {
    /**
     * 从本地获取用户信息（缓存或数据库）。
     * 如果本地不存在，返回 null，SDK 会调用 [fetchUserInfoFromServer] 远程拉取。
     */
    fun getUserInfo(account: String): UserInfo?

    /**
     * 从服务器异步拉取用户信息。
     * 拉取完成后调用 [callback] 返回结果，失败时传 null。
     * SDK 会自动将结果缓存到内存和数据库中。
     */
    fun fetchUserInfoFromServer(account: String, callback: (UserInfo?) -> Unit)
}
