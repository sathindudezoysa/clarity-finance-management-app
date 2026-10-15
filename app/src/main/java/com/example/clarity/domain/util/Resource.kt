package com.example.clarity.domain.util

import com.example.clarity.domain.model.AccountError
import com.example.clarity.domain.model.AccountException
import com.example.clarity.domain.model.TransactionError
import com.example.clarity.domain.model.TransactionException

sealed class Resource<T>(
    val data: T? = null,
    val message: String? = null
) {
    class Success<T>(data: T) : Resource<T>(data)
    class Error<T>(message: String, data: T? = null, val cause: Throwable? = null) : Resource<T>(data, message)
    class Loading<T>(data: T? = null) : Resource<T>(data)
}

fun Throwable?.isSignInRequired(): Boolean = when (this) {
    is AccountException -> error == AccountError.SIGN_IN_REQUIRED
    is TransactionException -> error == TransactionError.SIGN_IN_REQUIRED
    else -> false
}
