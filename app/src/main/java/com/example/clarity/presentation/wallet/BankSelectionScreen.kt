package com.example.clarity.presentation.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.clarity.domain.model.BankCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankSelectionScreen(onBack: () -> Unit, onSelect: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf("") }
    val banks = remember(query) { BankCatalog.search(query) }
    val minCell = (100 * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp
    Scaffold(topBar = { TopAppBar(title = { Text("Select Bank") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
    }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 1040.dp).fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Search banks or abbreviations") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (banks.isEmpty()) Text("No matching banks. Try a different name or abbreviation.")
                LazyVerticalGrid(columns = GridCells.Adaptive(minCell), modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(banks, key = { it.id }) { bank ->
                        Card(modifier = Modifier.fillMaxWidth().selectable(selected == bank.id, role = Role.RadioButton,
                            onClick = { selected = bank.id; onSelect(bank.id) }),
                            colors = CardDefaults.cardColors(containerColor = if (selected == bank.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                BankMark()
                                Text(bank.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
