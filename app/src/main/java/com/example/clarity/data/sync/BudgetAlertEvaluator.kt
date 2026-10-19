package com.example.clarity.data.sync

import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.domain.model.BudgetPolicy
import com.example.clarity.domain.repository.*
import kotlinx.coroutines.flow.first
import java.time.Clock
import javax.inject.Inject

class BudgetAlertEvaluator @Inject constructor(
    private val db: ClarityDatabase, private val auth: AuthRepository,
    private val notifications: BudgetNotificationDelivery, private val clock: Clock
) {
    suspend fun evaluate(overview: BudgetOverview) {
        val owner = overview.ownerId ?: return
        if (auth.currentUser.first()?.uid != owner) return
        val month = BudgetPolicy.currentMonth(clock)
        overview.statuses.forEach { status ->
            val budget = status.budget
            if (budget.userId != owner || auth.currentUser.first()?.uid != owner) return
            if (!budget.active || budget.month != month || status.threshold.level == 0) {
                notifications.cancel(owner, budget.id)
            } else {
                val level = BudgetPolicy.nextAlert(db.budgetDao.alert(owner, budget.id)?.level ?: 0, status)
                if (level != null && notifications.available() && db.budgetDao.claimAlert(owner, budget.id, level, budget.updatedAt)) {
                    // Recheck after the suspending atomic claim, before publishing any private data.
                    if (auth.currentUser.first()?.uid == owner && BudgetPolicy.currentMonth(clock) == budget.month) notifications.deliver(status)
                }
            }
        }
    }
}
