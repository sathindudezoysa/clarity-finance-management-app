package com.example.clarity.domain.usecase.account

import com.example.clarity.domain.repository.AccountRepository
import javax.inject.Inject

class GetAccountsUseCase @Inject constructor(private val repository: AccountRepository) {
    operator fun invoke() = repository.observeAccounts()
}
