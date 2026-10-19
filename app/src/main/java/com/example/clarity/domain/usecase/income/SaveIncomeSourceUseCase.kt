package com.example.clarity.domain.usecase.income

import com.example.clarity.domain.repository.IncomeSourceRepository
import com.example.clarity.domain.model.IncomeSource
import javax.inject.Inject

class SaveIncomeSourceUseCase @Inject constructor(private val repository: IncomeSourceRepository) {
    suspend operator fun invoke(source: IncomeSource) = repository.save(source)
}
