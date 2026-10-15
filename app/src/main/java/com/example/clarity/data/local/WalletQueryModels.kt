package com.example.clarity.data.local

import androidx.room.Embedded
import com.example.clarity.data.local.entity.WalletBankAccountEntity
import java.math.BigInteger

/** Split integer sums stay exact even when historical totals exceed a signed 64-bit sum. */
data class WalletMoneyParts(val high: Long, val low: Long) {
    fun value(): BigInteger = BigInteger.valueOf(high) * BigInteger.valueOf(1_000_000_000) + BigInteger.valueOf(low)
}

data class WalletBankTotals(@Embedded val account: WalletBankAccountEntity, val high: Long, val low: Long)

data class WalletActivityRow(
    val id: String, val title: String, val amountMinor: Long, val dateEpochMillis: Long,
    val kind: String, val transactionId: String?, val bankAccountId: String?
)
