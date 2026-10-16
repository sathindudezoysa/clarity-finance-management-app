package com.example.clarity.domain.usecase.wallet

import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.repository.CashWalletRepository
import javax.inject.Inject

class RecordCashTransactionUseCase @Inject constructor(private val repository: CashWalletRepository) {
    suspend operator fun invoke(amountMinor: Long, type: TransactionType, category: String, note: String) =
        repository.recordCash(amountMinor, type, category, note)
}
