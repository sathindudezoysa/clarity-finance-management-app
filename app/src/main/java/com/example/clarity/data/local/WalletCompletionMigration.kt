package com.example.clarity.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Published 3 -> 4 stays unchanged. Unknown legacy names are preserved, never guessed. */
object WalletCompletionMigration : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE wallet_bank_accounts ADD COLUMN bankId TEXT NOT NULL DEFAULT 'other'")
        db.execSQL("ALTER TABLE wallet_bank_accounts ADD COLUMN lastFour TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE wallet_bank_accounts ADD COLUMN accountType TEXT NOT NULL DEFAULT 'OTHER'")
        db.execSQL("ALTER TABLE wallet_bank_accounts ADD COLUMN currency TEXT NOT NULL DEFAULT 'LKR'")
        db.execSQL("ALTER TABLE wallet_bank_accounts ADD COLUMN isDefault INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE wallet_bank_accounts ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE wallet_bank_accounts ADD COLUMN needsBankReview INTEGER NOT NULL DEFAULT 1")
        db.execSQL("DROP INDEX IF EXISTS index_wallet_top_ups_userId")
        db.execSQL("CREATE INDEX index_wallet_top_ups_userId_bankAccountId_dateEpochMillis ON wallet_top_ups(userId, bankAccountId, dateEpochMillis)")
        db.execSQL("CREATE TABLE wallet_adjustments (userId TEXT NOT NULL, id TEXT NOT NULL, amountMinor INTEGER NOT NULL, targetBalanceMinor INTEGER NOT NULL, note TEXT NOT NULL, dateEpochMillis INTEGER NOT NULL, PRIMARY KEY(userId, id))")
        // Former automatic options become reminders without changing the user's chosen amounts/account.
    }
}
