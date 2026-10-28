package com.example.clarity.domain.repository

import com.example.clarity.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

interface TransactionTagRepository {
    fun observeTags(): Flow<List<String>>

    fun observeTagsByType(type: TransactionType): Flow<List<String>> = observeTags()

    suspend fun addCustomTag(name: String, type: TransactionType): Result<Unit> = Result.success(Unit)
}
