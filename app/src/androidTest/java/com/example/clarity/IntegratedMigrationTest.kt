package com.example.clarity

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.clarity.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntegratedMigrationTest {
    @Test fun developmentSchemaChangeRecreatesBothOldSchemaFamilies() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (wallet in listOf(false, true)) for (version in 3..6) {
            val name = "integration-${wallet}-${version}"
            context.deleteDatabase(name)
            try {
                val helper = FrameworkSQLiteOpenHelperFactory().create(
                    SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                        .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                db.execSQL("CREATE TABLE sample_table (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, description TEXT NOT NULL)")
                                TransactionHistoryMigration.migrate(db)
                                TransactionTagMigration.migrate(db)
                                db.execSQL("INSERT INTO transactions VALUES ('cash', 'owner', 'INCOME', 5000, 'Hobby', 'Keep description', 100, 1, 10, 1, 0)")
                                db.execSQL("INSERT INTO transaction_tags VALUES ('owner', 'Hobby')")
                                if (version >= 4) {
                                    if (wallet) {
                                        CashWalletMigration.migrate(db)
                                        db.execSQL("INSERT INTO wallet_bank_accounts VALUES ('owner', 'bank', 'Savings', 10000)")
                                        db.execSQL("INSERT INTO wallet_cash_links VALUES ('owner', 'cash')")
                                        db.execSQL("INSERT INTO wallet_top_ups VALUES ('owner', 'transfer', 'bank', 1000, 100, 0)")
                                        db.execSQL("INSERT INTO wallet_top_up_options VALUES ('owner', 'bank', 1, 200, 1000)")
                                        if (version >= 5) WalletCompletionMigration.migrate(db)
                                        if (version >= 6) WalletAutomationMigration.migrate(db)
                                    } else {
                                        AccountMigration.migrate(db)
                                        db.execSQL("INSERT INTO accounts VALUES ('bank', 'owner', 'Daily', 'Bank', NULL, 'LKR', 1, 1, 1, 0)")
                                        if (version >= 5) TransactionAccountMigration.migrate(db)
                                        if (version >= 6) TransactionAccountIndexMigration.migrate(db)
                                    }
                                }
                            }
                            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                                error("Legacy fixture must not upgrade")
                        }).build())
                helper.writableDatabase
                helper.close()
                val db = Room.databaseBuilder(context, ClarityDatabase::class.java, name)
                    .fallbackToDestructiveMigration().build()
                try {
                        // Development policy explicitly discards unsynced data on schema change.
                        assertNull(db.transactionDao.getById("cash"))
                        assertTrue(db.accountDao.getAllForUser("owner").isEmpty())
                        assertNull(db.currencyDao.preference("owner"))
                        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM wallet_top_ups").use {
                            assertTrue(it.moveToFirst())
                            assertEquals(0, it.getInt(0))
                        }
                        db.openHelper.readableDatabase.query("SELECT automaticExecution FROM wallet_top_up_options").use {
                            assertFalse(it.moveToFirst())
                        }
                } finally { db.close() }
            } finally { context.deleteDatabase(name) }
        }
    }
}
