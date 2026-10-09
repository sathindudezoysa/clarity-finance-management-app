package com.example.clarity

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.example.clarity.domain.repository.TransactionRepository
import javax.inject.Inject

@HiltAndroidApp
class ClarityApp : Application() {
    // Eager singleton injection starts auth-scoped sync without opening a transaction screen.
    @Inject
    lateinit var transactionRepository: TransactionRepository
}
