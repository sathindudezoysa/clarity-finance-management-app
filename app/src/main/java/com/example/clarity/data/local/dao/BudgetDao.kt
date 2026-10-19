package com.example.clarity.data.local.dao

import androidx.room.*
import com.example.clarity.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
abstract class BudgetDao {
    @Query("SELECT * FROM budgets WHERE userId = :uid ORDER BY month DESC, category")
    abstract fun observe(uid: String): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budgets WHERE userId = :uid AND id = :id")
    abstract suspend fun get(uid: String, id: String): BudgetEntity?
    @Query("SELECT * FROM budgets WHERE userId = :uid AND pendingSync = 1")
    abstract suspend fun pending(uid: String): List<BudgetEntity>
    @Upsert abstract suspend fun persist(row: BudgetEntity)
    // Every accepted write (local, reconciliation or upload acknowledgement) passes through here.
    // Level zero invalidates old delivery history and asks evaluation to cancel old notification content.
    @Transaction
    open suspend fun upsert(row: BudgetEntity) {
        val old = get(row.userId, row.id)
        if (old != null && old.limitMinor != row.limitMinor) putAlert(BudgetAlertEntity(row.userId, row.id, 0))
        persist(row)
    }
    @Upsert abstract suspend fun resolve(row: BudgetResolutionEntity)
    @Query("SELECT * FROM budget_resolutions WHERE userId = :uid")
    abstract fun resolutions(uid: String): Flow<List<BudgetResolutionEntity>>
    @Query("SELECT * FROM budget_alerts WHERE userId = :uid AND budgetId = :id")
    abstract suspend fun alert(uid: String, id: String): BudgetAlertEntity?
    @Upsert abstract suspend fun putAlert(row: BudgetAlertEntity)
    @Transaction
    open suspend fun claimAlert(uid: String, id: String, level: Int, expectedVersion: Long, expectedLimit: Long): Boolean {
        val budget = get(uid, id) ?: return false
        if (!budget.active || budget.updatedAt != expectedVersion || budget.limitMinor != expectedLimit) return false
        if (level <= (alert(uid, id)?.level ?: 0)) return false
        putAlert(BudgetAlertEntity(uid, id, level))
        return true
    }
}
