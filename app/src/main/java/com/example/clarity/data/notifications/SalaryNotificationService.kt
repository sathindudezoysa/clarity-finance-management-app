package com.example.clarity.data.notifications

import com.example.clarity.data.repository.SalaryReminderRepositoryImpl
import com.example.clarity.di.ApplicationScope
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.repository.AccountRepository
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.repository.TransactionRepository
import com.example.clarity.domain.usecase.salary.RecordSalaryReminderUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Listens automatically to user-entered Salary transactions and keeps the most recent
 * salary account, amount, and date in SharedPreferences to trigger next month's reminder.
 */
@Singleton
class SalaryNotificationService @Inject constructor(
    private val auth: AuthRepository,
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val salaryRepository: SalaryReminderRepositoryImpl,
    private val recordSalaryReminder: RecordSalaryReminderUseCase,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val started = AtomicBoolean(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (!started.compareAndSet(false, true)) return
        salaryRepository.restore()

        scope.launch {
            auth.currentUser.distinctUntilChangedBy { it?.uid }.collectLatest { user ->
                if (user == null) return@collectLatest
                combine(
                    transactions.observeTransactions(),
                    accounts.observeAccounts()
                ) { txResource, accResource ->
                    val txList = txResource.data ?: return@combine null
                    val accList = accResource.data ?: return@combine null
                    txList to accList
                }.catch { error ->
                    if (error is CancellationException) throw error
                }.collect { pair ->
                    val (txList, accList) = pair ?: return@collect
                    val latestSalary = txList
                        .filter {
                            it.type == TransactionType.INCOME &&
                                it.category.trim().equals(RecordSalaryReminderUseCase.SALARY_CATEGORY, ignoreCase = true) &&
                                it.amountMinor > 0L &&
                                !it.accountId.isNullOrBlank()
                        }
                        .maxWithOrNull(
                            compareBy<Transaction> { maxOf(it.updatedAt, it.createdAt) }
                                .thenBy { it.dateEpochMillis }
                        ) ?: return@collect

                    val account = accList.firstOrNull { it.id == latestSalary.accountId } ?: return@collect
                    val current = salaryRepository.get()
                    val latestStamp = maxOf(latestSalary.updatedAt, latestSalary.createdAt)
                    val isNewerOrChanged = current == null ||
                        latestStamp > current.sourceUpdatedAt ||
                        (latestSalary.id == current.sourceTransactionId &&
                            (latestSalary.amountMinor != current.amountMinor ||
                                latestSalary.accountId != current.accountId ||
                                latestSalary.dateEpochMillis != current.salaryDateEpochMillis ||
                                account.displayLabel != current.accountName))

                    if (isNewerOrChanged) {
                        recordSalaryReminder(latestSalary, account)
                    }
                }
            }
        }
    }
}
