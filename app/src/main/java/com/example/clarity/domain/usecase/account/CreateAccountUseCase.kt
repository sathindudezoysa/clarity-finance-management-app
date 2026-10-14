package com.example.clarity.domain.usecase.account

import com.example.clarity.domain.model.Account
import com.example.clarity.domain.repository.AccountRepository
import javax.inject.Inject

class CreateAccountUseCase @Inject constructor(private val repository: AccountRepository) {
    suspend operator fun invoke(account: Account) = repository.createAccount(account)
}
