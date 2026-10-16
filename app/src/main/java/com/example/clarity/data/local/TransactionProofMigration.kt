package com.example.clarity.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 7 is also used by the pending wallet branch; keep evidence upgrades additive. */
class TransactionProofMigration(from: Int = 6) : Migration(from, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS transaction_proofs (userId TEXT NOT NULL, transactionId TEXT NOT NULL, id TEXT NOT NULL, displayName TEXT NOT NULL, mimeType TEXT NOT NULL, sizeBytes INTEGER NOT NULL, createdAt INTEGER NOT NULL, verifiedAmountMinor INTEGER, pendingUpload INTEGER NOT NULL, revision INTEGER NOT NULL, PRIMARY KEY(userId, transactionId, id))")
    }
}
