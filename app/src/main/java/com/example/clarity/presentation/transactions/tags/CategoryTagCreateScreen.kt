package com.example.clarity.presentation.transactions.tags

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.presentation.common.AppIcons

private val TagBg = Color(0xFFF8FAFA)
private val TagTealDark = Color(0xFF006B5E)
private val TagTealPrimary = Color(0xFF006D43)
private val TagSurfaceMuted = Color(0xFFECF2F2)
private val TagCardSubtle = Color(0xFFF2F6F6)
private val TagBorder = Color(0xFFD9E2E2)
private val TagTextPrimary = Color(0xFF191C1D)
private val TagTextSecondary = Color(0xFF5C6B6B)

@OptIn(ExperimentalLayoutApi::class)
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
        containerColor = TagBg,
        topBar = {
            Surface(
                color = TagBg,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            enabled = !state.isSaving,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(TagSurfaceMuted)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = TagTealDark
                            )
                        }
                        Text(
                            text = "Create Category Tag",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TagTextPrimary
                            )
                        )
                    }
                    TextButton(
                        onClick = triggerSave,
                        enabled = !state.isSaving && tagName.isNotBlank()
                    ) {
                        Text(
                            text = "Save",
                            color = if (tagName.isNotBlank()) TagTealDark else TagTextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Slide Selector: Expense vs Income
            Surface(
                color = TagSurfaceMuted,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isExpense = state.selectedType == TransactionType.EXPENSE
                    val isIncome = state.selectedType == TransactionType.INCOME

                    Surface(
                        color = if (isExpense) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(9.dp),
                        shadowElevation = if (isExpense) 1.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .clickable(enabled = !state.isSaving, role = Role.Tab) {
                                viewModel.onTypeSelected(TransactionType.EXPENSE)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = AppIcons.ArrowDownward,
                                contentDescription = null,
                                tint = if (isExpense) TagTealDark else TagTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Expense",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isExpense) TagTealDark else TagTextSecondary
                                )
                            )
                        }
                    }

                    Surface(
                        color = if (isIncome) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(9.dp),
                        shadowElevation = if (isIncome) 1.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .clickable(enabled = !state.isSaving, role = Role.Tab) {
                                viewModel.onTypeSelected(TransactionType.INCOME)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = AppIcons.ArrowUpward,
                                contentDescription = null,
                                tint = if (isIncome) TagTealDark else TagTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Income",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isIncome) TagTealDark else TagTextSecondary
                                )
                            )
                        }
                    }
                }
            }

            // 2. Category Tag Name Input Card
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    if (state.error != null) MaterialTheme.colorScheme.error else TagBorder
                ),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (state.selectedType == TransactionType.INCOME) {
                            "NEW INCOME TAG"
                        } else {
                            "NEW EXPENSE TAG"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = TagTextSecondary
                        )
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
                            { Text(err, color = MaterialTheme.colorScheme.error) }
                        },
                        enabled = !state.isSaving,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { triggerSave() }),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TagTealDark,
                            unfocusedBorderColor = TagBorder,
                            focusedContainerColor = TagCardSubtle.copy(alpha = 0.45f),
                            unfocusedContainerColor = TagCardSubtle.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(nameFocusRequester)
                    )
                }
            }

            // 3. All Tags for Selected Type (Expense or Income)
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, TagBorder),
                shadowElevation = 1.dp,
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
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = TagTextSecondary
                        )
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.activeTags.forEach { item ->
                            Surface(
                                color = TagCardSubtle,
                                shape = CircleShape,
                                border = BorderStroke(1.dp, TagBorder),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(enabled = !state.isSaving) {
                                        onTagCreated(item, state.selectedType)
                                    }
                            ) {
                                Text(
                                    text = item,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = TagTextPrimary
                                    ),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Save Button
            Button(
                onClick = triggerSave,
                enabled = !state.isSaving && tagName.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TagTealPrimary,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 16.dp)
                    .height(52.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                } else {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = "Save Category Tag",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                )
            }
        }
    }
}
