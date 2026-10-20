package com.example.clarity.domain.usecase.budget

import com.example.clarity.domain.model.Budget
import com.example.clarity.domain.repository.BudgetRepository
import javax.inject.Inject

class ObserveBudgetsUseCase @Inject constructor(private val repository: BudgetRepository) {
    operator fun invoke() = repository.observe()
}
class SaveBudgetUseCase @Inject constructor(private val repository: BudgetRepository) {
    suspend operator fun invoke(budget: Budget) = repository.save(budget)
}
class DisableBudgetUseCase @Inject constructor(private val repository: BudgetRepository) {
    suspend operator fun invoke(id: String) = repository.disable(id)
}
class ResolveBudgetCurrencyUseCase @Inject constructor(private val repository: BudgetRepository) {
    suspend operator fun invoke(id: String) = repository.resolveUncounted(id)
}
