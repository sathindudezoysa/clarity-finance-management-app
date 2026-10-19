package com.example.clarity.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AccountMigration : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS accounts (
                id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL,
                name TEXT COLLATE NOCASE NOT NULL, bankName TEXT NOT NULL,
                accountType TEXT, currency TEXT NOT NULL, isDefault INTEGER NOT NULL,
                createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, pendingSync INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_accounts_userId ON accounts (userId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_accounts_userId_name ON accounts (userId, name)")
    }
}

// Version 1 contained only sample_table. Preserve it when replacing destructive fallback.
object InitialTransactionMigration : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS transactions (
                id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, type TEXT NOT NULL,
                amountMinor INTEGER NOT NULL, category TEXT NOT NULL, note TEXT NOT NULL,
                dateEpochMillis INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                pendingSync INTEGER NOT NULL, isDeleted INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_userId ON transactions (userId)")
    }
}
