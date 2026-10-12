package com.example.clarity.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds reusable tags without altering existing transactions or their pending sync state. */
object TransactionTagMigration : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS transaction_tags (
                userId TEXT NOT NULL,
                name TEXT COLLATE NOCASE NOT NULL,
                PRIMARY KEY(userId, name)
            )
        """.trimIndent())
        db.execSQL("""
            INSERT OR IGNORE INTO transaction_tags (userId, name)
            SELECT userId, TRIM(category) FROM transactions
            WHERE LENGTH(TRIM(category)) BETWEEN 1 AND 100
        """.trimIndent())
    }
}
