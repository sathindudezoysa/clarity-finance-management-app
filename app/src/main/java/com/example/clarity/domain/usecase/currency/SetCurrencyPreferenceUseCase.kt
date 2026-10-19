package com.example.clarity.domain.usecase.currency

import com.example.clarity.domain.repository.CurrencyPreferenceRepository
import javax.inject.Inject

class SetCurrencyPreferenceUseCase @Inject constructor(private val repository: CurrencyPreferenceRepository) {
    suspend operator fun invoke(currency: String) = repository.set(currency)
}
