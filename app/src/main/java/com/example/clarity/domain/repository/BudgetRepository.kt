package com.example.clarity.domain.repository

import com.example.clarity.domain.model.Budget
import com.example.clarity.domain.model.BudgetStatus
import kotlinx.coroutines.flow.Flow

data class BudgetOverview(val ownerId: String? = null, val statuses: List<BudgetStatus> = emptyList(), val syncError: Boolean = false,
    val loading: Boolean = false)

interface BudgetRepository {
    fun observe(): Flow<BudgetOverview>
    suspend fun save(budget: Budget): Result<Unit>
    suspend fun disable(id: String): Result<Unit>
    suspend fun resolveUncounted(id: String): Result<Unit>
}
