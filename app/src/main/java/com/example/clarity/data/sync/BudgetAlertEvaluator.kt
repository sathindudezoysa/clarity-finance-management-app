package com.example.clarity.data.sync

import com.example.clarity.data.local.dao.BudgetDao
import com.example.clarity.domain.model.Budget
import com.example.clarity.domain.model.BudgetPolicy
import com.example.clarity.domain.repository.*
import kotlinx.coroutines.flow.first
import java.time.Clock
import javax.inject.Inject

class BudgetAlertEvaluator @Inject constructor(
    private val dao: BudgetDao, private val auth: AuthRepository,
    private val notifications: BudgetNotificationDelivery, private val clock: Clock
) {
    suspend fun evaluate(overview: BudgetOverview) {
        val owner = overview.ownerId ?: return
        if (auth.currentUser.first()?.uid != owner) return
        overview.statuses.forEach { status ->
            val budget = status.budget
            if (budget.userId != owner || auth.currentUser.first()?.uid != owner) return
            if (!isCurrent(budget)) return@forEach
            val activePeriod = BudgetPolicy.activePeriodKey(budget, clock)
            if (!budget.active || status.activePeriod != activePeriod || status.threshold.level == 0) {
                notifications.cancel(owner, budget.id)
            } else {
                val alertKey = BudgetPolicy.alertKey(budget, activePeriod)
                val history = dao.alert(owner, alertKey)
                if (history?.level == 0 && isCurrent(budget)) notifications.cancel(owner, budget.id)
                if (!notifications.available()) return@forEach
                val level = BudgetPolicy.nextAlert(history?.level ?: 0, status)
                val claimed = level != null && dao.claimAlert(owner, alertKey, level, budget.updatedAt, budget.limitMinor)
                // The claim checks the version and limit inside its Room transaction. Recheck after
                // suspension so a stale evaluation neither publishes nor refreshes newer content.
                if (auth.currentUser.first()?.uid != owner) return
                if (!isCurrent(budget) || status.activePeriod != BudgetPolicy.activePeriodKey(budget, clock)) return@forEach
                if (claimed) notifications.deliver(status) else notifications.refreshVisible(status)
            }
        }
    }
    private suspend fun isCurrent(budget: Budget): Boolean = dao.get(budget.userId, budget.id)?.let {
        it.updatedAt == budget.updatedAt && it.limitMinor == budget.limitMinor &&
            it.active == budget.active && it.currency == budget.currency
    } == true
}
