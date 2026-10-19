package com.example.clarity.presentation.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.example.clarity.domain.model.*
import com.example.clarity.domain.util.MoneyUtils
import java.time.Instant

@Composable
fun CurrencyPicker(value: String, onSelected: (String) -> Unit, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
            Text("Currency: $value")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Currencies.supported.forEach { code ->
                DropdownMenuItem(text = { Text(code) }, onClick = { expanded = false; onSelected(code) })
            }
        }
    }
}

@Composable
fun ConversionInfo(target: String, snapshot: ConversionSnapshot?, pending: Boolean) {
    Column {
    if (pending) Text("Conversion to $target pending. The original entry is saved; conversion resumes with connectivity. Unresolved entries are excluded from totals.")
    snapshot?.let {
        Text("Reporting equivalent: $target ${MoneyUtils.formatMinor(it.amountMinor, target)}")
        if (it.provider != "IDENTITY") {
            Text((if (it.cached) "Cached source-to-target rate " else "Source-to-target rate ") +
                "${it.rate} · Provider time ${Instant.ofEpochMilli(it.rateAt)} · Fetched ${Instant.ofEpochMilli(it.fetchedAt)}")
            RateAttribution()
        }
    }
    }
}

@Composable
fun RateAttribution() {
    val uriHandler = LocalUriHandler.current
    TextButton(onClick = { uriHandler.openUri("https://www.exchangerate-api.com") }) {
        Text("Rates By Exchange Rate API")
    }
}
