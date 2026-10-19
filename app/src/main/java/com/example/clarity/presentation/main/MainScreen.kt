package com.example.clarity.presentation.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.clarity.R
import com.example.clarity.presentation.income.IncomeMonthlyPrompt

@Composable
@Preview
fun MainScreen(
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToCashWallet: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToIncome: () -> Unit = {}
) {
    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                IncomeMonthlyPrompt(onReview = onNavigateToIncome)
                Button(onClick = onNavigateToIncome) { Text(stringResource(R.string.income_title)) }
                Button(onClick = onNavigateToTransactions) {
                    Text(stringResource(R.string.transactions_title))
                }
                Button(onClick = onNavigateToCashWallet) {
                    Text(stringResource(R.string.wallet_title))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onNavigateToSettings) {
                    Text(stringResource(R.string.settings_title))
                }
            }
        }
    }
}
