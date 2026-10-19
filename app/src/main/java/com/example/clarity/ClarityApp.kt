package com.example.clarity

import android.app.Application
import com.example.clarity.data.sync.IncomeSyncManager
import com.example.clarity.data.sync.ProofSyncManager
import dagger.hilt.android.HiltAndroidApp
import com.example.clarity.domain.repository.TransactionRepository
import com.example.clarity.domain.repository.AccountRepository
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

    @Inject lateinit var incomeSyncManager: IncomeSyncManager

    override fun onCreate() {
        super.onCreate()
        proofSyncManager.start()
        incomeSyncManager.start()
    }
}
