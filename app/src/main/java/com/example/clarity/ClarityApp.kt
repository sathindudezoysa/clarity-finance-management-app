package com.example.clarity

import android.app.Application
import com.example.clarity.data.notifications.SalaryNotificationService
import com.example.clarity.data.notifications.WalletReminderScheduler
import com.example.clarity.data.sync.GoalContributionSyncManager
import com.example.clarity.data.sync.GoalSyncManager
import com.example.clarity.data.sync.ProofSyncManager
import com.example.clarity.domain.repository.AccountRepository
import com.example.clarity.domain.repository.TransactionRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ClarityApp : Application() {
    // Eager singleton injection starts auth-scoped sync without opening a transaction screen.
    @Inject
    lateinit var transactionRepository: TransactionRepository

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var proofSyncManager: ProofSyncManager

    @Inject lateinit var budgetSyncManager: com.example.clarity.data.sync.BudgetSyncManager
    @Inject lateinit var budgetAlertManager: com.example.clarity.data.sync.BudgetAlertManager
    @Inject lateinit var goalSyncManager: GoalSyncManager
    @Inject lateinit var goalContributionSyncManager: GoalContributionSyncManager
    @Inject lateinit var walletReminderScheduler: WalletReminderScheduler
    @Inject lateinit var salaryNotificationService: SalaryNotificationService

    @Inject lateinit var quickEntryWidgetRefresher: com.example.clarity.presentation.widget.QuickEntryWidgetRefresher

    override fun onCreate() {
        super.onCreate()
        proofSyncManager.start()
        budgetSyncManager.start()
        budgetAlertManager.start()
        goalSyncManager.start()
        goalContributionSyncManager.start()
        walletReminderScheduler.start()
        salaryNotificationService.start()
        quickEntryWidgetRefresher.start()
    }
}
