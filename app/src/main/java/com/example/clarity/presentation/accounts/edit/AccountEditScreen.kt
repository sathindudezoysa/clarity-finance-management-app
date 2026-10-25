package com.example.clarity.presentation.accounts.edit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.presentation.theme.ClarityTheme

@Composable
fun AccountEditScreen(onBack: () -> Unit, viewModel: AccountEditViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isSuccess) { if (state.isSuccess) onBack() }
    AccountEditContent(state, onBack, viewModel::save, viewModel::onNameChange, viewModel::onBankNameChange,
        viewModel::onTypeChange, viewModel::onCurrencyChange, viewModel::onDefaultChange)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountEditContent(
    state: AccountEditState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onBankNameChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onDefaultChange: (Boolean) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val enabled = state.canSave && !state.isSaving && !state.isLoading
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (state.isEditMode) "Edit bank account" else "Add bank account") },
            navigationIcon = {
                IconButton(onClick = onBack, enabled = !state.isSaving) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 600.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.isLoading) CircularProgressIndicator()
                else if (state.canSave) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = state.name, onValueChange = onNameChange,
                                label = { Text("Account name") },
                                isError = state.nameError != null,
                                supportingText = { state.nameError?.let { Text(it) } },
                                enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = state.bankName, onValueChange = onBankNameChange,
                                label = { Text("Bank name") },
                                enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = state.accountType, onValueChange = onTypeChange,
                                label = { Text("Account type (optional)") },
                                enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = state.currency, onValueChange = onCurrencyChange,
                                label = { Text("Currency") },
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                                isError = state.currencyError != null,
                                supportingText = { Text(state.currencyError ?: "Use a three-letter currency code, such as LKR or USD.") },
                                enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                Modifier.fillMaxWidth().toggleable(value = state.isDefault,
                                    enabled = enabled && !state.isDefaultLocked, role = Role.Switch, onValueChange = onDefaultChange)
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Set as default", modifier = Modifier.weight(1f))
                                Switch(checked = state.isDefault, onCheckedChange = null, enabled = enabled && !state.isDefaultLocked)
                            }
                            if (state.isDefaultLocked) {
                                Text("Choose another default account in the accounts list before turning this off.", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else if (!state.isEditMode) {
                                Text("Your first account automatically becomes the default.", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Button(onClick = { focusManager.clearFocus(); onSave() }, enabled = enabled,
                        modifier = Modifier.fillMaxWidth()) { Text("Save") }
                    if (state.isSaving) CircularProgressIndicator()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountEditPreview() {
    ClarityTheme { AccountEditContent(AccountEditState(), {}, {}, {}, {}, {}, {}, {}) }
}
