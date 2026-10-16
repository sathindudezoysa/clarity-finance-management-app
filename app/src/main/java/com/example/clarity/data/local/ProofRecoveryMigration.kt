package com.example.clarity.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Preserve all evidence metadata while recording files requiring user recovery. */
object ProofRecoveryMigration : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transaction_proofs ADD COLUMN unavailable INTEGER NOT NULL DEFAULT 0")
    }
}
