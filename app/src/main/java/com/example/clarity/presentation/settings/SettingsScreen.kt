package com.example.clarity.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.clarity.R
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
            title = { Text(stringResource(R.string.settings_title)) },
            navigationIcon = { TextButton(onClick = viewModel::onBack) { Text(stringResource(R.string.accounts_back)) } }
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(16.dp)) {
                OutlinedButton(onClick = viewModel::onBankAccounts, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.accounts_title))
                }
            }
        }
    }
}
