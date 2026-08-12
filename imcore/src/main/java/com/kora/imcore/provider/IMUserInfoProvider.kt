package com.kora.imcore.provider

import com.kora.imcore.db.UserInfo

/**
 * Interface to provide user profile information to the IM Core.
 * The App layer must implement this and supply it to IMClient.
 */
interface IMUserInfoProvider {
    /**
     * Retrieve UserInfo from cache or database.
     * Return null if it does not exist locally.
     */
    fun getUserInfo(account: String): UserInfo?

    /**
     * Fetch UserInfo from the server asynchronously.
     * The callback should be invoked with the fetched UserInfo, or null if failed.
     */
    fun fetchUserInfoFromServer(account: String, callback: (UserInfo?) -> Unit)
}
