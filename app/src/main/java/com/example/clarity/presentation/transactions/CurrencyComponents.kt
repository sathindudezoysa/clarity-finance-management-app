package com.example.clarity.presentation.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.example.clarity.R
import com.example.clarity.domain.model.*
import com.example.clarity.domain.util.MoneyUtils
import java.time.Instant

@Composable
fun CurrencyPicker(value: String, onSelected: (String) -> Unit, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
            Text(stringResource(R.string.currency_select, value))
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
    if (pending) Text(stringResource(R.string.currency_pending, target))
    snapshot?.let {
        Text(stringResource(R.string.currency_equivalent, target, MoneyUtils.formatMinor(it.amountMinor, target)))
        if (it.provider != "IDENTITY") {
            Text(stringResource(if (it.cached) R.string.currency_cached_rate else R.string.currency_rate,
                it.rate, Instant.ofEpochMilli(it.rateAt).toString(), Instant.ofEpochMilli(it.fetchedAt).toString()))
            RateAttribution()
        }
    }
    }
}

@Composable
fun RateAttribution() {
    val uriHandler = LocalUriHandler.current
    TextButton(onClick = { uriHandler.openUri("https://www.exchangerate-api.com") }) {
        Text(stringResource(R.string.currency_attribution))
    }
}
