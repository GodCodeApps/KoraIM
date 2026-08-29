package com.kora.imcore

sealed class RecallResult {
    data object Success : RecallResult()
    data class Failed(val code: String, val message: String) : RecallResult()
}
