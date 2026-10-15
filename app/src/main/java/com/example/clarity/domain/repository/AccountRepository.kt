package com.example.clarity.domain.repository

import com.example.clarity.domain.model.Account
import com.example.clarity.domain.util.Resource
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    /** Upload accounts before dependent transactions; invoke the callback only after acknowledgement. */
    suspend fun uploadPending(uid: String, afterAccounts: suspend () -> Unit = {}): Boolean
    fun observeAccounts(): Flow<Resource<List<Account>>>
    suspend fun createAccount(account: Account): Result<Unit>
    suspend fun updateAccount(account: Account): Result<Unit>
    suspend fun getAccount(id: String): Result<Account?>
    suspend fun getDefaultAccount(): Result<Account?>
    suspend fun setDefaultAccount(id: String): Result<Unit>
}
