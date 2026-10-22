package com.example.clarity.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Preserve all v15 tables and add the financial goals and progress operations tables. */
val MIGRATION_15_16: Migration = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `goals` (`userId` TEXT NOT NULL, `id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `targetMinor` INTEGER NOT NULL, `savedMinor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `targetDateEpochDay` INTEGER NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `pendingSync` INTEGER NOT NULL, PRIMARY KEY(`userId`, `id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_goals_userId` ON `goals` (`userId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `goal_progress_operations` (`userId` TEXT NOT NULL, `id` TEXT NOT NULL, `goalId` TEXT NOT NULL, `type` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `state` TEXT NOT NULL, PRIMARY KEY(`userId`, `id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_progress_operations_userId_goalId_state` ON `goal_progress_operations` (`userId`, `goalId`, `state`)")
    }
}

