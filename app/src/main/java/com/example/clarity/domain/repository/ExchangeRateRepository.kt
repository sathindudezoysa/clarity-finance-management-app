package com.example.clarity.domain.repository

import com.example.clarity.domain.model.ExchangeRate

interface ExchangeRateRepository {
    suspend fun cached(source: String, target: String): ExchangeRate?
    suspend fun latest(source: String, target: String): ExchangeRate
}
