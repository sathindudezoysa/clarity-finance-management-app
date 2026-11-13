package com.example.clarity.domain.usecase.salary

import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.repository.SalaryReminderRepository
import javax.inject.Inject

/** Automatically updates the salary notification schedule when the user enters or updates a Salary transaction. */
class RecordSalaryReminderUseCase @Inject constructor(
    private val repository: SalaryReminderRepository
) {
    suspend operator fun invoke(transaction: Transaction, account: Account) {
        if (transaction.type == TransactionType.INCOME &&
            transaction.category.trim().equals(SALARY_CATEGORY, ignoreCase = true) &&
            transaction.amountMinor > 0L
        ) {
            repository.recordSalary(transaction, account)
        }
    }

    companion object {
        const val SALARY_CATEGORY = "Salary"
    }
}
