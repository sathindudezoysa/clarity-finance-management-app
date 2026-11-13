package com.example.clarity.domain.usecase.salary

import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.repository.CurrencyPreferenceRepository
import com.example.clarity.domain.repository.SalaryReminderRepository
import com.example.clarity.domain.usecase.transaction.AddTransactionUseCase
import javax.inject.Inject

/** Records the saved salary as income in the previously used account when confirmed from the notification. */
class ConfirmSalaryReceivedUseCase @Inject constructor(
    private val repository: SalaryReminderRepository,
    private val addTransaction: AddTransactionUseCase,
    private val currencyPreferences: CurrencyPreferenceRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        val reminder = repository.get()
            ?: return Result.failure(IllegalStateException("No salary reminder configured"))
        val target = currencyPreferences.current()
        return addTransaction(
            Transaction(
                type = TransactionType.INCOME,
                amountMinor = reminder.amountMinor,
                category = RecordSalaryReminderUseCase.SALARY_CATEGORY,
                note = "Monthly salary",
                accountId = reminder.accountId,
                currency = reminder.currency,
                targetCurrency = target
            )
        )
    }
}
