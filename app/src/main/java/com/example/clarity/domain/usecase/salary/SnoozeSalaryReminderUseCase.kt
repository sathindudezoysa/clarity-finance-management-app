package com.example.clarity.domain.usecase.salary

import com.example.clarity.domain.repository.SalaryReminderRepository
import javax.inject.Inject

class SnoozeSalaryReminderUseCase @Inject constructor(private val repository: SalaryReminderRepository) {
    suspend operator fun invoke() = repository.snooze()
}
