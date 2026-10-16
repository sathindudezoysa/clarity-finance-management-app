package com.example.clarity.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Versions 4–6 were used by two branches. Inspect the schema, never guess from the version. */
object IntegratedMigrations {
    val all: Array<Migration> = arrayOf(
        InitialTransactionMigration, TransactionTagMigration,
        AccountMigration, TransactionAccountMigration, TransactionAccountIndexMigration,
        bridge(3), bridge(4), bridge(5), bridge(6)
    )

    private fun bridge(from: Int) = object : Migration(from, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!db.hasTable("accounts")) AccountMigration.migrate(db)
            if (!db.hasTable("wallet_bank_accounts")) CashWalletMigration.migrate(db)
            if (!db.hasColumn("wallet_bank_accounts", "bankId")) WalletCompletionMigration.migrate(db)
            if (!db.hasColumn("wallet_top_up_options", "automaticExecution")) WalletAutomationMigration.migrate(db)
            if (!db.hasColumn("transactions", "accountId")) {
                TransactionAccountMigration.migrate(db)
                // Cash records retain their cash funding source, including after bank-account backfill.
                db.execSQL("UPDATE transactions SET accountId = NULL WHERE EXISTS (SELECT 1 FROM wallet_cash_links WHERE wallet_cash_links.userId = transactions.userId AND wallet_cash_links.transactionId = transactions.id)")
            }
            TransactionAccountIndexMigration.migrate(db)
        }
    }

    private fun SupportSQLiteDatabase.hasTable(table: String): Boolean =
        query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use { it.moveToFirst() }

    private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean =
        query("PRAGMA table_info(`$table`)").use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            var found = false
            while (cursor.moveToNext()) if (cursor.getString(name) == column) found = true
            found
        }
}
