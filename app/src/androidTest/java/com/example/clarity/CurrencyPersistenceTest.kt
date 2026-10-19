package com.example.clarity

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.local.entity.*
import com.example.clarity.data.mapper.*
import com.example.clarity.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CurrencyPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "currency-test-${java.util.UUID.randomUUID()}"
    private var database: ClarityDatabase? = null
    private fun open() = Room.databaseBuilder(context, ClarityDatabase::class.java, name).build().also { database = it }
    private val now = 1_700_000_000_000L
    private fun pending() = Transaction(id = "entry", userId = "alice", amountMinor = 1250, createdAt = now, updatedAt = now,
        currency = "USD", targetCurrency = "LKR").toEntity().copy(pendingSync = true)
    private val snapshot = ConversionSnapshot(375000, "300", "ExchangeRate-API", now - 1000, now, false)

    @After fun cleanup() { database?.close(); context.deleteDatabase(name) }

    @Test fun cachePendingOutboxAndPreferencesSurviveReopeningAndRemainUserScoped() = runBlocking {
        var db = open()
        val pending = pending()
        val rate = ExchangeRateEntity("USD", "LKR", "300", "ExchangeRate-API", now - 1000, now, now + 1000)
        db.transactionDao.persistLocal(pending)
        db.currencyDao.putRates(listOf(rate))
        db.currencyDao.editPreference("alice", "JPY", now)
        db.currencyDao.editPreference("bob", "KWD", now)
        db.close(); db = open()
        assertEquals(pending, db.transactionDao.observeAll("alice").first().single())
        assertEquals(pending, db.transactionDao.pendingConversions("alice").single())
        assertEquals(rate, db.currencyDao.rate("USD", "LKR"))
        assertEquals("JPY", db.currencyDao.preference("alice")!!.currency)
        assertEquals("KWD", db.currencyDao.preference("bob")!!.currency)
        assertNull(db.currencyDao.preference("charlie"))
        assertTrue(db.currencyDao.preference("alice")!!.pendingSync)
        assertTrue(db.transactionDao.pendingConversions("bob").isEmpty())
        assertTrue(db.transactionDao.resolveConversion(pending, snapshot, now + 1))
        db.close(); db = open()
        val resolved = db.transactionDao.getById("entry")!!
        assertEquals(snapshot, resolved.toDomain().conversion)
        assertFalse(resolved.pendingConversion)
        assertTrue(resolved.pendingSync)
        assertEquals(1250L, resolved.amountMinor)
        assertEquals(resolved.toDomain(), resolved.toDomain().toFirestoreMap().toTransaction("entry"))
        assertFalse(db.transactionDao.resolveConversion(pending, snapshot.copy(rate = "400"), now + 2))
    }

    @Test fun lateConversionsCannotOverwriteEditsDeletesOwnerChangesOrReconciliation() = runBlocking {
        val db = open(); val request = pending()
        val replacements = listOf(
            request.copy(updatedAt = now + 1, note = "Edited"),
            request.copy(amountMinor = 2000), request.copy(currency = "EUR"), request.copy(targetCurrency = "JPY"),
            request.copy(isDeleted = true), request.copy(userId = "bob"),
            request.copy(conversion = snapshot, pendingConversion = false)
        )
        for (replacement in replacements) {
            db.transactionDao.upsert(replacement)
            assertFalse(db.transactionDao.resolveConversion(request, snapshot, now + 1))
            assertEquals(replacement, db.transactionDao.getById("entry"))
        }
        db.transactionDao.deleteById("entry", "alice")
        assertFalse(db.transactionDao.resolveConversion(request, snapshot, now + 1))
    }

    @Test fun preferenceAcknowledgementCannotEraseANewerOfflineEdit() = runBlocking {
        val db = open()
        db.currencyDao.editPreference("alice", "USD", now)
        val sent = db.currencyDao.preference("alice")!!
        db.currencyDao.editPreference("alice", "JPY", now)
        db.currencyDao.reconcilePreference(sent.copy(pendingSync = false), sent)
        assertEquals("JPY", db.currencyDao.preference("alice")!!.currency)
        assertTrue(db.currencyDao.preference("alice")!!.pendingSync)
    }
}
