package com.example.clarity.data.repository

import com.example.clarity.data.local.dao.CurrencyDao
import com.example.clarity.data.local.entity.ExchangeRateEntity
import com.example.clarity.data.remote.ExchangeRateRemoteDataSource
import com.example.clarity.domain.model.*
import com.example.clarity.domain.repository.ExchangeRateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExchangeRateRepositoryImpl @Inject constructor(
    private val dao: CurrencyDao,
    private val remote: ExchangeRateRemoteDataSource,
    private val clock: Clock
) : ExchangeRateRepository {
    private val mutex = Mutex()
    private val failedUntil = mutableMapOf<String, Long>()

    override suspend fun cached(source: String, target: String): ExchangeRate? {
        Currencies.digits(source); Currencies.digits(target)
        if (source == target) return identity(source)
        return dao.rate(source, target)?.let {
            ExchangeRate(it.source, it.target, it.rate, it.provider, it.rateAt, it.fetchedAt, it.nextUpdateAt, true)
        }?.takeIf { it.usable(clock.millis()) }
    }

    override suspend fun latest(source: String, target: String): ExchangeRate = mutex.withLock {
        if (source == target) { Currencies.digits(source); return@withLock identity(source) }
        val cached = cached(source, target)
        val now = clock.millis()
        if (cached?.fresh(now) == true) return@withLock cached
        try {
            require(now >= (failedUntil[source] ?: 0)) { "Exchange rate retry delayed" }
            val quotes = remote.fetch(source, now)
            require(quotes.all { it.source == source && it.usable(clock.millis()) })
            val selected = quotes.firstOrNull { it.target == target } ?: error("Currency missing from provider response")
            dao.putRates(quotes.map { ExchangeRateEntity(it.source, it.target, it.rate, it.provider, it.rateAt, it.fetchedAt, it.nextUpdateAt) })
            selected
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            failedUntil[source] = now + 30 * 60_000
            cached?.takeIf { it.usable(clock.millis()) } ?: throw error
        }
    }

    private fun identity(code: String) = ExchangeRate(code, code, "1", "IDENTITY", 0, 0, Long.MAX_VALUE)
}
