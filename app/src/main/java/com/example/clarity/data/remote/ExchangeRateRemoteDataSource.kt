package com.example.clarity.data.remote

import com.example.clarity.domain.model.*
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.inject.Inject

interface ExchangeRateRemoteDataSource {
    suspend fun fetch(source: String, now: Long): List<ExchangeRate>
}

class HttpsExchangeRateRemoteDataSource @Inject constructor(
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: java.time.Clock
) : ExchangeRateRemoteDataSource {
    override suspend fun fetch(source: String, now: Long): List<ExchangeRate> = withContext(ioDispatcher) {
        Currencies.digits(source)
        val connection = URL("https://open.er-api.com/v6/latest/$source").openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            require(connection.responseCode == 200) { "Exchange rate HTTP ${connection.responseCode}" }
            val body = connection.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(65_537)
                var count = 0
                while (count < buffer.size) {
                    ensureActive()
                    val read = reader.read(buffer, count, buffer.size - count)
                    if (read < 0) break
                    count += read
                }
                require(count < buffer.size) { "Exchange rate response too large" }
                String(buffer, 0, count)
            }
            ensureActive()
            parse(body, source, clock.millis())
        } finally { connection.disconnect() }
    }

    companion object {
        // Gson's JSON number token is read as text, never as Double.
        fun parse(body: String, source: String, now: Long): List<ExchangeRate> {
            Currencies.digits(source)
            val root = JsonParser.parseString(body).asJsonObject
            require(root["result"].asString == "success" && root["base_code"].asString == source)
            require(root["time_last_update_unix"].asJsonPrimitive.isNumber && root["time_next_update_unix"].asJsonPrimitive.isNumber)
            val at = Math.multiplyExact(root["time_last_update_unix"].asBigDecimal.longValueExact(), 1000)
            val next = Math.multiplyExact(root["time_next_update_unix"].asBigDecimal.longValueExact(), 1000)
            val rates = root["rates"].asJsonObject
            require(CurrencyConversion.rate(rates[source].asString).compareTo(java.math.BigDecimal.ONE) == 0)
            return Currencies.supported.mapNotNull { target ->
                val token = rates[target] ?: return@mapNotNull null
                require(token.isJsonPrimitive && token.asJsonPrimitive.isNumber)
                val rate = CurrencyConversion.rate(token.asString).toPlainString()
                ExchangeRate(source, target, rate, "ExchangeRate-API", at, now, next).also {
                    require(it.usable(now)) { "Unusable provider timestamp" }
                }
            }
        }
    }
}
