package com.example.clarity.presentation.income

import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.IncomeSource
import java.time.YearMonth

/** Draft values are separate from database emissions so unsaved edits are never overwritten. */
data class IncomeDraft(val source: IncomeSource, val name: String, val amount: String, val accountId: String, val recurring: Boolean)
data class IncomeState(
    val ownerId: String? = null, val sources: List<IncomeSource> = emptyList(), val accounts: List<Account> = emptyList(),
    val period: YearMonth = YearMonth.now(), val loading: Boolean = true, val busy: Boolean = false,
    val error: String? = null, val draft: IncomeDraft? = null, val confirmation: IncomeSource? = null, val confirmationMonth: String? = null,
    val lastTransactionId: String? = null
)

object IncomeMessages {
    const val LOAD_ERROR = "Could not load income sources. Reopen this screen to retry."
    const val ACCOUNT_ERROR = "Choose an available LKR bank account owned by you."
    const val SIGN_IN = "Please sign in to manage income sources."
    const val ALREADY_CONFIRMED = "This income has already been confirmed for this month."
    const val CHANGED = "The source or month changed. Review it again before confirming."
    const val SAVE_ERROR = "Could not save. Check the name, positive amount and receiving account."
}
