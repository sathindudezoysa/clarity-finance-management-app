package com.example.clarity.domain.repository

import com.example.clarity.domain.model.BudgetStatus

interface BudgetNotificationDelivery {
    fun available(): Boolean
    fun deliver(status: BudgetStatus)
    fun cancel(owner: String, budgetId: String)
    fun cancelOwner(owner: String)
}
