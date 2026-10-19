package com.example.clarity.presentation.income

import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.IncomeSource
import java.time.YearMonth

/** Draft values are separate from database emissions so unsaved edits are never overwritten. */
data class IncomeDraft(val source: IncomeSource, val name: String, val amount: String, val accountId: String, val recurring: Boolean)
data class IncomeState(
    val ownerId: String? = null, val sources: List<IncomeSource> = emptyList(), val accounts: List<Account> = emptyList(),
    val period: YearMonth = YearMonth.now(), val loading: Boolean = true, val busy: Boolean = false,
    val error: Int? = null, val draft: IncomeDraft? = null, val confirmation: IncomeSource? = null, val confirmationMonth: String? = null,
    val lastTransactionId: String? = null
)
