package com.kora.imcore.repository

import com.kora.imcore.db.UserDao
import com.kora.imcore.db.UserInfo
import com.kora.imcore.provider.IMUserInfoProvider
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 用户信息仓库，提供三级查找策略：
 *
 * 1. **内存缓存**（[cache]）→ 命中则直接返回，最快
 * 2. **本地数据库**（[dao]）→ 查 SQLite 中的 user_info 表
 * 3. **远程拉取**（[provider]）→ 通过上层 App 实现的 [IMUserInfoProvider] 从服务器获取
 *
 * 查找到的结果会自动回写到缓存和数据库，后续查询直接命中缓存。
 *
 * 线程安全：缓存使用 [ConcurrentHashMap]，数据库操作在 [Dispatchers.IO] 上执行。
 */
internal class UserRepository(
    private val dao: UserDao,
    private val scope: CoroutineScope
) {
    /** 内存缓存：account → UserInfo */
    private val cache = ConcurrentHashMap<String, UserInfo>()

    /** 上层 App 提供的用户信息解析器（可选） */
    var provider: IMUserInfoProvider? = null

    /**
     * 获取用户信息（三级查找）。
     *
     * 查找顺序：内存缓存 → 数据库 → Provider 本地 → Provider 远程
     * 远程拉取使用 [suspendCancellableCoroutine] 将回调转为挂起函数。
     */
    suspend fun get(account: String?): UserInfo? {
        if (account.isNullOrBlank()) return null
        // 1. 先查内存缓存
        cache[account]?.let { return it }
        val source = provider
        // 2. 查数据库
        val databaseInfo = withContext(Dispatchers.IO) { dao.getUserInfo(account) }
        if (source == null) return databaseInfo?.also { cache[account] = it }
        // 3. 查 Provider 本地缓存
        val local = databaseInfo ?: withContext(Dispatchers.IO) { source.getUserInfo(account) }
        if (local != null) return local.also { cacheAndPersist(it) }
        // 4. 从服务器远程拉取（回调转协程）
        return suspendCancellableCoroutine { continuation ->
            source.fetchUserInfoFromServer(account) { fetched ->
                if (continuation.isActive) continuation.resume(fetched)
                fetched?.let(::cacheAndPersistAsync)
            }
        }
    }

    /** 同步写入缓存和数据库 */
    private suspend fun cacheAndPersist(info: UserInfo) {
        cache[info.account] = info
        dao.insertOrUpdateUserInfo(info)
    }

    /** 异步写入缓存和数据库（用于回调场景，不阻塞当前流程） */
    private fun cacheAndPersistAsync(info: UserInfo) {
        cache[info.account] = info
        scope.launch { dao.insertOrUpdateUserInfo(info) }
    }

    /** 清空内存缓存（账号切换时调用） */
    fun clear() = cache.clear()
}
