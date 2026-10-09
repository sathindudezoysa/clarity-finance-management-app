package com.example.clarity.domain.repository

import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.util.Resource
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun observeTransactions(): Flow<Resource<List<Transaction>>>
    fun observeTransaction(id: String): Flow<Resource<Transaction?>>
    suspend fun addTransaction(t: Transaction): Result<Unit>
    suspend fun updateTransaction(t: Transaction): Result<Unit>
    suspend fun deleteTransaction(id: String): Result<Unit>
}
