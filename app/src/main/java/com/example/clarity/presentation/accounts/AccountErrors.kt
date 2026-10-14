package com.example.clarity.presentation.accounts

import com.example.clarity.R

// Repository messages are matched as error identifiers; displayed text comes from resources.
internal fun accountErrorResource(message: String?, fallback: Int): Int = when (message) {
    "Please sign in", "Cannot change account owner" -> R.string.accounts_sign_in
    "Account name must not be blank" -> R.string.accounts_name_blank
    "An account with this name already exists" -> R.string.accounts_name_duplicate
    "Account field is too long" -> R.string.accounts_field_long
    "Currency must be a three-letter uppercase code" -> R.string.accounts_currency_invalid
    "Select another default account first" -> R.string.accounts_default_required
    "Account not found" -> R.string.accounts_not_found
    "Account already exists" -> R.string.accounts_already_exists
    else -> fallback
}
