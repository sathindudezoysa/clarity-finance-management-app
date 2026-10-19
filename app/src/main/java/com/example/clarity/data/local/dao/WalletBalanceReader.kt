package com.example.clarity.data.local.dao

import androidx.room.Query
import com.example.clarity.data.local.WalletMoneyParts

/** Shared SQL for exact cash-ledger integrity checks in Room DAOs. */
interface WalletBalanceReader {
    @Query(WALLET_BALANCE_SQL)
    suspend fun balance(uid: String): WalletMoneyParts
}
