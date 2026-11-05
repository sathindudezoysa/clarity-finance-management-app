package com.example.clarity

import android.app.Application
import com.example.clarity.data.sync.GoalContributionSyncManager
import com.example.clarity.data.sync.GoalSyncManager
import com.example.clarity.data.sync.IncomeSyncManager
import com.example.clarity.data.sync.ProofSyncManager
import dagger.hilt.android.HiltAndroidApp
import com.example.clarity.domain.repository.TransactionRepository
import com.example.clarity.domain.repository.AccountRepository
import javax.inject.Inject
import com.example.clarity.data.notifications.WalletReminderScheduler

@HiltAndroidApp
class ClarityApp : Application() {
    // Eager singleton injection starts auth-scoped sync without opening a transaction screen.
    @Inject
    lateinit var transactionRepository: TransactionRepository

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var proofSyncManager: ProofSyncManager

    @Inject lateinit var incomeSyncManager: IncomeSyncManager
    @Inject lateinit var budgetSyncManager: com.example.clarity.data.sync.BudgetSyncManager
    @Inject lateinit var budgetAlertManager: com.example.clarity.data.sync.BudgetAlertManager
    @Inject lateinit var goalSyncManager: GoalSyncManager
    @Inject lateinit var goalContributionSyncManager: GoalContributionSyncManager
    @Inject lateinit var walletReminderScheduler: WalletReminderScheduler

    override fun onCreate() {
        super.onCreate()
        proofSyncManager.start()
        incomeSyncManager.start()
        budgetSyncManager.start()
        budgetAlertManager.start()
        goalSyncManager.start()
        goalContributionSyncManager.start()
        walletReminderScheduler.start()
    }
}
