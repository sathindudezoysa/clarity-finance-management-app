package com.example.clarity.domain.repository

import com.example.clarity.domain.model.CashWallet
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.model.WalletTopUpOption
import kotlinx.coroutines.flow.Flow

interface CashWalletRepository {
    fun observeWallet(): Flow<CashWallet>
    suspend fun recordCash(amountMinor: Long, type: TransactionType, category: String, note: String): Result<Unit>
    suspend fun topUp(bankAccountId: String, amountMinor: Long): Result<Unit>
    suspend fun addBankAccount(name: String, openingBalanceMinor: Long): Result<Unit>
    suspend fun configureTopUp(option: WalletTopUpOption): Result<Unit>
}
