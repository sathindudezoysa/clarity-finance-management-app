package com.example.clarity.presentation.accounts

import com.example.clarity.R
import com.example.clarity.domain.model.AccountError
import com.example.clarity.domain.model.AccountException

internal fun accountErrorResource(throwable: Throwable?, fallback: Int): Int {
    if (throwable !is AccountException) return fallback
    return when (throwable.error) {
        AccountError.SIGN_IN_REQUIRED, AccountError.OWNER_CHANGE -> R.string.accounts_sign_in
        AccountError.NAME_BLANK -> R.string.accounts_name_blank
        AccountError.NAME_DUPLICATE -> R.string.accounts_name_duplicate
        AccountError.FIELD_TOO_LONG -> R.string.accounts_field_long
        AccountError.CURRENCY_INVALID -> R.string.accounts_currency_invalid
        AccountError.DEFAULT_REQUIRED -> R.string.accounts_default_required
        AccountError.NOT_FOUND -> R.string.accounts_not_found
        AccountError.ALREADY_EXISTS -> R.string.accounts_already_exists
    }
}
