package com.example.clarity.presentation.accounts

import com.example.clarity.domain.model.AccountError
import com.example.clarity.domain.model.AccountException

object AccountMessages {
    const val NAME_BLANK = "Enter an account name."
    const val NAME_DUPLICATE = "An account with this name already exists. Choose a different name."
    const val FIELD_LONG = "Account name, bank name and type must each be at most 100 characters."
    const val CURRENCY_INVALID = "Enter a three-letter currency code."
    const val DEFAULT_REQUIRED = "Choose another default account from the list before turning this off."
    const val NOT_FOUND = "Bank account not found."
    const val ALREADY_EXISTS = "This account has already been created. Return to the account list."
    const val SIGN_IN = "Please sign in to manage bank accounts."
    const val LOAD_ERROR = "Unable to load or sync bank accounts. Please try again."
    const val OPEN_ERROR = "Unable to open this account. Please try again."
    const val SAVE_ERROR = "Unable to save this account. Please try again."
    const val DEFAULT_ERROR = "Unable to change the default account. Please try again."
}

internal fun accountErrorMessage(throwable: Throwable?, fallback: String): String {
    if (throwable !is AccountException) return fallback
    return when (throwable.error) {
        AccountError.SIGN_IN_REQUIRED, AccountError.OWNER_CHANGE -> AccountMessages.SIGN_IN
        AccountError.NAME_BLANK -> AccountMessages.NAME_BLANK
        AccountError.NAME_DUPLICATE -> AccountMessages.NAME_DUPLICATE
        AccountError.FIELD_TOO_LONG -> AccountMessages.FIELD_LONG
        AccountError.CURRENCY_INVALID -> AccountMessages.CURRENCY_INVALID
        AccountError.DEFAULT_REQUIRED -> AccountMessages.DEFAULT_REQUIRED
        AccountError.NOT_FOUND -> AccountMessages.NOT_FOUND
        AccountError.ALREADY_EXISTS -> AccountMessages.ALREADY_EXISTS
    }
}
