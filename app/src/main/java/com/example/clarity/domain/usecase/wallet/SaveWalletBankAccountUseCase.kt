package com.example.clarity.domain.usecase.wallet

import com.example.clarity.domain.model.WalletBankAccountInput
import com.example.clarity.domain.repository.CashWalletRepository
import javax.inject.Inject

class SaveWalletBankAccountUseCase @Inject constructor(private val repository: CashWalletRepository) {
    suspend operator fun invoke(input: WalletBankAccountInput) = repository.saveBankAccount(input)
}
