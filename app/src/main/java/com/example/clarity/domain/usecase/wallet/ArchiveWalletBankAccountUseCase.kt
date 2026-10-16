package com.example.clarity.domain.usecase.wallet

import com.example.clarity.domain.repository.CashWalletRepository
import javax.inject.Inject

class ArchiveWalletBankAccountUseCase @Inject constructor(private val repository: CashWalletRepository) {
    suspend operator fun invoke(id: String) = repository.archiveBankAccount(id)
}
