package com.example.clarity.data.sync

/** Data-layer ordering boundary for uploads that depend on acknowledged accounts. */
internal interface AccountUploader {
    suspend fun uploadPending(uid: String, afterAccounts: suspend () -> Unit = {}): Boolean
}
