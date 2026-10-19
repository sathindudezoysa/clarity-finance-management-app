package com.example.clarity.domain.usecase.income

import com.example.clarity.domain.repository.IncomeSourceRepository
import javax.inject.Inject

class ArchiveIncomeSourceUseCase @Inject constructor(private val repository: IncomeSourceRepository) {
    suspend operator fun invoke(id: String) = repository.archive(id)
}
