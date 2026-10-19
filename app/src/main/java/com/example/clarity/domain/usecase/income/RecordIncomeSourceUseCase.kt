package com.example.clarity.domain.usecase.income

import com.example.clarity.domain.repository.IncomeSourceRepository
import javax.inject.Inject

class RecordIncomeSourceUseCase @Inject constructor(private val repository: IncomeSourceRepository) {
    suspend operator fun invoke(id: String, month: String?, expectedVersion: Long) = repository.record(id, month, expectedVersion)
}
