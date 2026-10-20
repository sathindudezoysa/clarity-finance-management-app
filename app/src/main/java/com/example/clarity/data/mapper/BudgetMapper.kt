package com.example.clarity.data.mapper

import com.example.clarity.data.local.entity.BudgetEntity
import com.example.clarity.domain.model.Budget
import com.example.clarity.domain.model.BudgetPolicy

fun BudgetEntity.toDomain() = Budget(id, userId, month, category, limitMinor, currency, active, createdAt, updatedAt, pendingSync)
fun Budget.toEntity() = BudgetEntity(id, userId, month, category, BudgetPolicy.categoryKey(category), limitMinor, currency, active, createdAt, updatedAt, pendingSync)
fun Budget.toBudgetRemoteMap(): Map<String, Any> = mapOf(
    "id" to id, "userId" to userId, "month" to month, "category" to category,
    "categoryKey" to BudgetPolicy.categoryKey(category), "limitMinor" to limitMinor, "currency" to currency,
    "active" to active, "createdAt" to createdAt, "updatedAt" to updatedAt
)
fun Map<String, Any>.toBudget(id: String, uid: String): Budget? = runCatching {
    val budget = Budget(this["id"] as String, this["userId"] as String, this["month"] as String,
        this["category"] as String, this["limitMinor"] as Long, this["currency"] as String,
        this["active"] as Boolean, this["createdAt"] as Long, this["updatedAt"] as Long)
    require(budget.id == id && budget.userId == uid && this["categoryKey"] == BudgetPolicy.categoryKey(budget.category))
    BudgetPolicy.validate(budget)
    budget
}.getOrNull()
