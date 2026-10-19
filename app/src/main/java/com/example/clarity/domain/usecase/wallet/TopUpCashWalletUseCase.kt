package com.example.clarity.domain.usecase.wallet

import com.example.clarity.domain.repository.CashWalletRepository
import javax.inject.Inject

class TopUpCashWalletUseCase @Inject constructor(private val repository: CashWalletRepository) {
    suspend operator fun invoke(bankAccountId: String, amountMinor: Long, operationId: String) = repository.topUp(bankAccountId, amountMinor, operationId)
}
