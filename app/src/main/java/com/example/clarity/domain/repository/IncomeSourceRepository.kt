package com.example.clarity.domain.repository

import com.example.clarity.domain.model.IncomeSources
import com.example.clarity.domain.model.IncomeSource
import kotlinx.coroutines.flow.Flow

interface IncomeSourceRepository {
    fun observeSources(): Flow<IncomeSources>
    suspend fun save(source: IncomeSource): Result<Unit>
    suspend fun archive(id: String): Result<Unit>
    suspend fun record(id: String, month: String?, expectedVersion: Long): Result<String>
}
