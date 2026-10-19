package com.example.clarity

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.local.entity.TransactionEntity
import com.example.clarity.data.local.entity.TransactionProofEntity
import com.example.clarity.data.local.entity.WalletBankAccountEntity
import com.example.clarity.data.local.entity.WalletCashLinkEntity
import com.example.clarity.data.local.entity.WalletTopUpEntity
import com.example.clarity.di.DatabaseModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigInteger
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DatabaseCachePolicyTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun productionBuilderRecreatesStaleCachesOnUpgradeAndDowngrade() = runBlocking {
        for (version in ((1 until ClarityDatabase.SCHEMA_VERSION).toList() + (ClarityDatabase.SCHEMA_VERSION + 1))) {
            val name = "cache-policy-$version-${UUID.randomUUID()}"
            try {
                val path = context.getDatabasePath(name)
                val parent = requireNotNull(path.parentFile)
                check(parent.isDirectory || parent.mkdirs())
                SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
                    db.execSQL("CREATE TABLE sample_table (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, description TEXT NOT NULL)")
                    db.execSQL("INSERT INTO sample_table VALUES ('legacy', 'Old cache', 'Rebuild')")
                    db.execSQL("CREATE TABLE transactions (id TEXT NOT NULL PRIMARY KEY, pendingSync INTEGER NOT NULL)")
                    db.execSQL("INSERT INTO transactions VALUES ('local-only', 1)")
                    db.execSQL("CREATE TABLE transaction_proofs (id TEXT NOT NULL PRIMARY KEY, pendingUpload INTEGER NOT NULL)")
                    db.execSQL("INSERT INTO transaction_proofs VALUES ('local-proof', 1)")
                    db.version = version
                }
                val db = DatabaseModule.createDatabase(context, name)
                try {
                    assertEquals(ClarityDatabase.SCHEMA_VERSION, db.openHelper.readableDatabase.version)
                    assertTrue(db.transactionDao.getIdsForUser("owner").isEmpty())
                    assertTrue(db.accountDao.getAllForUser("owner").isEmpty())
                    assertTrue(db.cashWalletDao.accounts("owner").isEmpty())
                    assertEquals(0, db.transactionProofDao.count("owner", "transaction"))
                    db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sample_table").use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals(0, cursor.getInt(0))
                    }
                    // Fresh remote reconciliation can repopulate transactions and their tag catalog.
                    val remote = TransactionEntity("remote", "owner", "INCOME", 5000, "Salary", "Synced", 1, 1, 1)
                    db.transactionDao.reconcileRemote("owner", listOf(remote), setOf("remote"), authoritative = true)
                    assertEquals(remote, db.transactionDao.getById("remote"))
                    db.transactionDao.deleteById("remote", "owner")
                    assertEquals(listOf("Salary"), db.transactionTagDao.observeTags("owner").first())
                } finally { db.close() }
            } finally { context.deleteDatabase(name) }
        }
    }

    @Test fun reopeningCurrentSchemaPreservesWalletTransactionsAndPendingProofs() = runBlocking {
        val name = "current-cache-${UUID.randomUUID()}"
        val transaction = TransactionEntity("transaction", "owner", "EXPENSE", 1250, "Food", "Lunch", 1, 1, 1, pendingSync = true)
        val proof = TransactionProofEntity("owner", "transaction", "proof", "Receipt.pdf", "application/pdf", 100, 1, 1250, true, 42)
        try {
            var db = DatabaseModule.createDatabase(context, name)
            try {
                db.transactionDao.upsertWithTag(transaction)
                db.transactionProofDao.upsert(proof)
                db.cashWalletDao.insertAccount(WalletBankAccountEntity("owner", "bank", "Daily", 10000))
                db.cashWalletDao.insertTopUp(WalletTopUpEntity("owner", "transfer", "bank", 3000, 1, false))
                db.cashWalletDao.insertCashLink(WalletCashLinkEntity("owner", "transaction"))
            } finally { db.close() }
            db = DatabaseModule.createDatabase(context, name)
            try {
                assertEquals(transaction, db.transactionDao.getById("transaction"))
                assertEquals(proof, db.transactionProofDao.get("owner", "transaction", "proof"))
                assertEquals(BigInteger.valueOf(1750), db.cashWalletDao.balance("owner").value())
                assertEquals(1, db.cashWalletDao.topUps("owner").size)
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
}
