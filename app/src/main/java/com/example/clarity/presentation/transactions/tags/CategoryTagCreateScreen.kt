package com.example.clarity.presentation.transactions.tags

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.presentation.common.AppIcons

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CategoryTagCreateScreen(
    onBack: () -> Unit,
    onTagCreated: (name: String, type: TransactionType) -> Unit,
    viewModel: CategoryTagCreateViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tagName by rememberSaveable { mutableStateOf("") }
    val nameFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(state.savedCategoryName, state.savedCategoryType) {
        val createdName = state.savedCategoryName
        val createdType = state.savedCategoryType
        if (createdName != null && createdType != null) {
            onTagCreated(createdName, createdType)
        }
    }

    LaunchedEffect(Unit) {
        nameFocusRequester.requestFocus()
    }

    val triggerSave = {
        focusManager.clearFocus()
        viewModel.saveCategoryTag(tagName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Create Category Tag",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        enabled = !state.isSaving
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = triggerSave,
                        enabled = !state.isSaving && tagName.isNotBlank()
                    ) {
                        Text(
                            text = "Save",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Selector: Expense vs Income
                val isExpense = state.selectedType == TransactionType.EXPENSE
                val isIncome = state.selectedType == TransactionType.INCOME

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isExpense,
                        onClick = { viewModel.onTypeSelected(TransactionType.EXPENSE) },
                        enabled = !state.isSaving,
                        leadingIcon = {
                            Icon(
                                imageVector = AppIcons.ArrowDownward,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        },
                        label = {
                            Text(
                                text = "Expense",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = isIncome,
                        onClick = { viewModel.onTypeSelected(TransactionType.INCOME) },
                        enabled = !state.isSaving,
                        leadingIcon = {
                            Icon(
                                imageVector = AppIcons.ArrowUpward,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        },
                        label = {
                            Text(
                                text = "Income",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // 2. Category Tag Name Input Card
                OutlinedCard(
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(
                        1.dp,
                        if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
                    ),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (state.selectedType == TransactionType.INCOME) {
                                "NEW INCOME TAG"
                            } else {
                                "NEW EXPENSE TAG"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = tagName,
                            onValueChange = {
                                tagName = it
                                if (state.error != null) viewModel.onClearError()
                            },
                            label = { Text("Category name") },
                            placeholder = {
                                Text(
                                    if (state.selectedType == TransactionType.INCOME) {
                                        "e.g. Rental, Dividends"
                                    } else {
                                        "e.g. Dining, Fuel"
                                    }
                                )
                            },
                            isError = state.error != null,
                            supportingText = state.error?.let { err ->
                                { Text(err) }
                            },
                            enabled = !state.isSaving,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { triggerSave() }),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(nameFocusRequester)
                        )
                    }
                }

                // 3. All Tags for Selected Type (Expense or Income)
                OutlinedCard(
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (state.selectedType == TransactionType.INCOME) {
                                "INCOME TAGS"
                            } else {
                                "EXPENSE TAGS"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.activeTags.forEach { item ->
                                SuggestionChip(
                                    onClick = { onTagCreated(item, state.selectedType) },
                                    enabled = !state.isSaving,
                                    label = { Text(item) }
                                )
                            }
                        }
                    }
                }

                // 4. Save Button
                Button(
                    onClick = triggerSave,
                    enabled = !state.isSaving && tagName.isNotBlank(),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 16.dp)
                        .heightIn(min = 48.dp)
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    }
                    Text(
                        text = "Save Category Tag",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
