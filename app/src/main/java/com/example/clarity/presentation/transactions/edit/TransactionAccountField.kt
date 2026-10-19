package com.example.clarity.presentation.transactions.edit

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.clarity.domain.model.Account

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionAccountField(
    accounts: List<Account>,
    selectedAccountId: String?,
    onSelect: (String) -> Unit,
    enabled: Boolean,
    error: String?,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.id == selectedAccountId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = {
        if (enabled && accounts.isNotEmpty()) expanded = it
    }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.name.orEmpty(), onValueChange = {}, readOnly = true,
            label = { Text("Bank account") },
            placeholder = { Text("Select a bank account.") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            isError = error != null,
            supportingText = { error?.let { Text(it) } },
            enabled = enabled && accounts.isNotEmpty(), singleLine = true,
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(if (account.isDefault) "${account.name} (Default)" else account.name) },
                    onClick = { expanded = false; onSelect(account.id) }
                )
            }
        }
    }
}
