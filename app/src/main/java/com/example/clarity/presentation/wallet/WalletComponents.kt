package com.example.clarity.presentation.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.clarity.R
import com.example.clarity.domain.model.WalletBankAccount
import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale

fun walletMoney(minor: BigInteger): String = "Rs. " + NumberFormat.getNumberInstance(Locale("en", "LK")).apply {
    minimumFractionDigits = 2; maximumFractionDigits = 2
}.format(BigDecimal(minor, 2))
fun walletMoney(minor: Long) = walletMoney(BigInteger.valueOf(minor))

@Composable
fun BankMark(modifier: Modifier = Modifier) {
    Icon(painterResource(R.drawable.ic_bank), contentDescription = null, modifier = modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
}

@Composable
fun BankAccountLabel(bank: WalletBankAccount) {
    Text(bank.bankName, style = MaterialTheme.typography.titleMedium)
    Text(bank.name + if (bank.lastFour.isBlank()) "" else " •••• ${bank.lastFour}")
    Text(walletMoney(bank.balanceMinor), style = MaterialTheme.typography.bodyLarge)
    if (!bank.isActive) Text("Archived", color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (bank.isDefault) Text("Default account", color = MaterialTheme.colorScheme.primary)
    if (bank.needsBankReview) Text("Choose this account's bank when you next edit it.", style = MaterialTheme.typography.bodySmall)
}
