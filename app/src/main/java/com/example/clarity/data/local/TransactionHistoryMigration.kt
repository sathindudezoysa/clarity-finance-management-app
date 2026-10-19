package com.example.clarity.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Upgrade the original sample-only database without erasing its contents. */
object TransactionHistoryMigration : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS transactions (id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, type TEXT NOT NULL, amountMinor INTEGER NOT NULL, category TEXT NOT NULL, note TEXT NOT NULL, dateEpochMillis INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, pendingSync INTEGER NOT NULL, isDeleted INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_userId ON transactions(userId)")
    }
}
