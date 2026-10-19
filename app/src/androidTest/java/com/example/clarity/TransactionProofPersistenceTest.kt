package com.example.clarity

import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.clarity.data.local.*
import com.example.clarity.data.local.entity.*
import com.example.clarity.data.remote.ProofRemoteDataSource
import com.example.clarity.data.sync.ProofSyncProcessor
import com.example.clarity.data.repository.TransactionProofRepositoryImpl
import com.example.clarity.domain.model.User
import com.example.clarity.domain.repository.AuthRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TransactionProofPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun additiveMigrationPreservesExistingTransactionsAccountsTagsAndOutbox() = runBlocking {
        val name = "proof-migration-${UUID.randomUUID()}"
        try {
            val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name).callback(object : SupportSQLiteOpenHelper.Callback(6) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE sample_table (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, description TEXT NOT NULL)")
                        InitialTransactionMigration.migrate(db)
                        TransactionTagMigration.migrate(db)
                        AccountMigration.migrate(db)
                        TransactionAccountMigration.migrate(db)
                        TransactionAccountIndexMigration.migrate(db)
                        db.execSQL("INSERT INTO accounts VALUES ('bank', 'owner', 'Daily', 'Bank', NULL, 'LKR', 1, 1, 1, 1)")
                        db.execSQL("INSERT INTO transactions VALUES ('transaction', 'owner', 'EXPENSE', 1250, 'Hobby', 'Paint', 100, 1, 10, 1, 0, 'bank')")
                        db.execSQL("INSERT INTO transaction_tags VALUES ('owner', 'Hobby')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Fixture only")
                }).build())
            helper.writableDatabase
            helper.close()
            val db = Room.databaseBuilder(context, ClarityDatabase::class.java, name).addMigrations(*IntegratedMigrations.all).build()
            try {
                val transaction = db.transactionDao.getById("transaction")!!
                assertEquals("Paint", transaction.note)
                assertEquals("Hobby", transaction.category)
                assertEquals("bank", transaction.accountId)
                assertEquals(1250L, transaction.amountMinor)
                assertTrue(transaction.pendingSync)
                assertTrue(db.accountDao.getById("bank")!!.pendingSync)
                assertEquals(0, db.transactionProofDao.count("owner", "transaction"))
                assertEquals(listOf("Hobby"), db.transactionTagDao.observeTags("owner").first())
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun versionEightProofsMigrateWithoutLosingVerificationOrQueueState() = runBlocking {
        val name = "proof-recovery-migration-${UUID.randomUUID()}"
        try {
            val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name).callback(object : SupportSQLiteOpenHelper.Callback(8) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE sample_table (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, description TEXT NOT NULL)")
                        InitialTransactionMigration.migrate(db)
                        TransactionTagMigration.migrate(db)
                        AccountMigration.migrate(db)
                        TransactionAccountMigration.migrate(db)
                        TransactionAccountIndexMigration.migrate(db)
                        TransactionProofMigration().migrate(db)
                        db.execSQL("INSERT INTO transactions VALUES ('transaction', 'owner', 'EXPENSE', 1250, 'Hobby', 'Paint', 100, 1, 10, 1, 0, NULL)")
                        db.execSQL("INSERT INTO transaction_proofs VALUES ('owner', 'transaction', 'proof', 'Receipt.pdf', 'application/pdf', 100, 1, 1250, 1, 2)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Fixture only")
                }).build())
            helper.writableDatabase
            helper.close()
            val db = Room.databaseBuilder(context, ClarityDatabase::class.java, name).addMigrations(*IntegratedMigrations.all).build()
            try {
                val row = db.transactionProofDao.get("owner", "transaction", "proof")!!
                assertFalse(row.unavailable)
                assertTrue(row.pendingUpload)
                assertEquals(1250L, row.verifiedAmountMinor)
                assertEquals(2L, row.revision)
                assertEquals("Paint", db.transactionDao.getById("transaction")!!.note)
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun photoAndPdfReopenOfflineSyncRetryAndVerificationRemainOwnerScoped() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ClarityDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val auth = TestAuth()
        val files = ProofFileStore(context)
        val remote = FakeRemote()
        val importedIds = mutableListOf<String>()
        try {
            db.transactionDao.upsertWithTag(TransactionEntity("transaction", "owner", "EXPENSE", 1250, "Hobby", "Paint", 100, 1, 10))
            val repository = TransactionProofRepositoryImpl(db, files, remote, auth, Dispatchers.IO,
                ProofSyncProcessor(db, files, remote, auth, Dispatchers.IO))
            for ((extension, bytes) in listOf("pdf" to "%PDF-1.4 receipt".toByteArray(), "jpg" to byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 1))) {
                val source = File(context.cacheDir, "proof-previews/fixture-${UUID.randomUUID()}.$extension").apply { parentFile!!.mkdirs(); writeBytes(bytes) }
                try {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.proofs", source)
                    repository.attach("transaction", uri.toString()).getOrThrow()
                    val proof = repository.observeProofs("transaction").first { rows -> rows.size == importedIds.size + 1 }.single { it.id !in importedIds }
                    importedIds += proof.id
                    assertTrue(proof.pendingUpload)
                    assertFalse(proof.verifies(1250))
                    val opened = repository.open("transaction", proof.id).getOrThrow()
                    assertEquals("${context.packageName}.proofs", android.net.Uri.parse(opened.uri).authority)
                    assertEquals(proof.mimeType, context.contentResolver.getType(android.net.Uri.parse(opened.uri)))
                    assertArrayEquals(bytes, context.contentResolver.openInputStream(android.net.Uri.parse(opened.uri))!!.use { it.readBytes() })
                    source.delete() // Reopening no longer depends on the original picker URI.
                    repository.open("transaction", proof.id).getOrThrow()
                } finally { source.delete() }
            }
            val id = importedIds.first()
            repository.setVerified("transaction", id, 1250, true).getOrThrow()
            assertTrue(repository.observeProofs("transaction").first().first { it.id == id }.verifies(1250))
            db.transactionDao.upsert(db.transactionDao.getById("transaction")!!.copy(amountMinor = 1500))
            assertFalse(repository.observeProofs("transaction").first().first { it.id == id }.verifies(1500))
            assertTrue(repository.setVerified("transaction", id, 1250, true).isFailure)
            repository.setVerified("transaction", id, 1500, false).getOrThrow()
            remote.offline = false
            repository.refresh("transaction").getOrThrow()
            assertEquals(2, remote.rows.size)
            assertEquals(2, remote.mediaUploads)
            repository.setVerified("transaction", id, 1500, true).getOrThrow()
            files.file("owner", id).delete()
            repository.refresh("transaction").getOrThrow()
            assertEquals(2, remote.mediaUploads)
            assertEquals(1, remote.metadataUpdates)
            assertEquals(1500L, remote.rows.getValue(id).verifiedAmountMinor)
            assertTrue(repository.observeProofs("transaction").first().none { it.pendingUpload })
            files.file("owner", id).delete()
            repository.open("transaction", id).getOrThrow() // Restored from uploaded object.
            auth.currentUser.value = User("other", null, null)
            assertTrue(repository.observeProofs("transaction").first().isEmpty())
            assertTrue(repository.open("transaction", id).isFailure)
            assertTrue(repository.attach("transaction", "content://invalid").isFailure)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            importedIds.forEach { files.file("owner", it).delete() }
            db.close()
        }
    }

    @Test fun backgroundUploadsRetryWithoutLosingVerificationAndIgnoreDeletedTransactions() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ClarityDatabase::class.java).build()
        val files = ProofFileStore(context)
        val id = UUID.randomUUID().toString()
        try {
            db.transactionDao.upsert(TransactionEntity("transaction", "owner", "EXPENSE", 1250, "Hobby", "Paint", 100, 1, 10))
            val row = TransactionProofEntity("owner", "transaction", id, "Receipt.pdf", "application/pdf", 8, 1, 1250, true, 2)
            files.file("owner", id).writeText("%PDF-1.4")
            db.transactionProofDao.upsert(row)
            val auth = TestAuth()
            val remote = FakeRemote()
            val processor = ProofSyncProcessor(db, files, remote, auth, Dispatchers.IO)
            assertFalse(processor.syncPending("owner"))
            assertTrue(db.transactionProofDao.get("owner", "transaction", id)!!.pendingUpload)
            remote.offline = false
            assertTrue(processor.syncPending("owner"))
            assertEquals(1250L, remote.rows.getValue(id).verifiedAmountMinor)
            assertFalse(db.transactionProofDao.get("owner", "transaction", id)!!.pendingUpload)
            db.transactionProofDao.upsert(row.copy(revision = 3))
            db.transactionDao.upsert(db.transactionDao.getById("transaction")!!.copy(isDeleted = true))
            assertTrue(db.transactionProofDao.pending("owner").isEmpty())
            assertTrue(processor.syncPending("owner"))
            assertEquals(2L, remote.rows.getValue(id).revision)
        } finally { files.file("owner", id).delete(); db.close() }
    }

    @Test fun uploadAcknowledgementCannotClearANewerVerificationEdit() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ClarityDatabase::class.java).build()
        try {
            val row = TransactionProofEntity("owner", "transaction", "proof", "Receipt.pdf", "application/pdf", 100, 1, null, true, 1)
            db.transactionProofDao.upsert(row)
            db.transactionProofDao.upsert(row.copy(verifiedAmountMinor = 1250, revision = 2))
            db.transactionProofDao.markUnavailable("owner", "transaction", "proof", 1)
            assertFalse(db.transactionProofDao.get("owner", "transaction", "proof")!!.unavailable)
            db.transactionProofDao.acknowledge("owner", "transaction", "proof", 1)
            assertTrue(db.transactionProofDao.get("owner", "transaction", "proof")!!.pendingUpload)
            db.transactionProofDao.acknowledge("owner", "transaction", "proof", 2)
            assertFalse(db.transactionProofDao.get("owner", "transaction", "proof")!!.pendingUpload)
        } finally { db.close() }
    }

    @Test fun missingOriginalIsRecoverableAndDoesNotRetryForever() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ClarityDatabase::class.java).build()
        val files = ProofFileStore(context)
        try {
            db.transactionDao.upsert(TransactionEntity("transaction", "owner", "EXPENSE", 1250, "Hobby", "Paint", 100, 1, 10))
            val row = TransactionProofEntity("owner", "transaction", UUID.randomUUID().toString(), "Missing.pdf", "application/pdf", 8, 1, 1250, true, 1)
            db.transactionProofDao.upsert(row)
            val remote = FakeRemote().apply { offline = false }
            val auth = TestAuth()
            val processor = ProofSyncProcessor(db, files, remote, auth, Dispatchers.IO)
            assertTrue(processor.syncPending("owner"))
            val missing = db.transactionProofDao.get("owner", "transaction", row.id)!!
            assertTrue(missing.unavailable)
            assertFalse(missing.pendingUpload)
            assertNull(missing.verifiedAmountMinor)
            assertTrue(db.transactionProofDao.pending("owner").isEmpty())
            assertTrue(processor.syncPending("owner"))
            val repository = TransactionProofRepositoryImpl(db, files, remote, auth, Dispatchers.IO, processor)
            assertTrue(repository.setVerified("transaction", row.id, 1250, true).isFailure)
            repository.removeUnavailable("transaction", row.id).getOrThrow()
            assertNull(db.transactionProofDao.get("owner", "transaction", row.id))
            assertEquals(1250L, db.transactionDao.getById("transaction")!!.amountMinor)
        } finally { db.close() }
    }

    private class FakeRemote : ProofRemoteDataSource {
        @Volatile var offline = true
        var mediaUploads = 0
        var metadataUpdates = 0
        val rows = mutableMapOf<String, TransactionProofEntity>()
        private val contents = mutableMapOf<String, ByteArray>()
        override suspend fun upload(proof: TransactionProofEntity, file: File) {
            check(!offline) { "Offline" }
            mediaUploads++
            rows[proof.id] = proof.copy(pendingUpload = false)
            contents[proof.id] = file.readBytes()
        }
        override suspend fun updateMetadata(proof: TransactionProofEntity): Boolean {
            check(!offline) { "Offline" }
            if (proof.id !in rows) return false
            metadataUpdates++
            rows[proof.id] = proof.copy(pendingUpload = false)
            return true
        }
        override suspend fun list(uid: String, transactionId: String): List<TransactionProofEntity> {
            check(!offline) { "Offline" }
            return rows.values.filter { it.userId == uid && it.transactionId == transactionId }
        }
        override suspend fun download(proof: TransactionProofEntity, destination: File) { destination.writeBytes(contents.getValue(proof.id)) }
    }
    private class TestAuth : AuthRepository {
        override val currentUser = MutableStateFlow<User?>(User("owner", null, null))
        override suspend fun signInWithEmailPassword(email: String, password: String): Result<User> = error("Unused")
        override suspend fun signUpWithEmailPassword(email: String, password: String): Result<User> = error("Unused")
        override suspend fun signInWithGoogle(idToken: String): Result<User> = error("Unused")
        override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = error("Unused")
        override suspend fun signOut() { currentUser.value = null }
    }
}
