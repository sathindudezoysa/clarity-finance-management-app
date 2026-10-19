package com.example.clarity.domain.repository

import kotlinx.coroutines.flow.Flow

data class CurrencyPreference(val currency: String = "LKR", val pendingSync: Boolean = false)

interface CurrencyPreferenceRepository {
    fun observe(): Flow<CurrencyPreference>
    suspend fun set(currency: String): Result<Unit>
    suspend fun current(): String
}
