package com.example.clarity.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exchange_rates", primaryKeys = ["source", "target"])
data class ExchangeRateEntity(
    val source: String, val target: String, val rate: String, val provider: String,
    val rateAt: Long, val fetchedAt: Long, val nextUpdateAt: Long
)

@Entity(tableName = "currency_preferences")
data class CurrencyPreferenceEntity(
    @PrimaryKey val userId: String,
    val currency: String,
    val updatedAt: Long,
    val pendingSync: Boolean
)
