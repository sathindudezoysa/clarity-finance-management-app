package com.example.clarity.presentation.transactions.edit

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.clarity.R
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.ConversionSnapshot
import com.example.clarity.domain.model.ProofPolicy
import com.example.clarity.domain.model.TransactionProof
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.util.MoneyUtils
import com.example.clarity.domain.util.TransactionCalendar
import com.example.clarity.presentation.common.AppIcons
import com.example.clarity.presentation.theme.ClarityTheme
import com.example.clarity.presentation.transactions.TransactionMessages
import com.example.clarity.presentation.transactions.formatTransactionDate
import java.io.File

@Composable
fun TransactionEditScreen(
    onBack: () -> Unit,
    onCreateAccount: () -> Unit = {},
    onOpenCreateCategory: (TransactionType) -> Unit = {},
    createdCategoryName: String? = null,
    createdCategoryType: String? = null,
    onConsumedCreatedCategory: () -> Unit = {},
    viewModel: TransactionEditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(state.isSuccess, state.isDeleted) {
        if (state.isSuccess || state.isDeleted) onBack()
    }

    LaunchedEffect(viewModel, context, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.openFiles.collect { file ->
                val uri = Uri.parse(file.uri)
                if (uri.scheme != "content" || uri.authority != "${context.packageName}.proofs") {
                    viewModel.viewerUnavailable()
                } else {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, file.mimeType)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            .apply { clipData = ClipData.newRawUri("Proof", uri) }
                        context.startActivity(Intent.createChooser(intent, "Open receipt"))
                    } catch (_: ActivityNotFoundException) {
                        viewModel.viewerUnavailable()
                    } catch (_: SecurityException) {
                        viewModel.viewerUnavailable()
                    }
                }
            }
        }
    }

    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraName by rememberSaveable { mutableStateOf("Receipt_Photo.jpg") }

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val displayName = resolveDisplayName(context, uri)
            viewModel.onAttachProof(uri.toString(), displayName)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val uriString = pendingCameraUri
        if (captured && !uriString.isNullOrBlank()) {
            viewModel.onAttachProof(uriString, pendingCameraName)
        }
        pendingCameraUri = null
    }

    val launchCameraCapture = {
        runCatching {
            val fileName = "Receipt_${System.currentTimeMillis()}.jpg"
            val cameraDir = File(context.cacheDir, "proof-previews/camera").apply { mkdirs() }
            val photoFile = File(cameraDir, fileName)
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.proofs",
                photoFile,
                fileName
            )
            pendingCameraName = fileName
            pendingCameraUri = contentUri.toString()
            cameraLauncher.launch(contentUri)
        }.onFailure {
            viewModel.viewerUnavailable()
        }
        Unit
    }

    TransactionEditContent(
        state = state,
        onBack = onBack,
        onSave = viewModel::save,
        onAccountSelected = viewModel::onAccountSelected,
        onCreateAccount = onCreateAccount,
        onCurrencySelected = viewModel::onCurrencySelected,
        onPreview = viewModel::preview,
        onPickProofDocument = { documentPicker.launch(ProofPolicy.mimeTypes.toTypedArray()) },
        onCaptureProofPhoto = launchCameraCapture,
        onOpenProof = viewModel::onOpenProof,
        onVerifyProof = viewModel::onVerifyProof,
        onRemoveUnavailableProof = viewModel::onRemoveUnavailableProof,
        onRetryProofs = viewModel::onRetryProofs,
        onRemovePendingProof = viewModel::onRemovePendingProof,
        onTogglePendingProofVerified = viewModel::onTogglePendingProofVerified,
        onOpenCreateCategory = onOpenCreateCategory,
        createdCategoryName = createdCategoryName,
        createdCategoryType = createdCategoryType,
        onConsumedCreatedCategory = onConsumedCreatedCategory,
        onRequestDelete = if (state.canDelete) viewModel::showDeleteDialog else null,
        onConfirmDelete = viewModel::confirmDelete,
        onDismissDelete = viewModel::hideDeleteDialog
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionEditContent(
    state: TransactionEditState,
    onBack: () -> Unit,
    onSave: (TransactionType, String, String, String, Long) -> Unit,
    onAccountSelected: (String) -> Unit,
    onCreateAccount: () -> Unit,
    onCurrencySelected: (String) -> Unit = {},
    onPreview: (String) -> Unit = {},
    onRetryPreview: (() -> Unit)? = null,
    onPickProofDocument: () -> Unit = {},
    onCaptureProofPhoto: () -> Unit = {},
    onOpenProof: (String) -> Unit = {},
    onVerifyProof: (String, Long, Boolean) -> Unit = { _, _, _ -> },
    onRemoveUnavailableProof: (String) -> Unit = {},
    onRetryProofs: () -> Unit = {},
    onRemovePendingProof: (String) -> Unit = {},
    onTogglePendingProofVerified: (String) -> Unit = {},
    onOpenCreateCategory: (TransactionType) -> Unit = {},
    createdCategoryName: String? = null,
    createdCategoryType: String? = null,
    onConsumedCreatedCategory: () -> Unit = {},
    onRequestDelete: (() -> Unit)? = null,
    onConfirmDelete: () -> Unit = {},
    onDismissDelete: () -> Unit = {}
) {
    val initialTx = state.transaction
    var typeName by rememberSaveable { mutableStateOf(initialTx?.type?.name ?: TransactionType.EXPENSE.name) }
    var amount by rememberSaveable {
        mutableStateOf(initialTx?.let { MoneyUtils.formatMinor(it.amountMinor, it.currency) } ?: "")
    }
    var category by rememberSaveable { mutableStateOf(initialTx?.category ?: "General") }
    var note by rememberSaveable { mutableStateOf(initialTx?.note ?: "") }
    var dateEpochMillis by rememberSaveable { mutableStateOf(initialTx?.dateEpochMillis ?: System.currentTimeMillis()) }
    var loadedId by rememberSaveable { mutableStateOf(initialTx?.id) }
    var loadedOwner by rememberSaveable { mutableStateOf(initialTx?.userId) }
    var dateDialogVisible by rememberSaveable { mutableStateOf(false) }

    val amountFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val isBusy = state.isSaving || state.isDeleting

    LaunchedEffect(state.transaction?.id, state.transaction?.userId) {
        state.transaction?.let { transaction ->
            if (loadedId != transaction.id || loadedOwner != transaction.userId) {
                typeName = transaction.type.name
                amount = MoneyUtils.formatMinor(transaction.amountMinor, transaction.currency)
                category = transaction.category
                note = transaction.note
                dateEpochMillis = transaction.dateEpochMillis
                loadedId = transaction.id
                loadedOwner = transaction.userId
            }
        }
    }

    LaunchedEffect(createdCategoryName, createdCategoryType) {
        if (!createdCategoryName.isNullOrBlank()) {
            val targetType = createdCategoryType
                ?.let { runCatching { TransactionType.valueOf(it.uppercase()) }.getOrNull() }
            if (targetType != null) {
                typeName = targetType.name
            }
            category = createdCategoryName.trim()
            onConsumedCreatedCategory()
        }
    }

    LaunchedEffect(amount, state.currency, state.targetCurrency, state.transaction?.conversion) {
        onPreview(amount)
    }

    if (dateDialogVisible) {
        val initialDate = TransactionCalendar.pickerDate(dateEpochMillis)
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialDate)
        LaunchedEffect(pickerState.selectedDateMillis) {
            pickerState.selectedDateMillis?.takeIf { it != initialDate }?.let { selected ->
                dateEpochMillis = TransactionCalendar.fromPickerDate(selected)
                dateDialogVisible = false
            }
        }
        DatePickerDialog(
            onDismissRequest = { dateDialogVisible = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { selected ->
                            dateEpochMillis = TransactionCalendar.fromPickerDate(selected)
                        }
                        dateDialogVisible = false
                    },
                    enabled = pickerState.selectedDateMillis != null
                ) {
                    Text("Choose date")
                }
            },
            dismissButton = {
                TextButton(onClick = { dateDialogVisible = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (state.isDeleteDialogVisible) {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text("Delete transaction?") },
            text = { Text("This transaction will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) {
                    Text("Cancel")
                }
            }
        )
    }

    val parsedAmountMinor = remember(amount, state.currency) {
        MoneyUtils.parseMinor(amount, state.currency).getOrNull() ?: state.transaction?.amountMinor ?: 0L
    }

    val triggerSave = {
        focusManager.clearFocus()
        val resolvedType = runCatching { TransactionType.valueOf(typeName) }.getOrDefault(TransactionType.EXPENSE)
        val resolvedCategory = category.trim().ifBlank {
            if (resolvedType == TransactionType.INCOME) "Salary" else "General"
        }
        onSave(resolvedType, amount, resolvedCategory, note, dateEpochMillis)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.isEditMode) "Update Transaction" else "Transaction Entry",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        enabled = !isBusy
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = TransactionMessages.BACK_LABEL
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = triggerSave,
                        enabled = !isBusy
                    ) {
                        Text(
                            text = TransactionMessages.SAVE_LABEL,
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.error?.let { errorMsg ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (state.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (state.isEditMode && state.transaction == null) {
                    if (state.error == null) {
                        Text(
                            text = TransactionMessages.NOT_FOUND,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LaunchedEffect(Unit) {
                        if (!state.isEditMode) amountFocus.requestFocus()
                    }

                    // 1. Transaction Type Selector (Expense / Income)
                    val currentType = runCatching { TransactionType.valueOf(typeName) }.getOrDefault(TransactionType.EXPENSE)
                    val activeTypeTags = if (currentType == TransactionType.INCOME) state.incomeTags else state.expenseTags

                    TransactionTypeSelector(
                        selectedType = typeName,
                        enabled = !isBusy,
                        onTypeSelected = { newType ->
                            if (newType != typeName) {
                                typeName = newType
                                val originalTx = state.transaction
                                if (originalTx != null && originalTx.type.name == newType) {
                                    category = originalTx.category
                                } else {
                                    val nextTags = if (newType == TransactionType.INCOME.name) state.incomeTags else state.expenseTags
                                    val defaultForSlide = if (newType == TransactionType.INCOME.name) "Salary" else "General"
                                    category = nextTags.firstOrNull() ?: defaultForSlide
                                }
                            }
                        }
                    )

                    // 2. Amount Card (shows conversion rate when currency != targetCurrency)
                    TransactionAmountCard(
                        amount = amount,
                        onAmountChange = { amount = it },
                        currency = state.currency,
                        targetCurrency = state.targetCurrency,
                        amountError = state.amountError,
                        preview = state.preview,
                        previewLoading = state.previewLoading,
                        previewError = state.previewError,
                        onCurrencySelected = onCurrencySelected,
                        onRetryPreview = { onRetryPreview?.invoke() ?: onPreview(amount) },
                        enabled = !isBusy,
                        currencyLocked = false,
                        amountFocus = amountFocus
                    )

                    // 3. Category Chips Section
                    TransactionCategorySection(
                        transactionType = currentType,
                        category = category,
                        tags = activeTypeTags,
                        categoryError = state.categoryError,
                        onOpenCreateCategory = { onOpenCreateCategory(currentType) },
                        onCategoryChange = { category = it },
                        enabled = !isBusy
                    )

                    // 4. Payment Account Section
                    TransactionPaymentAccountSection(
                        accounts = state.accounts,
                        selectedAccountId = state.selectedAccountId,
                        isAccountsLoading = state.isAccountsLoading,
                        accountsError = state.accountsError,
                        accountError = state.accountError,
                        enabled = !isBusy,
                        onAccountSelected = onAccountSelected,
                        onCreateAccount = onCreateAccount
                    )

                    // 5. Receipt Verification / Slip Upload Section
                    TransactionReceiptSection(
                        isEditMode = state.isEditMode,
                        proofs = state.proofs,
                        pendingProofs = state.pendingProofs,
                        isProofBusy = state.isProofBusy,
                        proofError = state.proofError,
                        amountMinor = parsedAmountMinor,
                        currency = state.currency,
                        enabled = !isBusy,
                        onCapturePhoto = onCaptureProofPhoto,
                        onUploadSlip = onPickProofDocument,
                        onOpenProof = onOpenProof,
                        onOpenPendingUri = { rawUri ->
                            openLocalDraftUri(context, rawUri)
                        },
                        onVerifyProof = { proofId, verified ->
                            if (parsedAmountMinor > 0L) {
                                onVerifyProof(proofId, parsedAmountMinor, verified)
                            }
                        },
                        onRemoveUnavailableProof = onRemoveUnavailableProof,
                        onRetryProofs = onRetryProofs,
                        onRemovePendingProof = onRemovePendingProof,
                        onTogglePendingVerified = onTogglePendingProofVerified
                    )

                    // 6. Date & Transaction Note Section
                    TransactionDateAndNoteSection(
                        dateEpochMillis = dateEpochMillis,
                        note = note,
                        noteError = state.noteError,
                        enabled = !isBusy,
                        onOpenDatePicker = { dateDialogVisible = true },
                        onNoteChange = { note = it }
                    )

                    // 7. Primary & Delete Bottom Action Buttons
                    Button(
                        onClick = triggerSave,
                        enabled = !isBusy,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = 4.dp,
                                bottom = if (state.isEditMode && onRequestDelete != null) 0.dp else 16.dp
                            )
                            .heightIn(min = 48.dp)
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = if (state.isEditMode) "Update Transaction" else "Add Transaction",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (state.isEditMode && state.transaction != null && onRequestDelete != null) {
                        OutlinedButton(
                            onClick = {
                                focusManager.clearFocus()
                                onRequestDelete()
                            },
                            enabled = !isBusy,
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .heightIn(min = 48.dp)
                        ) {
                            if (state.isDeleting) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.error,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize)
                                )
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            }
                            Text(
                                text = "Delete Transaction",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionTypeSelector(
    selectedType: String,
    enabled: Boolean,
    onTypeSelected: (String) -> Unit
) {
    val isExpense = selectedType == TransactionType.EXPENSE.name
    val isIncome = selectedType == TransactionType.INCOME.name

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = isExpense,
            onClick = { onTypeSelected(TransactionType.EXPENSE.name) },
            enabled = enabled,
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
            onClick = { onTypeSelected(TransactionType.INCOME.name) },
            enabled = enabled,
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
}

@Composable
private fun TransactionAmountCard(
    amount: String,
    onAmountChange: (String) -> Unit,
    currency: String,
    targetCurrency: String,
    amountError: String?,
    preview: ConversionSnapshot?,
    previewLoading: Boolean,
    previewError: Boolean,
    @Suppress("UNUSED_PARAMETER")
    onCurrencySelected: (String) -> Unit,
    onRetryPreview: () -> Unit,
    enabled: Boolean,
    @Suppress("UNUSED_PARAMETER")
    currencyLocked: Boolean,
    amountFocus: FocusRequester
) {
    val uriHandler = LocalUriHandler.current
    val isDifferentCurrency = !currency.equals(targetCurrency, ignoreCase = true)

    OutlinedCard(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            width = 1.dp,
            color = if (amountError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Transactions,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Currency: $currency (${currencySymbol(currency)})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            OutlinedTextField(
                value = amount,
                onValueChange = onAmountChange,
                label = { Text("Amount") },
                placeholder = { Text("0.00") },
                prefix = {
                    Text(
                        text = "${currencySymbol(currency)} ",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = amountError != null,
                supportingText = amountError?.let { err ->
                    { Text(err) }
                },
                enabled = enabled,
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(amountFocus)
            )

            if (isDifferentCurrency) {
                if (previewLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Converting to $targetCurrency...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (preview != null) {
                    AssistChip(
                        onClick = { uriHandler.openUri("https://www.exchangerate-api.com") },
                        leadingIcon = {
                            Icon(
                                imageVector = AppIcons.Transactions,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize)
                            )
                        },
                        label = {
                            Text(
                                text = "≈ ${currencySymbol(targetCurrency)}${MoneyUtils.formatMinor(preview.amountMinor, targetCurrency)} $targetCurrency · Rate: ${preview.rate}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    )
                } else if (previewError) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Rate unavailable",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = onRetryPreview,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("Retry", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TransactionCategorySection(
    transactionType: TransactionType,
    category: String,
    tags: List<String>,
    categoryError: String?,
    onOpenCreateCategory: () -> Unit,
    onCategoryChange: (String) -> Unit,
    enabled: Boolean
) {
    val defaultFallback = if (transactionType == TransactionType.INCOME) "Salary" else "General"
    val effectiveSelected = category.trim().ifBlank { tags.firstOrNull() ?: defaultFallback }
    val displayTags = remember(tags, effectiveSelected) {
        if (effectiveSelected.isBlank()) {
            tags
        } else {
            val matchedTag = tags.firstOrNull { it.equals(effectiveSelected, ignoreCase = true) } ?: effectiveSelected
            val remaining = tags.filterNot { it.equals(effectiveSelected, ignoreCase = true) }
            listOf(matchedTag) + remaining
        }
    }
    val focusManager = LocalFocusManager.current

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (transactionType == TransactionType.INCOME) "INCOME CATEGORY" else "EXPENSE CATEGORY",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = {
                    focusManager.clearFocus()
                    onOpenCreateCategory()
                },
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "+ Custom Tag",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            displayTags.forEach { item ->
                val isSelected = effectiveSelected.equals(item, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onCategoryChange(item)
                        focusManager.clearFocus()
                    },
                    enabled = enabled,
                    label = { Text(item) },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else null
                )
            }

            AssistChip(
                onClick = {
                    focusManager.clearFocus()
                    onOpenCreateCategory()
                },
                enabled = enabled,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize)
                    )
                },
                label = { Text("New Tag") }
            )
        }

        categoryError?.let { err ->
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun TransactionPaymentAccountSection(
    accounts: List<Account>,
    selectedAccountId: String?,
    isAccountsLoading: Boolean,
    accountsError: String?,
    accountError: String?,
    enabled: Boolean,
    onAccountSelected: (String) -> Unit,
    onCreateAccount: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PAYMENT ACCOUNT",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (accounts.isNotEmpty()) {
                TextButton(
                    onClick = onCreateAccount,
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "+ New",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        accountsError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (isAccountsLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else if (accounts.isEmpty()) {
            OutlinedCard(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = TransactionMessages.ACCOUNT_REQUIRED,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    OutlinedButton(
                        onClick = onCreateAccount,
                        enabled = enabled,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("Create bank account")
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                accounts.forEach { account ->
                    val isSelected = account.id == selectedAccountId
                    OutlinedCard(
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        modifier = Modifier
                            .width(164.dp)
                            .clickable(enabled = enabled, role = Role.RadioButton) {
                                onAccountSelected(account.id)
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    contentColor = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (account.isCashWallet) {
                                            Icon(
                                                imageVector = AppIcons.Wallet,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_bank),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                Surface(
                                    color = if (account.isDefault) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    contentColor = if (account.isDefault) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        text = if (account.isDefault) "Default" else account.currency,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = account.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!account.isCashWallet && account.bankName.isNotBlank()) {
                                Text(
                                    text = account.bankName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        accountError?.let { err ->
            if (accounts.isNotEmpty() || err != TransactionMessages.ACCOUNT_REQUIRED) {
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun TransactionReceiptSection(
    isEditMode: Boolean,
    proofs: List<TransactionProof>,
    pendingProofs: List<PendingProofDraft>,
    isProofBusy: Boolean,
    proofError: String?,
    amountMinor: Long,
    currency: String,
    enabled: Boolean,
    onCapturePhoto: () -> Unit,
    onUploadSlip: () -> Unit,
    onOpenProof: (String) -> Unit,
    onOpenPendingUri: (String) -> Unit,
    onVerifyProof: (String, Boolean) -> Unit,
    onRemoveUnavailableProof: (String) -> Unit,
    onRetryProofs: () -> Unit,
    onRemovePendingProof: (String) -> Unit,
    onTogglePendingVerified: (String) -> Unit
) {
    val totalCount = if (isEditMode) proofs.size else pendingProofs.size
    val canAttachMore = enabled && !isProofBusy && totalCount < ProofPolicy.MAX_ATTACHMENTS

    OutlinedCard(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "RECEIPT SLIP",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isProofBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            proofError?.let { warningText ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = warningText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Pending Proofs (Create Mode)
            if (!isEditMode) {
                pendingProofs.forEach { draft ->
                    val isPdf = draft.displayName.endsWith(".pdf", ignoreCase = true)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .size(40.dp)
                                    .clickable { onOpenPendingUri(draft.uri) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_receipt_verified),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        shape = MaterialTheme.shapes.extraSmall,
                                        modifier = Modifier.align(Alignment.BottomEnd)
                                    ) {
                                        Text(
                                            text = if (isPdf) "PDF" else "IMG",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = draft.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(enabled = enabled) { onTogglePendingVerified(draft.uri) }
                            )

                            IconButton(
                                onClick = { onOpenPendingUri(draft.uri) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Preview slip",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { onRemovePendingProof(draft.uri) },
                                enabled = enabled,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove slip",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // Saved Proofs (Edit Mode)
                proofs.forEach { proof ->
                    val isPdf = proof.mimeType == "application/pdf"
                    val isVerified = amountMinor > 0L && proof.verifies(amountMinor)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    shape = MaterialTheme.shapes.small,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clickable(enabled = enabled && !proof.unavailable) { onOpenProof(proof.id) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_receipt_verified),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            shape = MaterialTheme.shapes.extraSmall,
                                            modifier = Modifier.align(Alignment.BottomEnd)
                                        ) {
                                            Text(
                                                text = if (isPdf) "PDF" else "IMG",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = proof.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                if (proof.unavailable) {
                                    TextButton(
                                        onClick = { onRemoveUnavailableProof(proof.id) },
                                        enabled = enabled && !isProofBusy
                                    ) {
                                        Text("Remove", color = MaterialTheme.colorScheme.error)
                                    }
                                } else {
                                    IconButton(
                                        onClick = { onOpenProof(proof.id) },
                                        enabled = enabled && !isProofBusy,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Open proof",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            if (!proof.unavailable && amountMinor > 0L) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = { onVerifyProof(proof.id, !isVerified) },
                                        enabled = enabled && !isProofBusy,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Text(
                                            text = if (isVerified) {
                                                "Verified"
                                            } else {
                                                "Verify ${currencySymbol(currency)}${MoneyUtils.formatMinor(amountMinor, currency)}"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Camera + Slip Upload Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCapturePhoto,
                    enabled = canAttachMore,
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (totalCount > 0) "Retake Photo" else "Take Photo",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                FilledTonalButton(
                    onClick = onUploadSlip,
                    enabled = canAttachMore,
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_receipt_verified),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Upload Slip / PDF",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (isEditMode && proofError != null) {
                TextButton(
                    onClick = onRetryProofs,
                    enabled = enabled && !isProofBusy,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Retry Sync",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionDateAndNoteSection(
    dateEpochMillis: Long,
    note: String,
    noteError: String?,
    enabled: Boolean,
    onOpenDatePicker: () -> Unit,
    onNoteChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedCard(
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, role = Role.Button) { onOpenDatePicker() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Date: ${formatTransactionDate(dateEpochMillis)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Change",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            label = { Text("Description (optional)") },
            placeholder = { Text("Add note...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null
                )
            },
            isError = noteError != null,
            supportingText = noteError?.let { err ->
                { Text(err) }
            },
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun currencySymbol(code: String): String = when (code.uppercase()) {
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    "LKR" -> "Rs"
    "INR" -> "₹"
    "JPY" -> "¥"
    "AUD" -> "A$"
    "CAD" -> "C$"
    "SGD" -> "S$"
    "KWD" -> "KD"
    else -> code
}

private fun resolveDisplayName(context: Context, uri: Uri): String {
    var name = ""
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(idx).orEmpty()
            }
        }
    }
    if (name.isBlank()) {
        val mime = context.contentResolver.getType(uri).orEmpty()
        name = if (mime.equals("application/pdf", ignoreCase = true)) "Receipt_Slip.pdf" else "Receipt_Slip.jpg"
    }
    return name
}

private fun openLocalDraftUri(context: Context, rawUri: String) {
    runCatching {
        val uri = Uri.parse(rawUri)
        val mime = context.contentResolver.getType(uri) ?: "image/*"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = ClipData.newRawUri("Receipt preview", uri) }
        context.startActivity(Intent.createChooser(intent, "Preview receipt"))
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
private fun TransactionEditPreview() {
    ClarityTheme {
        TransactionEditContent(
            state = TransactionEditState(
                accounts = listOf(
                    Account(name = "Chase Sapphire", bankName = "Chase Bank", isDefault = true),
                    Account.defaultCashWallet("USD")
                ),
                pendingProofs = listOf(
                    PendingProofDraft(uri = "content://example/receipt.jpg", displayName = "WholeFoods_Oct24.jpg", verified = true)
                )
            ),
            onBack = {},
            onSave = { _, _, _, _, _ -> },
            onAccountSelected = {},
            onCreateAccount = {}
        )
    }
}
