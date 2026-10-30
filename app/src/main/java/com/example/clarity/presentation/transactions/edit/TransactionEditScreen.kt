package com.example.clarity.presentation.transactions.edit

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.clarity.R
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.Currencies
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

private val StitchBg = Color(0xFFF8FAFA)
private val StitchTealDark = Color(0xFF006B5E)
private val StitchTealPrimary = Color(0xFF006D43)
private val StitchTealLightBg = Color(0xFFE6F4F1)
private val StitchTealBadgeText = Color(0xFF004D40)
private val StitchSurfaceMuted = Color(0xFFECF2F2)
private val StitchCardSubtle = Color(0xFFF2F6F6)
private val StitchBorder = Color(0xFFD9E2E2)
private val StitchTextPrimary = Color(0xFF191C1D)
private val StitchTextSecondary = Color(0xFF5C6B6B)

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
        containerColor = StitchBg,
        topBar = {
            Surface(
                color = StitchBg,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            enabled = !isBusy,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(StitchSurfaceMuted)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = TransactionMessages.BACK_LABEL,
                                tint = StitchTealDark
                            )
                        }
                        Text(
                            text = if (state.isEditMode) "Update Transaction" else "Transaction Entry",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = StitchTextPrimary
                            )
                        )
                    }
                    TextButton(
                        onClick = triggerSave,
                        enabled = !isBusy
                    ) {
                        Text(
                            text = TransactionMessages.SAVE_LABEL,
                            color = StitchTealDark,
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
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.error?.let { errorMsg ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMsg,
                        color = MaterialTheme.colorScheme.onErrorContainer,
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
                    CircularProgressIndicator(color = StitchTealDark)
                }
            } else if (state.isEditMode && state.transaction == null) {
                if (state.error == null) {
                    Text("Transaction not found.", color = StitchTextSecondary)
                }
            } else {
                LaunchedEffect(Unit) {
                    if (!state.isEditMode) amountFocus.requestFocus()
                }

                // 1. Segmented Control (Expense / Income)
                val currentType = runCatching { TransactionType.valueOf(typeName) }.getOrDefault(TransactionType.EXPENSE)
                val activeTypeTags = if (currentType == TransactionType.INCOME) state.incomeTags else state.expenseTags

                StitchTransactionTypeSelector(
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

                // 2. Hero Amount Card (only shows conversion rate when currency != targetCurrency)
                StitchHeroAmountCard(
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
                    amountFocus = amountFocus
                )

                // 3. Category Pills Section (Visible multi-row FlowRow, selected tag first)
                StitchCategorySection(
                    transactionType = currentType,
                    category = category,
                    tags = activeTypeTags,
                    categoryError = state.categoryError,
                    onOpenCreateCategory = { onOpenCreateCategory(currentType) },
                    onCategoryChange = { category = it },
                    enabled = !isBusy
                )

                // 4. Payment Account Section
                StitchPaymentAccountSection(
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
                StitchReceiptVerificationCard(
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

                // 6. Date & Transaction Note Cards
                StitchDateAndNoteSection(
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
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StitchTealPrimary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = if (state.isEditMode && onRequestDelete != null) 0.dp else 14.dp)
                        .height(52.dp)
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        text = if (state.isEditMode) "Update Transaction" else "Add Transaction",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                }

                if (state.isEditMode && state.transaction != null && onRequestDelete != null) {
                    OutlinedButton(
                        onClick = {
                            focusManager.clearFocus()
                            onRequestDelete()
                        },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .height(48.dp)
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
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = "Delete Transaction",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StitchTransactionTypeSelector(
    selectedType: String,
    enabled: Boolean,
    onTypeSelected: (String) -> Unit
) {
    Surface(
        color = StitchSurfaceMuted,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val isExpense = selectedType == TransactionType.EXPENSE.name
            val isIncome = selectedType == TransactionType.INCOME.name

            Surface(
                color = if (isExpense) Color.White else Color.Transparent,
                shape = RoundedCornerShape(9.dp),
                shadowElevation = if (isExpense) 1.dp else 0.dp,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(enabled = enabled, role = Role.Tab) {
                        onTypeSelected(TransactionType.EXPENSE.name)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = AppIcons.ArrowDownward,
                        contentDescription = null,
                        tint = if (isExpense) StitchTealDark else StitchTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Expense",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = if (isExpense) StitchTealDark else StitchTextSecondary
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
                    .clickable(enabled = enabled, role = Role.Tab) {
                        onTypeSelected(TransactionType.INCOME.name)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = AppIcons.ArrowUpward,
                        contentDescription = null,
                        tint = if (isIncome) StitchTealDark else StitchTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Income",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = if (isIncome) StitchTealDark else StitchTextSecondary
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun StitchHeroAmountCard(
    amount: String,
    onAmountChange: (String) -> Unit,
    currency: String,
    targetCurrency: String,
    amountError: String?,
    preview: com.example.clarity.domain.model.ConversionSnapshot?,
    previewLoading: Boolean,
    previewError: Boolean,
    onCurrencySelected: (String) -> Unit,
    onRetryPreview: () -> Unit,
    enabled: Boolean,
    amountFocus: FocusRequester
) {
    var currencyMenuExpanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val isDifferentCurrency = !currency.equals(targetCurrency, ignoreCase = true)

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (amountError != null) MaterialTheme.colorScheme.error else StitchBorder),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Currency Switcher Pill Badge
            Box {
                Surface(
                    color = StitchSurfaceMuted,
                    shape = CircleShape,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = enabled) { currencyMenuExpanded = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Transactions,
                            contentDescription = null,
                            tint = StitchTealDark,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Currency: $currency (${currencySymbol(currency)})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = StitchTextPrimary
                            )
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select currency",
                            tint = StitchTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                DropdownMenu(
                    expanded = currencyMenuExpanded,
                    onDismissRequest = { currencyMenuExpanded = false }
                ) {
                    Currencies.supported.forEach { code ->
                        DropdownMenuItem(
                            text = { Text("$code (${currencySymbol(code)})") },
                            onClick = {
                                currencyMenuExpanded = false
                                onCurrencySelected(code)
                            }
                        )
                    }
                }
            }

            // Ergonomic Amount Input
            OutlinedTextField(
                value = amount,
                onValueChange = onAmountChange,
                label = { Text("Amount") },
                placeholder = { Text("0.00") },
                prefix = {
                    Text(
                        text = "${currencySymbol(currency)} ",
                        style = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = StitchTealDark
                        )
                    )
                },
                textStyle = TextStyle(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = StitchTextPrimary
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = amountError != null,
                supportingText = amountError?.let { err ->
                    { Text(err, color = MaterialTheme.colorScheme.error) }
                },
                enabled = enabled,
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StitchTealDark,
                    unfocusedBorderColor = StitchBorder,
                    focusedContainerColor = StitchCardSubtle.copy(alpha = 0.5f),
                    unfocusedContainerColor = StitchCardSubtle.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(amountFocus)
            )

            // Only show conversion rate if entering a different currency than default targetCurrency
            if (isDifferentCurrency) {
                if (previewLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = StitchTealDark
                        )
                        Text(
                            text = "Converting to $targetCurrency...",
                            style = MaterialTheme.typography.labelSmall.copy(color = StitchTextSecondary)
                        )
                    }
                } else if (preview != null) {
                    // Rate value pill with ExchangeRate-API link hidden inside on tap
                    Surface(
                        color = StitchTealLightBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                uriHandler.openUri("https://www.exchangerate-api.com")
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Transactions,
                                contentDescription = null,
                                tint = StitchTealBadgeText,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "≈ ${currencySymbol(targetCurrency)}${MoneyUtils.formatMinor(preview.amountMinor, targetCurrency)} $targetCurrency · Rate: ${preview.rate}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = StitchTealBadgeText
                                )
                            )
                        }
                    }
                } else if (previewError) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Rate unavailable",
                            style = MaterialTheme.typography.bodySmall.copy(color = StitchTextSecondary)
                        )
                        TextButton(
                            onClick = onRetryPreview,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("Retry", color = StitchTealDark, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StitchCategorySection(
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (transactionType == TransactionType.INCOME) "INCOME CATEGORY" else "EXPENSE CATEGORY",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = StitchTextSecondary
                )
            )
            Text(
                text = "+ Custom Tag",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = StitchTealDark
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = enabled) {
                        focusManager.clearFocus()
                        onOpenCreateCategory()
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            displayTags.forEach { item ->
                val isSelected = effectiveSelected.equals(item, ignoreCase = true)
                Surface(
                    color = if (isSelected) StitchTealDark else Color.White,
                    shape = CircleShape,
                    border = if (isSelected) null else BorderStroke(1.dp, StitchBorder),
                    shadowElevation = if (isSelected) 1.dp else 0.dp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = enabled) {
                            onCategoryChange(item)
                            focusManager.clearFocus()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = item,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) Color.White else StitchTextPrimary
                            )
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            Surface(
                color = StitchTealLightBg,
                shape = CircleShape,
                border = BorderStroke(1.dp, StitchTealDark.copy(alpha = 0.35f)),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = enabled) {
                        focusManager.clearFocus()
                        onOpenCreateCategory()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = StitchTealBadgeText,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "New Tag",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = StitchTealBadgeText
                        )
                    )
                }
            }
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
private fun StitchPaymentAccountSection(
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PAYMENT ACCOUNT",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = StitchTextSecondary
                )
            )
            if (accounts.isNotEmpty()) {
                Text(
                    text = "+ New",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = StitchTealPrimary
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = enabled) { onCreateAccount() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        accountsError?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        if (isAccountsLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = StitchTealDark
            )
        } else if (accounts.isEmpty()) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = TransactionMessages.ACCOUNT_REQUIRED,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                    )
                    OutlinedButton(
                        onClick = onCreateAccount,
                        enabled = enabled,
                        shape = RoundedCornerShape(10.dp)
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
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) StitchTealDark else StitchBorder
                        ),
                        shadowElevation = if (isSelected) 2.dp else 0.dp,
                        modifier = Modifier
                            .width(164.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = enabled) { onAccountSelected(account.id) }
                    ) {
                        Column(
                            modifier = Modifier.padding(11.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) StitchTealDark.copy(alpha = 0.12f) else StitchSurfaceMuted),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (account.isCashWallet) {
                                        Icon(
                                            imageVector = AppIcons.Wallet,
                                            contentDescription = null,
                                            tint = if (isSelected) StitchTealDark else StitchTextSecondary,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_bank),
                                            contentDescription = null,
                                            tint = if (isSelected) StitchTealDark else StitchTextSecondary,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                                Surface(
                                    color = if (account.isDefault) StitchTealLightBg else StitchSurfaceMuted,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (account.isDefault) "Default" else account.currency,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (account.isDefault) StitchTealBadgeText else StitchTextSecondary
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = account.name,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = StitchTextPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!account.isCashWallet && account.bankName.isNotBlank()) {
                                Text(
                                    text = account.bankName,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        color = StitchTextSecondary
                                    ),
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
            if (!accounts.isEmpty() || err != TransactionMessages.ACCOUNT_REQUIRED) {
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
private fun StitchReceiptVerificationCard(
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

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, StitchBorder),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "RECEIPT SLIP",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = StitchTextSecondary
                )
            )

            if (isProofBusy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = StitchTealDark
                )
            }

            proofError?.let { warningText ->
                Surface(
                    color = Color(0xFFFFF8E1),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFCC80)),
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
                            tint = Color(0xFFB26A00),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = warningText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF663C00),
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            // Pending Proofs (Create Mode)
            if (!isEditMode) {
                pendingProofs.forEach { draft ->
                    val isPdf = draft.displayName.endsWith(".pdf", ignoreCase = true)
                    Surface(
                        color = StitchCardSubtle,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(StitchSurfaceMuted)
                                    .border(1.dp, StitchBorder, RoundedCornerShape(8.dp))
                                    .clickable { onOpenPendingUri(draft.uri) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_receipt_verified),
                                    contentDescription = null,
                                    tint = StitchTealDark,
                                    modifier = Modifier.size(20.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(topStart = 4.dp))
                                        .padding(horizontal = 3.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = if (isPdf) "PDF" else "IMG",
                                        style = TextStyle(color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    )
                                }
                            }

                            Text(
                                text = draft.displayName,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = StitchTextPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(enabled = enabled) { onTogglePendingVerified(draft.uri) }
                            )

                            IconButton(
                                onClick = { onOpenPendingUri(draft.uri) },
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Preview slip",
                                    tint = StitchTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = { onRemovePendingProof(draft.uri) },
                                enabled = enabled,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove slip",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
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
                    Surface(
                        color = StitchCardSubtle,
                        shape = RoundedCornerShape(10.dp),
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
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(StitchSurfaceMuted)
                                        .border(1.dp, StitchBorder, RoundedCornerShape(8.dp))
                                        .clickable(enabled = enabled && !proof.unavailable) { onOpenProof(proof.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_receipt_verified),
                                        contentDescription = null,
                                        tint = StitchTealDark,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(topStart = 4.dp))
                                            .padding(horizontal = 3.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = if (isPdf) "PDF" else "IMG",
                                            style = TextStyle(color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }

                                Text(
                                    text = proof.displayName,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = StitchTextPrimary
                                    ),
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
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Open proof",
                                            tint = StitchTextSecondary,
                                            modifier = Modifier.size(16.dp)
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
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = StitchTealDark
                                            )
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
                Surface(
                    color = StitchSurfaceMuted,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = canAttachMore) { onCapturePhoto() }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 9.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = StitchTextPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (totalCount > 0) "Retake Photo" else "Take Photo",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = StitchTextPrimary
                            )
                        )
                    }
                }

                Surface(
                    color = StitchTealLightBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = canAttachMore) { onUploadSlip() }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 9.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_receipt_verified),
                            contentDescription = null,
                            tint = StitchTealBadgeText,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Upload Slip / PDF",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = StitchTealBadgeText
                            )
                        )
                    }
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
                        tint = StitchTealDark,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Retry Sync",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = StitchTealDark
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun StitchDateAndNoteSection(
    dateEpochMillis: Long,
    note: String,
    noteError: String?,
    enabled: Boolean,
    onOpenDatePicker: () -> Unit,
    onNoteChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Date Selector Card
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, StitchBorder),
            shadowElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = enabled) { onOpenDatePicker() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
                        tint = StitchTealDark,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Date: ${formatTransactionDate(dateEpochMillis)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = StitchTextPrimary
                        )
                    )
                }
                Text(
                    text = "Change",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = StitchTealDark
                    )
                )
            }
        }

        // Transaction Note Card
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, if (noteError != null) MaterialTheme.colorScheme.error else StitchBorder),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                OutlinedTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    label = { Text("Description (optional)") },
                    placeholder = { Text("Add note...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = StitchTextSecondary
                        )
                    },
                    isError = noteError != null,
                    supportingText = noteError?.let { err ->
                        { Text(err, color = MaterialTheme.colorScheme.error) }
                    },
                    enabled = enabled,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
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

@Preview(showBackground = true)
@Composable
private fun TransactionEditPreview() {
    ClarityTheme {
        TransactionEditContent(
            state = TransactionEditState(
                accounts = listOf(
                    Account(name = "Chase Sapphire", bankName = " Chase Bank", isDefault = true),
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
