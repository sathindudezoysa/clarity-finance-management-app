package com.example.clarity.domain.usecase.wallet

import com.example.clarity.domain.repository.CashWalletRepository
import javax.inject.Inject

class ObserveCashWalletUseCase @Inject constructor(private val repository: CashWalletRepository) {
    operator fun invoke() = repository.observeWallet()
}
