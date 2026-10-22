package com.example.clarity.presentation.goals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.domain.model.GoalProgressOperationType
import com.example.clarity.presentation.theme.ClarityTheme

@Composable
fun GoalProgressScreen(
    onBack: () -> Unit,
    viewModel: GoalProgressViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) onBack()
    }
    GoalProgressContent(
        state = state,
        onBack = onBack,
        onModeChange = viewModel::onModeChange,
        onAmountChange = viewModel::onAmountChange,
        onSave = viewModel::save
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalProgressContent(
    state: GoalProgressState,
    onBack: () -> Unit,
    onModeChange: (GoalProgressOperationType) -> Unit,
    onAmountChange: (String) -> Unit,
    onSave: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val enabled = state.canSave && !state.isSaving && !state.isLoading

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Update goal progress") },
                navigationIcon = {
                    TextButton(onClick = onBack, enabled = !state.isSaving) {
                        Text("Back")
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.error?.let { err ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (state.isLoading) {
                    CircularProgressIndicator()
                } else if (!state.canSave) {
                    OutlinedButton(onClick = onBack) {
                        Text("Back to Goals")
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = state.goalName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Current saved: ${state.currentSavedFormatted}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Target: ${state.targetFormatted}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Remaining: ${state.remainingFormatted}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            LinearProgressIndicator(
                                progress = state.progressFraction,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Text(
                                text = "${state.progressPercent}% complete",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilterChip(
                            selected = state.mode == GoalProgressOperationType.ADD,
                            onClick = { onModeChange(GoalProgressOperationType.ADD) },
                            label = { Text("Add contribution") },
                            enabled = enabled
                        )
                        FilterChip(
                            selected = state.mode == GoalProgressOperationType.SET,
                            onClick = { onModeChange(GoalProgressOperationType.SET) },
                            label = { Text("Set saved balance") },
                            enabled = enabled
                        )
                    }

                    val fieldLabel = if (state.mode == GoalProgressOperationType.ADD) {
                        "Contribution amount (${state.currency})"
                    } else {
                        "New saved balance (${state.currency})"
                    }
                    val helperText = if (state.mode == GoalProgressOperationType.ADD) {
                        "Adds this amount to the current saved balance. Remaining: ${state.remainingFormatted}."
                    } else {
                        "Replaces the saved balance with this exact amount (0 up to ${state.targetFormatted})."
                    }

                    OutlinedTextField(
                        value = state.amount,
                        onValueChange = onAmountChange,
                        label = { Text(fieldLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = state.amountError != null,
                        supportingText = {
                            Text(state.amountError ?: helperText)
                        },
                        enabled = enabled,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            enabled = !state.isSaving,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                onSave()
                            },
                            enabled = enabled,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save")
                        }
                    }

                    if (state.isSaving) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GoalProgressPreview() {
    ClarityTheme {
        GoalProgressContent(
            state = GoalProgressState(
                isLoading = false,
                canSave = true,
                goalName = "Emergency Fund",
                currency = "LKR",
                targetFormatted = "LKR 500000.00",
                currentSavedFormatted = "LKR 100000.00",
                remainingFormatted = "LKR 400000.00",
                progressPercent = 20,
                progressFraction = 0.2f
            ),
            onBack = {},
            onModeChange = {},
            onAmountChange = {},
            onSave = {}
        )
    }
}

