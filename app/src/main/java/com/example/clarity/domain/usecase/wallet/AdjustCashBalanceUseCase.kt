package com.example.clarity.domain.usecase.wallet

import com.example.clarity.domain.repository.CashWalletRepository
import javax.inject.Inject

class AdjustCashBalanceUseCase @Inject constructor(private val repository: CashWalletRepository) {
    suspend operator fun invoke(amount: Long, note: String, operationId: String) = repository.adjustCashBalance(amount, note, operationId)
}
