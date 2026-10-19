package com.example.clarity.domain.usecase.wallet

import com.example.clarity.domain.repository.CashWalletRepository
import javax.inject.Inject

class AddWalletBankAccountUseCase @Inject constructor(private val repository: CashWalletRepository) {
    suspend operator fun invoke(name: String, openingBalanceMinor: Long) = repository.addBankAccount(name, openingBalanceMinor)
}
