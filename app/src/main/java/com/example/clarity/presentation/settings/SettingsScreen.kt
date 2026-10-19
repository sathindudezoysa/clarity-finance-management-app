package com.example.clarity.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onBankAccounts: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.destination) {
        when (state.destination) {
            SettingsDestination.BACK -> onBack()
            SettingsDestination.BANK_ACCOUNTS -> onBankAccounts()
            null -> return@LaunchedEffect
        }
        viewModel.onNavigationHandled()
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { TextButton(onClick = viewModel::onBack) { Text("Back") } }
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(16.dp)) {
                Text("Primary reporting currency")
                com.example.clarity.presentation.transactions.CurrencyPicker(state.currency, viewModel::setCurrency, !state.saving)
                Text("New entries use this reporting currency. Existing entries keep their recorded currency. The initial default is LKR; cash wallet entries remain LKR.")
                if (state.pendingSync) Text("Saved on this device. Cloud sync pending.")
                if (state.error) Text("Could not save. Select the currency to retry.", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = viewModel::onBankAccounts, modifier = Modifier.fillMaxWidth()) {
                    Text("Bank accounts")
                }
            }
        }
    }
}
