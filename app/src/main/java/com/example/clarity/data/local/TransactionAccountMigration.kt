package com.example.clarity.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object TransactionAccountMigration : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN accountId TEXT DEFAULT NULL")
        val now = System.currentTimeMillis()
        // Backfilled associations are local edits: keep them newer than old server documents
        // and retain them in the outbox until Firestore confirms the accountId as well.
        db.execSQL("""
            UPDATE transactions SET
                accountId = (SELECT id FROM accounts
                    WHERE accounts.userId = transactions.userId
                    ORDER BY isDefault DESC, createdAt ASC, id ASC LIMIT 1),
                pendingSync = 1,
                updatedAt = CASE WHEN updatedAt < 9223372036854775807
                    THEN MAX(updatedAt + 1, $now) ELSE updatedAt END
            WHERE EXISTS (SELECT 1 FROM accounts
                WHERE accounts.userId = transactions.userId)
        """.trimIndent())
    }
}
