package com.example.clarity.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Preserve v10 transactions with the identity snapshot used by same-currency transactions. */
val MIGRATION_10_11: Migration = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Kotlin constructor defaults are not SQL defaults. Rebuild rather than giving
        // the new NOT NULL columns permanent defaults absent from the Room schema.
        db.execSQL("""
            CREATE TABLE `transactions_currency_11` (
                `id` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `amountMinor` INTEGER NOT NULL,
                `category` TEXT NOT NULL,
                `note` TEXT NOT NULL,
                `dateEpochMillis` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `pendingSync` INTEGER NOT NULL,
                `isDeleted` INTEGER NOT NULL,
                `accountId` TEXT DEFAULT NULL,
                `currency` TEXT NOT NULL,
                `targetCurrency` TEXT NOT NULL,
                `pendingConversion` INTEGER NOT NULL,
                `conversion_amountMinor` INTEGER,
                `conversion_rate` TEXT,
                `conversion_provider` TEXT,
                `conversion_rateAt` INTEGER,
                `conversion_fetchedAt` INTEGER,
                `conversion_cached` INTEGER,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `transactions_currency_11` (
                `id`, `userId`, `type`, `amountMinor`, `category`, `note`,
                `dateEpochMillis`, `createdAt`, `updatedAt`, `pendingSync`, `isDeleted`,
                `accountId`, `currency`, `targetCurrency`, `pendingConversion`,
                `conversion_amountMinor`, `conversion_rate`, `conversion_provider`,
                `conversion_rateAt`, `conversion_fetchedAt`, `conversion_cached`
            )
            SELECT `id`, `userId`, `type`, `amountMinor`, `category`, `note`,
                `dateEpochMillis`, `createdAt`, `updatedAt`, `pendingSync`, `isDeleted`,
                `accountId`, 'LKR', 'LKR', 0, `amountMinor`, '1', 'IDENTITY', 0, 0, 0
            FROM `transactions`
        """.trimIndent())
        db.execSQL("DROP TABLE `transactions`")
        db.execSQL("ALTER TABLE `transactions_currency_11` RENAME TO `transactions`")
        db.execSQL("CREATE INDEX `index_transactions_userId` ON `transactions` (`userId`)")
        db.execSQL("CREATE INDEX `index_transactions_accountId` ON `transactions` (`accountId`)")
        db.execSQL("""
            CREATE TABLE `exchange_rates` (
                `source` TEXT NOT NULL,
                `target` TEXT NOT NULL,
                `rate` TEXT NOT NULL,
                `provider` TEXT NOT NULL,
                `rateAt` INTEGER NOT NULL,
                `fetchedAt` INTEGER NOT NULL,
                `nextUpdateAt` INTEGER NOT NULL,
                PRIMARY KEY(`source`, `target`)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE `currency_preferences` (
                `userId` TEXT NOT NULL,
                `currency` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `pendingSync` INTEGER NOT NULL,
                PRIMARY KEY(`userId`)
            )
        """.trimIndent())
    }
}
