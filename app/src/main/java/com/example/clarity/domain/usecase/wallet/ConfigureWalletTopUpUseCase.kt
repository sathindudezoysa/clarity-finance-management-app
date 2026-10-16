package com.example.clarity.domain.usecase.wallet

import com.example.clarity.domain.model.WalletTopUpOption
import com.example.clarity.domain.repository.CashWalletRepository
import javax.inject.Inject

class ConfigureWalletTopUpUseCase @Inject constructor(private val repository: CashWalletRepository) {
    suspend operator fun invoke(option: WalletTopUpOption) = repository.configureTopUp(option)
}
