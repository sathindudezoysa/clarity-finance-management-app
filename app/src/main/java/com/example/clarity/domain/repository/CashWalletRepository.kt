package com.example.clarity.domain.repository

import com.example.clarity.domain.model.CashWallet
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.model.WalletTopUpOption
import com.example.clarity.domain.model.WalletBankAccountInput
import com.example.clarity.domain.model.WalletActivity
import kotlinx.coroutines.flow.Flow

interface CashWalletRepository {
    fun observeWallet(): Flow<CashWallet>
    suspend fun recordCash(amountMinor: Long, type: TransactionType, category: String, note: String): Result<Unit>
    suspend fun topUp(bankAccountId: String, amountMinor: Long, operationId: String = java.util.UUID.randomUUID().toString()): Result<Unit>
    suspend fun addBankAccount(name: String, openingBalanceMinor: Long): Result<Unit>
    suspend fun configureTopUp(option: WalletTopUpOption): Result<Unit>
    suspend fun saveBankAccount(input: WalletBankAccountInput): Result<Unit>
    suspend fun archiveBankAccount(id: String): Result<Unit>
    suspend fun adjustCashBalance(targetMinor: Long, note: String, operationId: String): Result<Unit>
    suspend fun bankActivity(id: String): Result<List<WalletActivity>>
}
