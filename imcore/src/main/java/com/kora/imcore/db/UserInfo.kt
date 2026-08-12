package com.kora.imcore.db

data class UserInfo(
    val account: String,
    var nickname: String,
    var avatar: String,
    var updateTime: Long = 0L
)
