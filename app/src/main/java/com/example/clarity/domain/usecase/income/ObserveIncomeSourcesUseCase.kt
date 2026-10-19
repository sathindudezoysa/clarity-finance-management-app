package com.example.clarity.domain.usecase.income

import com.example.clarity.domain.repository.IncomeSourceRepository
import javax.inject.Inject

class ObserveIncomeSourcesUseCase @Inject constructor(private val repository: IncomeSourceRepository) {
    operator fun invoke() = repository.observeSources()
}
