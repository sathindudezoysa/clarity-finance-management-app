package com.example.clarity.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

object Currencies {
    val supported = listOf("LKR", "USD", "EUR", "GBP", "INR", "AUD", "CAD", "SGD", "JPY", "KWD")
    fun digits(code: String): Int {
        require(code in supported) { "Unsupported currency" }
        return when (code) { "JPY" -> 0; "KWD" -> 3; else -> 2 }
    }
}

data class ConversionSnapshot(
    val amountMinor: Long,
    val rate: String,
    val provider: String,
    val rateAt: Long,
    val fetchedAt: Long,
    val cached: Boolean
) {
    companion object {
        fun identity(amountMinor: Long) = ConversionSnapshot(amountMinor, "1", "IDENTITY", 0, 0, false)
    }
}

data class ExchangeRate(
    val source: String,
    val target: String,
    val rate: String,
    val provider: String,
    val rateAt: Long,
    val fetchedAt: Long,
    val nextUpdateAt: Long,
    val cached: Boolean = false
) {
    fun usable(now: Long): Boolean = runCatching {
        Currencies.digits(source); Currencies.digits(target); CurrencyConversion.rate(rate)
        require(provider == "ExchangeRate-API")
        require(rateAt > 0 && rateAt <= fetchedAt && fetchedAt <= now)
        require(nextUpdateAt > rateAt && nextUpdateAt - rateAt <= 2 * DAY)
        require(now - rateAt <= 7 * DAY)
    }.isSuccess

    fun fresh(now: Long) = usable(now) && now < nextUpdateAt && now - fetchedAt < DAY
    companion object { const val DAY = 86_400_000L }
}

object CurrencyConversion {
    fun rate(text: String): BigDecimal {
        require(text.length <= 31 && text.matches(Regex("[0-9]{1,12}(?:\\.[0-9]{1,18})?"))) { "Malformed rate" }
        return BigDecimal(text).also { require(it.signum() > 0 && it.precision() <= 30 && it.scale() <= 18) { "Invalid rate" } }
    }

    fun convert(amount: Long, source: String, target: String, rateText: String): Long {
        require(amount > 0) { "Amount must be positive" }
        val result = BigDecimal.valueOf(amount, Currencies.digits(source)).multiply(rate(rateText))
            .movePointRight(Currencies.digits(target)).setScale(0, RoundingMode.HALF_UP).longValueExact()
        require(result > 0) { "Converted amount rounds to zero" }
        return result
    }

    fun snapshot(amount: Long, quote: ExchangeRate) = ConversionSnapshot(
        convert(amount, quote.source, quote.target, quote.rate), quote.rate, quote.provider,
        quote.rateAt, quote.fetchedAt, quote.cached
    )

    fun validate(transaction: Transaction) {
        Currencies.digits(transaction.currency); Currencies.digits(transaction.targetCurrency)
        require(transaction.amountMinor > 0)
        val snapshot = transaction.conversion
        require(transaction.pendingConversion == (snapshot == null))
        if (transaction.currency == transaction.targetCurrency) {
            require(snapshot == ConversionSnapshot.identity(transaction.amountMinor))
        } else if (snapshot != null) {
            require(snapshot.provider == "ExchangeRate-API" && snapshot.rateAt > 0 && snapshot.fetchedAt >= snapshot.rateAt)
            require(snapshot.fetchedAt <= transaction.updatedAt)
            require(convert(transaction.amountMinor, transaction.currency, transaction.targetCurrency, snapshot.rate) == snapshot.amountMinor)
        }
    }
}
