package com.example.clarity.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val CashWalletMigration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS wallet_bank_accounts (userId TEXT NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL, openingBalanceMinor INTEGER NOT NULL, PRIMARY KEY(userId, id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS wallet_top_ups (userId TEXT NOT NULL, id TEXT NOT NULL, bankAccountId TEXT NOT NULL, amountMinor INTEGER NOT NULL, dateEpochMillis INTEGER NOT NULL, automatic INTEGER NOT NULL, PRIMARY KEY(userId, id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_wallet_top_ups_userId ON wallet_top_ups(userId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS wallet_cash_links (userId TEXT NOT NULL, transactionId TEXT NOT NULL, PRIMARY KEY(userId, transactionId))")
        db.execSQL("CREATE TABLE IF NOT EXISTS wallet_top_up_options (userId TEXT NOT NULL, bankAccountId TEXT NOT NULL, enabled INTEGER NOT NULL, thresholdMinor INTEGER NOT NULL, amountMinor INTEGER NOT NULL, PRIMARY KEY(userId))")
    }
}
