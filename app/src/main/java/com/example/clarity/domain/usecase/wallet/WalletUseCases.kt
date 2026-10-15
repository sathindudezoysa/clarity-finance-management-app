package com.example.clarity.domain.usecase.wallet

import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.model.WalletTopUpOption
import com.example.clarity.domain.model.WalletBankAccountInput
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
    suspend operator fun invoke(bankAccountId: String, amountMinor: Long, operationId: String) = repository.topUp(bankAccountId, amountMinor, operationId)
}

class AddWalletBankAccountUseCase @Inject constructor(private val repository: CashWalletRepository) {
    suspend operator fun invoke(name: String, openingBalanceMinor: Long) = repository.addBankAccount(name, openingBalanceMinor)
}

class ConfigureWalletTopUpUseCase @Inject constructor(private val repository: CashWalletRepository) {
    suspend operator fun invoke(option: WalletTopUpOption) = repository.configureTopUp(option)
}

class SaveWalletBankAccountUseCase @Inject constructor(private val repository: CashWalletRepository) {
    suspend operator fun invoke(input: WalletBankAccountInput) = repository.saveBankAccount(input)
}
class ArchiveWalletBankAccountUseCase @Inject constructor(private val repository: CashWalletRepository) {
    suspend operator fun invoke(id: String) = repository.archiveBankAccount(id)
}
class AdjustCashBalanceUseCase @Inject constructor(private val repository: CashWalletRepository) {
    suspend operator fun invoke(amount: Long, note: String, operationId: String) = repository.adjustCashBalance(amount, note, operationId)
}
class GetWalletBankActivityUseCase @Inject constructor(private val repository: CashWalletRepository) {
    suspend operator fun invoke(id: String) = repository.bankActivity(id)
}
