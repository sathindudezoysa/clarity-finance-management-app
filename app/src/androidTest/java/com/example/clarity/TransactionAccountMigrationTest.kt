package com.example.clarity

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.mapper.*
import com.example.clarity.domain.model.Transaction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Schema-changing development upgrades recreate tables; installed-schema restarts preserve data. */
@RunWith(AndroidJUnit4::class)
class TransactionAccountMigrationTest {
    @Test fun versionSevenRecreatesTablesThenPreservesCurrencyAndAccountAcrossRestart() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "currency-upgrade-${java.util.UUID.randomUUID()}"
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("CREATE TABLE transactions (id TEXT NOT NULL PRIMARY KEY, amountMinor INTEGER NOT NULL)")
            old.execSQL("INSERT INTO transactions VALUES ('unsynced', 1250)")
            old.version = 7
        }
        fun open() = Room.databaseBuilder(context, ClarityDatabase::class.java, name)
            .fallbackToDestructiveMigration().build()
        try {
            val row = Transaction(id = "new", userId = "owner", amountMinor = 1250,
                currency = "USD", targetCurrency = "LKR", accountId = "daily").toEntity().copy(pendingSync = true)
            var db = open()
            try {
                assertNull(db.transactionDao.getById("unsynced"))
                db.transactionDao.persistLocal(row)
            } finally { db.close() }
            db = open()
            try { assertEquals(row, db.transactionDao.getById("new")) } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
}
