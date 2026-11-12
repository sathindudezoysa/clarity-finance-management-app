package com.example.clarity.presentation.accounts.transfer

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.ConversionSnapshot
import com.example.clarity.domain.model.TransactionTags
import com.example.clarity.domain.util.MoneyUtils
import com.example.clarity.presentation.common.AppIcons
import com.example.clarity.presentation.theme.ClarityTheme
import java.math.BigInteger

@Composable
fun AccountTransferScreen(
    onBack: () -> Unit,
    onAddAccount: () -> Unit = {},
    viewModel: AccountTransferViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onBack()
        }
    }

    AccountTransferContent(
        state = state,
        onBack = onBack,
        onAddAccount = onAddAccount,
        onSourceAccountSelected = viewModel::onSourceAccountSelected,
        onDestinationAccountSelected = viewModel::onDestinationAccountSelected,
        onSwapAccounts = viewModel::swapAccounts,
        onAmountChanged = viewModel::onAmountChanged,
        onNoteChanged = viewModel::onNoteChanged,
        onRetryPreview = viewModel::retryPreview,
        onSubmit = viewModel::submit
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountTransferContent(
    state: AccountTransferState,
    onBack: () -> Unit,
    onAddAccount: () -> Unit = {},
    onSourceAccountSelected: (String) -> Unit,
    onDestinationAccountSelected: (String) -> Unit,
    onSwapAccounts: () -> Unit,
    onAmountChanged: (String) -> Unit,
    onNoteChanged: (String) -> Unit,
    onRetryPreview: () -> Unit,
    onSubmit: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    val context = LocalContext.current

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Send Money",
                        style = typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isSaving) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground
                )
            )
        },
        bottomBar = {
            if (!state.isLoading && state.accounts.size >= 2) {
                Surface(
                    color = colors.surface,
                    tonalElevation = 3.dp,
                    shadowElevation = 6.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = onSubmit,
                            enabled = state.canSubmit && state.amountInput.isNotBlank(),
                            modifier = Modifier
                                .widthIn(max = 600.dp)
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                            shape = shapes.medium
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = colors.onPrimary
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Transferring...",
                                    style = typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Icon(
                                    imageVector = AppIcons.Transactions,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Send Money",
                                    style = typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                state.error?.let { errorMsg ->
                    TransferErrorBanner(message = errorMsg)
                }

                state.accountError?.let { accErr ->
                    TransferErrorBanner(message = accErr)
                }

                if (state.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (state.accounts.size < 2) {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.large,
                        colors = CardDefaults.outlinedCardColors(containerColor = colors.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(shapes.medium)
                                    .background(colors.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.Transactions,
                                    contentDescription = null,
                                    tint = colors.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                text = "At least two accounts are required",
                                style = typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Add another bank account to transfer money between accounts.",
                                style = typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = onAddAccount,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Add Account")
                            }
                        }
                    }
                } else {
                    // Category tag badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Category Tag:",
                            style = typography.labelLarge,
                            color = colors.onSurfaceVariant
                        )
                        Surface(
                            shape = shapes.small,
                            color = colors.secondaryContainer,
                            contentColor = colors.onSecondaryContainer
                        ) {
                            Text(
                                text = TransactionTags.MONEY_TRANSFER,
                                style = typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Accounts selection card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.large,
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AccountSelectorDropdown(
                                label = "From Account",
                                selectedAccount = state.sourceAccount,
                                accounts = state.accounts,
                                balancesFormatted = state.accountBalancesFormatted,
                                enabled = !state.isSaving,
                                onAccountSelected = onSourceAccountSelected
                            )

                            if (state.availableSourceBalanceFormatted.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Available balance",
                                        style = typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                    Text(
                                        text = state.availableSourceBalanceFormatted,
                                        style = typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.primary
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Divider(color = colors.outlineVariant)
                                FilledTonalIconButton(
                                    onClick = onSwapAccounts,
                                    enabled = !state.isSaving
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Transactions,
                                        contentDescription = "Swap accounts"
                                    )
                                }
                            }

                            AccountSelectorDropdown(
                                label = "To Account",
                                selectedAccount = state.destinationAccount,
                                accounts = state.accounts.filter { it.id != state.sourceAccountId },
                                balancesFormatted = state.accountBalancesFormatted,
                                enabled = !state.isSaving,
                                onAccountSelected = onDestinationAccountSelected
                            )

                            if (state.destinationBalanceFormatted.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Current destination balance",
                                        style = typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                    Text(
                                        text = state.destinationBalanceFormatted,
                                        style = typography.labelLarge,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Amount & Currency Conversion card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.large,
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = state.amountInput,
                                onValueChange = onAmountChanged,
                                label = { Text("Amount (${state.sourceCurrency})") },
                                placeholder = { Text("0.00") },
                                prefix = {
                                    Text(
                                        text = "${state.sourceCurrency} ",
                                        style = typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onSurfaceVariant
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                enabled = !state.isSaving,
                                isError = state.amountError != null,
                                supportingText = state.amountError?.let { err ->
                                    { Text(err) }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (state.isCrossCurrency) {
                                CrossCurrencyConversionCard(
                                    sourceCurrency = state.sourceCurrency,
                                    destinationCurrency = state.destinationCurrency,
                                    preview = state.preview,
                                    previewLoading = state.previewLoading,
                                    previewError = state.previewError,
                                    onRetry = onRetryPreview,
                                    onOpenAttribution = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse("https://www.exchangerate-api.com")
                                                )
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Optional Note card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.large,
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = state.noteInput,
                                onValueChange = onNoteChanged,
                                label = { Text("Note (optional)") },
                                placeholder = { Text("Add a description for this transfer") },
                                enabled = !state.isSaving,
                                isError = state.noteError != null,
                                supportingText = state.noteError?.let { err ->
                                    { Text(err) }
                                },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSelectorDropdown(
    label: String,
    selectedAccount: Account?,
    accounts: List<Account>,
    balancesFormatted: Map<String, String>,
    enabled: Boolean,
    onAccountSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = selectedAccount?.let { account ->
        "${account.displayLabel} (${account.currency})"
    } ?: "Select account"

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            accounts.forEach { account ->
                val balanceText = balancesFormatted[account.id].orEmpty()
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = account.displayLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (balanceText.isNotBlank()) {
                                    "${account.currency} · Available: $balanceText"
                                } else {
                                    account.currency
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onAccountSelected(account.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun CrossCurrencyConversionCard(
    sourceCurrency: String,
    destinationCurrency: String,
    preview: ConversionSnapshot?,
    previewLoading: Boolean,
    previewError: Boolean,
    onRetry: () -> Unit,
    onOpenAttribution: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = shapes.medium,
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (previewError) {
                colors.errorContainer.copy(alpha = 0.4f)
            } else {
                colors.secondaryContainer.copy(alpha = 0.45f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Automatic Currency Conversion ($sourceCurrency → $destinationCurrency)",
                    style = typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceVariant
                )
                if (previewLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            when {
                previewLoading -> {
                    Text(
                        text = "Fetching live exchange rate...",
                        style = typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                previewError -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "No network service to convert $sourceCurrency to $destinationCurrency. Transfer is blocked until conversion rate is available.",
                            style = typography.bodyMedium,
                            color = colors.error,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onRetry) {
                            Text("Retry")
                        }
                    }
                }
                preview != null -> {
                    val convertedFormatted = MoneyUtils.formatMinor(
                        preview.amountMinor,
                        destinationCurrency
                    )
                    Text(
                        text = "Recipient gets: $destinationCurrency $convertedFormatted",
                        style = typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Text(
                        text = "1 $sourceCurrency = ${preview.rate} $destinationCurrency · Rates by ExchangeRate-API",
                        style = typography.labelSmall,
                        color = colors.primary,
                        modifier = Modifier.clickable(onClick = onOpenAttribution)
                    )
                }
                else -> {
                    Text(
                        text = "Enter an amount to preview the converted total in $destinationCurrency.",
                        style = typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferErrorBanner(message: String) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = colors.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                color = colors.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private val previewTransferAccounts = listOf(
    Account(
        id = "acc-1",
        name = "Daily Checking",
        bankName = "Commercial Bank",
        accountType = "Checking",
        currency = "LKR",
        isDefault = true
    ),
    Account(
        id = "acc-2",
        name = "USD Foreign Reserve",
        bankName = "Sampath Bank",
        accountType = "Savings",
        currency = "USD",
        isDefault = false
    )
)

private val previewTransferState = AccountTransferState(
    isLoading = false,
    accounts = previewTransferAccounts,
    accountBalancesMinor = mapOf(
        "acc-1" to BigInteger.valueOf(150_000_00L),
        "acc-2" to BigInteger.valueOf(500_00L)
    ),
    accountBalancesFormatted = mapOf(
        "acc-1" to "LKR 150,000.00",
        "acc-2" to "USD 500.00"
    ),
    sourceAccountId = "acc-1",
    destinationAccountId = "acc-2",
    amountInput = "30000.00",
    noteInput = "Monthly USD savings transfer",
    preview = ConversionSnapshot(
        amountMinor = 100_00L,
        rate = "0.003333",
        provider = "ExchangeRate-API",
        rateAt = 1_710_000_000_000L,
        fetchedAt = 1_710_000_000_000L,
        cached = false
    )
)

@Preview(showBackground = true)
@Composable
private fun AccountTransferLightPreview() {
    ClarityTheme(darkTheme = false) {
        AccountTransferContent(
            state = previewTransferState,
            onBack = {},
            onSourceAccountSelected = {},
            onDestinationAccountSelected = {},
            onSwapAccounts = {},
            onAmountChanged = {},
            onNoteChanged = {},
            onRetryPreview = {},
            onSubmit = {}
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AccountTransferDarkPreview() {
    ClarityTheme(darkTheme = true) {
        AccountTransferContent(
            state = previewTransferState,
            onBack = {},
            onSourceAccountSelected = {},
            onDestinationAccountSelected = {},
            onSwapAccounts = {},
            onAmountChanged = {},
            onNoteChanged = {},
            onRetryPreview = {},
            onSubmit = {}
        )
    }
}
