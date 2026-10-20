package com.example.clarity.presentation.transactions.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TransactionTagField(
    value: String,
    tags: List<String>,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    error: String?,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    Column(modifier) {
        Text("Tag", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                FilterChip(
                    selected = value.trim().ifBlank { "General" }.equals(tag, ignoreCase = true),
                    onClick = {
                        onValueChange(tag)
                        focusManager.clearFocus()
                    },
                    label = { Text(tag) },
                    enabled = enabled
                )
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Tag or custom tag (optional)") },
            placeholder = { Text("General") },
            supportingText = {
                Text(error ?: "Tap a tag or type a new one. New tags are saved with this transaction.")
            },
            isError = error != null,
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
