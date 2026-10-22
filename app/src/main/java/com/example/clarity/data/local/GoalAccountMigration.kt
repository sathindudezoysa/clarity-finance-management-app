package com.example.clarity.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Preserve all v16 tables and add the nullable sourceAccountId column to goal_progress_operations. */
val MIGRATION_16_17: Migration = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `goal_progress_operations` ADD COLUMN `sourceAccountId` TEXT")
    }
}

