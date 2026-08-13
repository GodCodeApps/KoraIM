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

internal class UserRepository(
    private val dao: UserDao,
    private val scope: CoroutineScope
) {
    private val cache = ConcurrentHashMap<String, UserInfo>()
    var provider: IMUserInfoProvider? = null

    suspend fun get(account: String?): UserInfo? {
        if (account.isNullOrBlank()) return null
        cache[account]?.let { return it }
        val source = provider
        val databaseInfo = withContext(Dispatchers.IO) { dao.getUserInfo(account) }
        if (source == null) return databaseInfo?.also { cache[account] = it }
        val local = databaseInfo ?: withContext(Dispatchers.IO) { source.getUserInfo(account) }
        if (local != null) return local.also { cacheAndPersist(it) }
        return suspendCancellableCoroutine { continuation ->
            source.fetchUserInfoFromServer(account) { fetched ->
                if (continuation.isActive) continuation.resume(fetched)
                fetched?.let(::cacheAndPersistAsync)
            }
        }
    }

    private suspend fun cacheAndPersist(info: UserInfo) {
        cache[info.account] = info
        dao.insertOrUpdateUserInfo(info)
    }

    private fun cacheAndPersistAsync(info: UserInfo) {
        cache[info.account] = info
        scope.launch { dao.insertOrUpdateUserInfo(info) }
    }

    fun clear() = cache.clear()
}
