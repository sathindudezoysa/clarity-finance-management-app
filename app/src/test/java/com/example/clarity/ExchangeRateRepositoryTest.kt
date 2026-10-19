package com.example.clarity

import com.example.clarity.data.local.dao.CurrencyDao
import com.example.clarity.data.local.entity.*
import com.example.clarity.data.remote.ExchangeRateRemoteDataSource
import com.example.clarity.data.repository.ExchangeRateRepositoryImpl
import com.example.clarity.domain.model.ExchangeRate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ExchangeRateRepositoryTest {
    private val now = 1_700_000_000_000L
    private fun quote(age: Long = 1000) = ExchangeRate("USD", "LKR", "300", "ExchangeRate-API", now - age, now - age, now + 1000)
    private class MemoryDao : CurrencyDao() {
        var rate: ExchangeRateEntity? = null
        override suspend fun rate(source: String, target: String) = rate?.takeIf { it.source == source && it.target == target }
        override suspend fun putRates(rates: List<ExchangeRateEntity>) { rate = rates.first() }
        override suspend fun preference(uid: String): CurrencyPreferenceEntity? = null
        override fun observePreference(uid: String) = flowOf<CurrencyPreferenceEntity?>(null)
        override suspend fun putPreference(preference: CurrencyPreferenceEntity) = Unit
        fun put(rate: ExchangeRate) { this.rate = ExchangeRateEntity(rate.source, rate.target, rate.rate, rate.provider, rate.rateAt, rate.fetchedAt, rate.nextUpdateAt) }
    }

    @Test fun fetchPersistsQuoteAndFreshCacheAvoidsAnotherRequest() = runBlocking {
        val dao = MemoryDao(); var requests = 0
        val remote = object : ExchangeRateRemoteDataSource {
            override suspend fun fetch(source: String, now: Long) = listOf(quote()).also { requests++ }
        }
        val repo = ExchangeRateRepositoryImpl(dao, remote, Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC))
        assertFalse(repo.latest("USD", "LKR").cached)
        assertTrue(repo.latest("USD", "LKR").cached)
        assertEquals(1, requests)
        assertEquals("300", dao.rate!!.rate)
        repo.latest("JPY", "JPY")
        assertEquals(1, requests)
    }

    @Test fun offlineCacheHasASevenDayLimitAndFailuresAreThrottled() = runBlocking {
        val dao = MemoryDao().apply { put(quote(2 * ExchangeRate.DAY).copy(nextUpdateAt = now - ExchangeRate.DAY)) }
        var requests = 0
        val remote = object : ExchangeRateRemoteDataSource {
            override suspend fun fetch(source: String, now: Long): List<ExchangeRate> { requests++; error("HTTP 429") }
        }
        val repo = ExchangeRateRepositoryImpl(dao, remote, Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC))
        assertTrue(repo.latest("USD", "LKR").cached)
        assertTrue(repo.latest("USD", "LKR").cached)
        assertEquals(1, requests)
        dao.put(quote(8 * ExchangeRate.DAY))
        assertNull(repo.cached("USD", "LKR"))
        assertTrue(runCatching { repo.latest("USD", "LKR") }.isFailure)
    }

    @Test fun missingQuotesAndCancellationDoNotCreateCacheEntries() = runBlocking {
        for (cancel in listOf(false, true)) {
            val dao = MemoryDao()
            val remote = object : ExchangeRateRemoteDataSource {
                override suspend fun fetch(source: String, now: Long): List<ExchangeRate> {
                    if (cancel) throw CancellationException("Cancelled")
                    return emptyList()
                }
            }
            val repo = ExchangeRateRepositoryImpl(dao, remote, Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC))
            val failure = runCatching { repo.latest("USD", "LKR") }.exceptionOrNull()
            assertNotNull(failure)
            if (cancel) assertTrue(failure is CancellationException)
            assertNull(dao.rate)
        }
    }
}
