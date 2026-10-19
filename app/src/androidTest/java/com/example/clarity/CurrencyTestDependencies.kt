package com.example.clarity

import com.example.clarity.domain.repository.*
import com.example.clarity.domain.model.ExchangeRate
import kotlinx.coroutines.flow.flowOf

internal object TestCurrencies {
    val preferences = object : CurrencyPreferenceRepository {
        override fun observe() = flowOf(CurrencyPreference())
        override suspend fun set(currency: String) = Result.success(Unit)
        override suspend fun current() = "LKR"
    }
    val rates = object : ExchangeRateRepository {
        override suspend fun cached(source: String, target: String): ExchangeRate? = null
        override suspend fun latest(source: String, target: String): ExchangeRate = error("Network disabled in this test")
    }
    fun scheduler() = com.example.clarity.data.sync.CurrencyWorkScheduler(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext)
}
