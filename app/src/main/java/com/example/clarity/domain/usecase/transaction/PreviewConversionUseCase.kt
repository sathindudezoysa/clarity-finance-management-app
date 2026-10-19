package com.example.clarity.domain.usecase.transaction

import com.example.clarity.domain.model.*
import com.example.clarity.domain.repository.ExchangeRateRepository
import javax.inject.Inject

class PreviewConversionUseCase @Inject constructor(private val rates: ExchangeRateRepository) {
    suspend operator fun invoke(amount: Long, source: String, target: String): ConversionSnapshot {
        if (source == target) {
            Currencies.digits(source); require(amount > 0)
            return ConversionSnapshot.identity(amount)
        }
        return CurrencyConversion.snapshot(amount, rates.latest(source, target))
    }
}
