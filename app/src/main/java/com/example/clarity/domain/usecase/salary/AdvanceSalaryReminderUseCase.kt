package com.example.clarity.domain.usecase.salary

import com.example.clarity.domain.model.SalaryReminder
import com.example.clarity.domain.repository.SalaryReminderRepository
import javax.inject.Inject

/** Called when a reminder fires: returns the reminder to notify about and schedules the following month. */
class AdvanceSalaryReminderUseCase @Inject constructor(private val repository: SalaryReminderRepository) {
    suspend operator fun invoke(): SalaryReminder? {
        val reminder = repository.get() ?: return null
        repository.advance()
        return reminder
    }
}
