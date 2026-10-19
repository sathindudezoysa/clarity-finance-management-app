package com.example.clarity.domain.usecase.currency

import com.example.clarity.domain.repository.CurrencyPreferenceRepository
import javax.inject.Inject

class ObserveCurrencyPreferenceUseCase @Inject constructor(private val repository: CurrencyPreferenceRepository) {
    operator fun invoke() = repository.observe()
}
