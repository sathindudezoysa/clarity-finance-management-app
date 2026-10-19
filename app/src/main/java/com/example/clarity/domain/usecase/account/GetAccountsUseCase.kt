package com.example.clarity.domain.usecase.account

import com.example.clarity.domain.model.Account
import com.example.clarity.domain.repository.AccountRepository
import com.example.clarity.domain.repository.CurrencyPreferenceRepository
import com.example.clarity.domain.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class GetAccountsUseCase @Inject constructor(
    private val repository: AccountRepository,
    private val currencyPreferences: CurrencyPreferenceRepository
) {
    operator fun invoke(): Flow<Resource<List<Account>>> = combine(
        repository.observeAccounts(),
        currencyPreferences.observe()
    ) { result, pref ->
        val accounts = result.data
        if (result is Resource.Success && accounts != null) {
            val cashWallet = accounts.firstOrNull { it.isCashWallet }
            if (cashWallet == null) {
                repository.createAccount(Account.defaultCashWallet(currency = pref.currency, isDefault = accounts.isEmpty()))
            } else if (cashWallet.currency != pref.currency) {
                repository.updateAccount(cashWallet.copy(currency = pref.currency))
            }
        }
        result
    }
}
