package com.example.clarity.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Existing reminders never become withdrawal authorization during an upgrade. */
object WalletAutomationMigration : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE wallet_top_up_options ADD COLUMN automaticExecution INTEGER NOT NULL DEFAULT 0")
    }
}
