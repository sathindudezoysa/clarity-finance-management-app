package com.example.clarity

import com.example.clarity.data.mapper.*
import com.example.clarity.data.remote.HttpsExchangeRateRemoteDataSource
import com.example.clarity.domain.model.*
import com.example.clarity.domain.repository.ExchangeRateRepository
import com.example.clarity.domain.usecase.transaction.PreviewConversionUseCase
import com.example.clarity.domain.util.MoneyUtils
import com.example.clarity.presentation.transactions.list.TransactionListState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class CurrencyTest {
    private val now = 1_700_000_000_000L
    private fun quote(source: String = "USD", target: String = "LKR", rate: String = "300.125") =
        ExchangeRate(source, target, rate, "ExchangeRate-API", now - 1000, now, now + ExchangeRate.DAY)

    @Test fun directionFractionDigitsAndRoundingAreExact() {
        assertEquals(375156L, CurrencyConversion.convert(1250, "USD", "LKR", "300.125"))
        assertEquals(2L, CurrencyConversion.convert(100, "USD", "JPY", "1.5"))
        assertEquals(1235L, CurrencyConversion.convert(100, "USD", "KWD", "1.2345"))
        assertEquals(100L, CurrencyConversion.convert(1000, "KWD", "USD", "1"))
        assertEquals(100L, CurrencyConversion.convert(1, "JPY", "USD", "1"))
        assertEquals("1.234", MoneyUtils.formatMinor(1234, "KWD"))
        assertEquals(1234L, MoneyUtils.parseMinor("1.234", "KWD").getOrThrow())
        assertEquals(123L, MoneyUtils.parseMinor("123", "JPY").getOrThrow())
        assertTrue(MoneyUtils.parseMinor("1.1", "JPY").isFailure)
        assertTrue(MoneyUtils.parseMinor("1.234", "USD").isFailure)
        assertTrue(MoneyUtils.parseMinor("9223372036854775808", "JPY").isFailure)
        assertEquals("12.50", MoneyUtils.formatMinor(1250))
    }

    @Test fun rejectsInvalidRatesAmountsCodesOverflowAndZeroRounding() {
        for (rate in listOf("0", "-1", "NaN", "Infinity", "1e2", "", "1.0000000000000000001", "9".repeat(41))) {
            assertTrue(rate, runCatching { CurrencyConversion.convert(100, "USD", "LKR", rate) }.isFailure)
        }
        for (amount in listOf(0L, -1L, Long.MIN_VALUE)) {
            assertTrue(runCatching { CurrencyConversion.convert(amount, "USD", "LKR", "1") }.isFailure)
        }
        assertTrue(runCatching { CurrencyConversion.convert(Long.MAX_VALUE, "USD", "LKR", "2") }.isFailure)
        assertTrue(runCatching { CurrencyConversion.convert(1, "USD", "JPY", "0.001") }.isFailure)
        assertTrue(runCatching { CurrencyConversion.convert(100, "ZZZ", "USD", "1") }.isFailure)
    }

    @Test fun sameCurrencyPreviewNeverCallsTheNetwork() = runBlocking {
        val usecase = PreviewConversionUseCase(object : ExchangeRateRepository {
            override suspend fun cached(source: String, target: String): ExchangeRate? = error("No cache needed")
            override suspend fun latest(source: String, target: String): ExchangeRate = error("No network needed")
        })
        assertEquals(ConversionSnapshot.identity(100), usecase(100, "JPY", "JPY"))
    }

    @Test fun providerParsingRetainsDecimalTokensAndValidatesBaseTimesAndMissingRates() {
        fun response(base: String = "USD", timestamp: Long = (now - 1000) / 1000, rate: String = "300.123456789012345678") =
            """{"result":"success","base_code":"$base","time_last_update_unix":$timestamp,"time_next_update_unix":${(now + ExchangeRate.DAY) / 1000},"rates":{"USD":1,"LKR":$rate}}"""
        val quotes = HttpsExchangeRateRemoteDataSource.parse(response(), "USD", now)
        assertEquals("300.123456789012345678", quotes.single { it.target == "LKR" }.rate)
        assertFalse(quotes.any { it.target == "JPY" })
        for (body in listOf("{}", "invalid", response(base = "EUR"), response(timestamp = (now + 1000) / 1000),
            response(rate = "0"), response(rate = "null"))) {
            assertTrue(body, runCatching { HttpsExchangeRateRemoteDataSource.parse(body, "USD", now) }.isFailure)
        }
    }

    @Test fun freshnessAndUsabilityHaveExplicitBoundaries() {
        val rate = quote()
        assertTrue(rate.fresh(now))
        assertFalse(rate.fresh(now + ExchangeRate.DAY))
        assertTrue(rate.usable(now + 6 * ExchangeRate.DAY))
        assertFalse(rate.usable(now + 8 * ExchangeRate.DAY))
        assertFalse(rate.usable(now - 1))
        assertFalse(rate.copy(rateAt = 0).usable(now))
        assertFalse(rate.copy(nextUpdateAt = rate.rateAt).usable(now))
    }

    @Test fun originalAndConversionRoundTripWithoutOutboxMetadataAndLegacyMeansLkr() {
        val transaction = Transaction(id = "entry", userId = "owner", amountMinor = 1250, createdAt = now, updatedAt = now,
            currency = "USD", targetCurrency = "LKR", conversion = CurrencyConversion.snapshot(1250, quote()))
        assertEquals(transaction, transaction.toEntity().toDomain())
        assertEquals(transaction, transaction.toFirestoreMap().toTransaction(transaction.id))
        val pending = transaction.copy(conversion = null, pendingConversion = true)
        assertEquals(pending, pending.toEntity().toDomain())
        assertEquals(pending, pending.toFirestoreMap().toTransaction(pending.id))
        assertFalse(transaction.toFirestoreMap().containsKey("pendingSync"))
        val legacyMap = transaction.toFirestoreMap() - setOf("currency", "targetCurrency", "conversion", "pendingConversion")
        val legacy = checkNotNull(legacyMap.toTransaction(transaction.id))
        assertEquals("LKR", legacy.currency)
        assertEquals(ConversionSnapshot.identity(1250), legacy.conversion)
        for (field in listOf("currency", "targetCurrency", "conversion", "pendingConversion")) {
            assertNull(transaction.toFirestoreMap().minus(field).toTransaction(transaction.id))
        }
        assertNull((transaction.toFirestoreMap() + ("conversion" to null)).toTransaction(transaction.id))
        assertNull((transaction.toFirestoreMap() + ("currency" to "ZZZ")).toTransaction(transaction.id))
    }

    @Test fun totalsGroupStoredTargetsAndExcludePendingWithoutRelabellingHistory() {
        val lkr = Transaction(amountMinor = 100, currency = "LKR", targetCurrency = "LKR", type = TransactionType.INCOME)
        val usd = Transaction(amountMinor = 200, currency = "USD", targetCurrency = "USD", type = TransactionType.EXPENSE)
        val pending = Transaction(amountMinor = Long.MAX_VALUE, currency = "JPY", targetCurrency = "LKR")
        val state = TransactionListState(transactions = listOf(lkr, usd, pending))
        assertEquals(1, state.pendingCount)
        assertEquals(setOf("LKR", "USD"), state.totalsByCurrency.keys)
        assertEquals(BigInteger.valueOf(100), state.totalsByCurrency.getValue("LKR").income)
        assertEquals(BigInteger.valueOf(200), state.totalsByCurrency.getValue("USD").expense)
    }
}
