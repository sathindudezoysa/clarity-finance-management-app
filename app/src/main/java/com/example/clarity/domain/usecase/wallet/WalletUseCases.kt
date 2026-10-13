package com.example.clarity.domain.usecase.wallet

import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.model.WalletTopUpOption
import com.example.clarity.domain.repository.CashWalletRepository
import javax.inject.Inject

class ObserveCashWalletUseCase @Inject constructor(private val repository: CashWalletRepository) {
    operator fun invoke() = repository.observeWallet()
}

class RecordCashTransactionUseCase @Inject constructor(private val repository: CashWalletRepository) {
    suspend operator fun invoke(amountMinor: Long, type: TransactionType, category: String, note: String) =
        repository.recordCash(amountMinor, type, category, note)
}

class TopUpCashWalletUseCase @Inject constructor(private val repository: CashWalletRepository) {
    suspend operator fun invoke(bankAccountId: String, amountMinor: Long) = repository.topUp(bankAccountId, amountMinor)
}

class AddWalletBankAccountUseCase @Inject constructor(private val repository: CashWalletRepository) {
    suspend operator fun invoke(name: String, openingBalanceMinor: Long) = repository.addBankAccount(name, openingBalanceMinor)
}

class ConfigureWalletTopUpUseCase @Inject constructor(private val repository: CashWalletRepository) {
    suspend operator fun invoke(option: WalletTopUpOption) = repository.configureTopUp(option)
}
