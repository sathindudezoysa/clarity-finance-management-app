package com.example.clarity.domain.usecase.account

import com.example.clarity.domain.repository.AccountRepository
import javax.inject.Inject

class GetAccountUseCase @Inject constructor(private val repository: AccountRepository) {
    suspend operator fun invoke(id: String) = repository.getAccount(id)
}
