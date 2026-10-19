package com.example.clarity

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.clarity.data.local.AccountMigration
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.local.InitialTransactionMigration
import com.example.clarity.data.local.IntegratedMigrations
import com.example.clarity.data.local.ProofRecoveryMigration
import com.example.clarity.data.local.TransactionAccountIndexMigration
import com.example.clarity.data.local.TransactionAccountMigration
import com.example.clarity.data.local.TransactionProofMigration
import com.example.clarity.data.local.TransactionTagMigration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigInteger
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WalletProofMigrationTest {
    @Test fun walletAndProofReleasesUpgradeAndReopenWithoutLosingEitherLedgerOrOutbox() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (version in listOf(7, 8, 9, 10)) {
            val name = "wallet-proof-$version-${UUID.randomUUID()}"
            val hasWallet = version == 7 || version == 10
            val hasProof = version >= 8
            try {
                val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(name).callback(object : SupportSQLiteOpenHelper.Callback(version) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL("CREATE TABLE sample_table (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, description TEXT NOT NULL)")
                            InitialTransactionMigration.migrate(db)
                            TransactionTagMigration.migrate(db)
                            AccountMigration.migrate(db)
                            TransactionAccountMigration.migrate(db)
                            TransactionAccountIndexMigration.migrate(db)
                            db.execSQL("INSERT INTO accounts VALUES ('bank', 'owner', 'Daily', 'Bank', NULL, 'LKR', 1, 1, 1, 1)")
                            db.execSQL("INSERT INTO transactions VALUES ('transaction', 'owner', 'EXPENSE', 1250, 'Hobby', 'Keep receipt', 100, 1, 10, 1, 0, NULL)")
                            db.execSQL("INSERT INTO transaction_tags VALUES ('owner', 'Hobby')")
                            if (hasWallet) {
                                IntegratedMigrations.all.single { it.startVersion == 6 && it.endVersion == 7 }.migrate(db)
                                db.execSQL("INSERT INTO wallet_bank_accounts (userId, id, name, openingBalanceMinor) VALUES ('owner', 'wallet-bank', 'Wallet bank', 10000)")
                                db.execSQL("INSERT INTO wallet_top_ups VALUES ('owner', 'transfer', 'wallet-bank', 3000, 100, 0)")
                                db.execSQL("INSERT INTO wallet_cash_links VALUES ('owner', 'transaction')")
                                db.execSQL("INSERT INTO wallet_top_up_options VALUES ('owner', 'wallet-bank', 1, 200, 1000, 1)")
                            }
                            if (hasProof) {
                                TransactionProofMigration().migrate(db)
                                if (version >= 9) ProofRecoveryMigration.migrate(db)
                                db.execSQL("INSERT INTO transaction_proofs (userId, transactionId, id, displayName, mimeType, sizeBytes, createdAt, verifiedAmountMinor, pendingUpload, revision) VALUES ('owner', 'transaction', 'proof', 'Receipt.pdf', 'application/pdf', 100, 1, 1250, 1, 42)")
                            }
                        }
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Fixture only")
                    }).build())
                helper.writableDatabase
                helper.close()
                repeat(2) {
                    val db = Room.databaseBuilder(context, ClarityDatabase::class.java, name)
                        .addMigrations(*IntegratedMigrations.all).build()
                    try {
                        val transaction = db.transactionDao.getById("transaction")!!
                        assertEquals(1250L, transaction.amountMinor)
                        assertEquals("Keep receipt", transaction.note)
                        assertEquals("Hobby", transaction.category)
                        assertTrue(transaction.pendingSync)
                        assertNull(transaction.accountId)
                        assertEquals(1, db.transactionDao.getIdsForUser("owner").size)
                        assertEquals(listOf("Hobby"), db.transactionTagDao.observeTags("owner").first())
                        assertTrue(db.accountDao.getById("bank")!!.pendingSync)
                        assertEquals(if (hasWallet) 1 else 0, db.cashWalletDao.topUps("owner").size)
                        assertEquals(if (hasWallet) BigInteger.valueOf(1750) else BigInteger.ZERO, db.cashWalletDao.balance("owner").value())
                        if (hasWallet) {
                            assertTrue(db.cashWalletDao.isCashLink("owner", "transaction"))
                            assertTrue(db.cashWalletDao.option("owner")!!.automaticExecution)
                            assertEquals(3000L, db.cashWalletDao.topUps("owner").single().amountMinor)
                        }
                        assertEquals(if (hasProof) 1 else 0, db.transactionProofDao.count("owner", "transaction"))
                        if (hasProof) {
                            val proof = db.transactionProofDao.get("owner", "transaction", "proof")!!
                            assertTrue(proof.pendingUpload)
                            assertFalse(proof.unavailable)
                            assertEquals(1250L, proof.verifiedAmountMinor)
                            assertEquals(42L, proof.revision)
                        }
                    } finally { db.close() }
                }
            } finally { context.deleteDatabase(name) }
        }
    }
}
