package com.example.clarity.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "wallet_bank_accounts", primaryKeys = ["userId", "id"])
data class WalletBankAccountEntity(
    val userId: String, val id: String, val name: String, val openingBalanceMinor: Long,
    @androidx.room.ColumnInfo(defaultValue = "'other'") val bankId: String = "other",
    @androidx.room.ColumnInfo(defaultValue = "''") val lastFour: String = "",
    @androidx.room.ColumnInfo(defaultValue = "'OTHER'") val accountType: String = "OTHER",
    @androidx.room.ColumnInfo(defaultValue = "'LKR'") val currency: String = "LKR",
    @androidx.room.ColumnInfo(defaultValue = "0") val isDefault: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "1") val isActive: Boolean = true,
    @androidx.room.ColumnInfo(defaultValue = "1") val needsBankReview: Boolean = true
)

/** One row represents both sides of a bank-to-cash transfer, so they cannot diverge. */
@Entity(tableName = "wallet_top_ups", primaryKeys = ["userId", "id"], indices = [Index(value = ["userId", "bankAccountId", "dateEpochMillis"])])
data class WalletTopUpEntity(
    val userId: String, val id: String, val bankAccountId: String,
    val amountMinor: Long, val dateEpochMillis: Long, val automatic: Boolean
)

@Entity(tableName = "wallet_cash_links", primaryKeys = ["userId", "transactionId"])
data class WalletCashLinkEntity(val userId: String, val transactionId: String)

@Entity(tableName = "wallet_top_up_options", primaryKeys = ["userId"])
data class WalletTopUpOptionEntity(
    val userId: String, val bankAccountId: String, val enabled: Boolean,
    val thresholdMinor: Long, val amountMinor: Long
)

@Entity(tableName = "wallet_adjustments", primaryKeys = ["userId", "id"])
data class WalletAdjustmentEntity(
    val userId: String, val id: String, val amountMinor: Long,
    val targetBalanceMinor: Long, val note: String, val dateEpochMillis: Long
)
