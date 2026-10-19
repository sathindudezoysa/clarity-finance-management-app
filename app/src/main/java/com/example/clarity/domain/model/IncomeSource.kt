package com.example.clarity.domain.model

import java.time.YearMonth
import java.util.UUID

data class IncomeSource(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val name: String,
    val amountMinor: Long,
    val accountId: String,
    val recurring: Boolean,
    val active: Boolean = true,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val lastConfirmedMonth: String? = null,
    val pendingSync: Boolean = false
) {
    fun isDue(month: YearMonth): Boolean = active && recurring &&
        (lastConfirmedMonth == null || YearMonth.parse(lastConfirmedMonth) < month)
}

enum class IncomeError { SIGN_IN, INVALID, ACCOUNT, NOT_FOUND, ALREADY_CONFIRMED, PERIOD_CHANGED, SOURCE_CHANGED }
class IncomeException(val error: IncomeError) : Exception(error.name)

data class IncomeSources(val ownerId: String?, val sources: List<IncomeSource>)
