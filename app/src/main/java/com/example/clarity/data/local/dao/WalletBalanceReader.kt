package com.example.clarity.data.local.dao

import androidx.room.Query
import com.example.clarity.data.local.WalletMoneyParts

/** Shared SQL for exact cash-ledger integrity checks in Room DAOs. */
interface WalletBalanceReader {
    @Query("SELECT COALESCE(SUM(value / 1000000000), 0) AS high, COALESCE(SUM(value % 1000000000), 0) AS low FROM (SELECT CASE WHEN t.type = 'INCOME' THEN t.amountMinor ELSE -t.amountMinor END AS value FROM transactions t INNER JOIN wallet_cash_links w ON t.id = w.transactionId AND t.userId = w.userId WHERE t.userId = :uid AND t.isDeleted = 0 UNION ALL SELECT amountMinor AS value FROM wallet_top_ups WHERE userId = :uid UNION ALL SELECT amountMinor AS value FROM wallet_adjustments WHERE userId = :uid)")
    suspend fun balance(uid: String): WalletMoneyParts
}
