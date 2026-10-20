package com.example.clarity.domain.repository

import com.example.clarity.domain.model.BudgetStatus

interface BudgetNotificationDelivery {
    fun available(): Boolean
    /** A durably claimed new threshold; Android may alert according to the user's settings. */
    fun deliver(status: BudgetStatus)
    /** Silently update only an existing visible notification. Never recreate a dismissed one. */
    fun refreshVisible(status: BudgetStatus)
    fun cancel(owner: String, budgetId: String)
    fun cancelOwner(owner: String)
}
