package com.example.clarity.presentation.goals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.domain.util.GoalPolicy
import com.example.clarity.presentation.theme.ClarityTheme

@Composable
fun GoalEditScreen(
    onBack: () -> Unit,
    viewModel: GoalEditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            onBack()
        }
    }

    GoalEditContent(
        state = state,
        onBack = onBack,
        onRetryLoad = viewModel::loadInitialState,
        onNameChanged = viewModel::onNameChanged,
        onDescriptionChanged = viewModel::onDescriptionChanged,
        onTargetAmountChanged = viewModel::onTargetAmountChanged,
        onTargetDateSelected = viewModel::onTargetDateSelected,
        onSave = viewModel::save
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalEditContent(
    state: GoalEditState,
    onBack: () -> Unit,
    onRetryLoad: () -> Unit,
    onNameChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onTargetAmountChanged: (String) -> Unit,
    onTargetDateSelected: (Long?) -> Unit,
    onSave: () -> Unit
) {
    val titleText = if (state.isEditMode) "Edit Goal" else "Create Goal"
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.fatalError != null -> {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GoalErrorBanner(
                            message = state.fatalError,
                            onRetry = onRetryLoad
                        )
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Back to Goals")
                        }
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        state.formError?.let { errorMsg ->
                            GoalErrorBanner(
                                message = errorMsg,
                                onRetry = onSave
                            )
                        }

                        OutlinedTextField(
                            value = state.name,
                            onValueChange = onNameChanged,
                            label = { Text("Goal Name *") },
                            placeholder = { Text("e.g., Emergency Fund") },
                            singleLine = true,
                            enabled = !state.isSaving,
                            isError = state.nameError != null,
                            supportingText = {
                                val error = state.nameError
                                if (error != null) {
                                    Text(error)
                                } else {
                                    Text("${state.name.trim().length}/${GoalPolicy.MAX_NAME_LENGTH}")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = state.targetAmount,
                            onValueChange = onTargetAmountChanged,
                            label = { Text("Target Amount (${state.currency}) *") },
                            prefix = { Text("${state.currency} ") },
                            singleLine = true,
                            enabled = !state.isSaving,
                            isError = state.targetAmountError != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            supportingText = {
                                val error = state.targetAmountError
                                if (error != null) {
                                    Text(error)
                                } else {
                                    Text("Currency: ${state.currency} (read-only)")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val borderColor = if (state.targetDateError != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .semantics {
                                        contentDescription = "Target date ${state.targetDateFormatted}"
                                    }
                                    .clickable(
                                        enabled = !state.isSaving,
                                        role = Role.Button
                                    ) {
                                        showDatePicker = !showDatePicker
                                    },
                                border = BorderStroke(1.dp, borderColor),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "Target Date *",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (state.targetDateError != null) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                        Text(
                                            text = state.targetDateFormatted,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            state.targetDateError?.let { dateErr ->
                                Text(
                                    text = dateErr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }

                        if (showDatePicker) {
                            val pickerState = rememberDatePickerState(
                                initialSelectedDateMillis = state.selectedDateUtcMillis.takeIf { it != 0L }
                            )
                            LaunchedEffect(pickerState) {
                                val initialMillis = pickerState.selectedDateMillis
                                snapshotFlow { pickerState.selectedDateMillis }
                                    .collect { selectedMillis ->
                                        if (selectedMillis != null && selectedMillis != initialMillis) {
                                            onTargetDateSelected(selectedMillis)
                                            showDatePicker = false
                                        }
                                    }
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Select Target Date",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        TextButton(onClick = { showDatePicker = false }) {
                                            Text("Done")
                                        }
                                    }
                                    DatePicker(
                                        state = pickerState,
                                        showModeToggle = false,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = state.description,
                            onValueChange = onDescriptionChanged,
                            label = { Text("Description (optional)") },
                            placeholder = { Text("Add notes about this financial goal") },
                            minLines = 2,
                            maxLines = 4,
                            enabled = !state.isSaving,
                            isError = state.descriptionError != null,
                            supportingText = {
                                val error = state.descriptionError
                                if (error != null) {
                                    Text(error)
                                } else {
                                    Text("${state.description.trim().length}/${GoalPolicy.MAX_DESCRIPTION_LENGTH}")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onBack,
                                enabled = !state.isSaving,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = onSave,
                                enabled = !state.isSaving,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text(if (state.isEditMode) "Save Changes" else "Create Goal")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GoalEditScreenPreview() {
    ClarityTheme {
        GoalEditContent(
            state = GoalEditState(
                isLoading = false,
                currency = "LKR",
                name = "Emergency Fund",
                description = "Six months of living expenses",
                targetAmount = "500000.00",
                targetDateEpochDay = 21000L,
                selectedDateUtcMillis = 21000L * 86_400_000L,
                targetDateFormatted = "29 Jun 2027"
            ),
            onBack = {},
            onRetryLoad = {},
            onNameChanged = {},
            onDescriptionChanged = {},
            onTargetAmountChanged = {},
            onTargetDateSelected = {},
            onSave = {}
        )
    }
}
