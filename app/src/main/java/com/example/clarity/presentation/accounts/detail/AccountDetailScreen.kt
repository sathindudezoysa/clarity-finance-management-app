package com.example.clarity.presentation.accounts.detail

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionTags
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.presentation.common.AppIcons
import com.example.clarity.presentation.theme.ClarityTheme
import java.math.BigInteger

@Composable
fun AccountDetailScreen(
    onBack: () -> Unit,
    onEditAccount: (String) -> Unit,
    onSendMoney: (String) -> Unit,
    onOpenTransaction: (String) -> Unit,
    onAddAccount: () -> Unit = {},
    viewModel: AccountDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AccountDetailContent(
        state = state,
        onBack = onBack,
        onEditAccount = onEditAccount,
        onSendMoney = onSendMoney,
        onOpenTransaction = onOpenTransaction,
        onAddAccount = onAddAccount,
        onMakeDefault = viewModel::makeDefault
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountDetailContent(
    state: AccountDetailState,
    onBack: () -> Unit,
    onEditAccount: (String) -> Unit,
    onSendMoney: (String) -> Unit,
    onOpenTransaction: (String) -> Unit,
    onAddAccount: () -> Unit = {},
    onMakeDefault: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    val account = state.account

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = account?.name?.ifBlank { "Account Details" } ?: "Account Details",
                        style = typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (account != null && !account.isCashWallet) {
                        IconButton(
                            onClick = { onEditAccount(account.id) },
                            modifier = Modifier.semantics { contentDescription = "Edit account" }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit account"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground,
                    actionIconContentColor = colors.onBackground
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                state.error?.let { errorMsg ->
                    item(key = "error") {
                        AccountDetailErrorBanner(message = errorMsg)
                    }
                }

                state.actionError?.let { actionErrorMsg ->
                    item(key = "action-error") {
                        AccountDetailErrorBanner(message = actionErrorMsg)
                    }
                }

                if (state.isLoading) {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (account != null) {
                    item(key = "account-summary") {
                        AccountOverviewCard(
                            account = account,
                            balanceFormatted = state.balanceFormatted,
                            isMakingDefault = state.isMakingDefault,
                            onSendMoney = { onSendMoney(account.id) },
                            onMakeDefault = onMakeDefault
                        )
                    }

                    if (state.otherAccountsCount == 0) {
                        item(key = "transfer-hint") {
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = shapes.medium,
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = colors.surfaceVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Need another account to transfer funds?",
                                            style = typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.onSurface
                                        )
                                        Text(
                                            text = "Add a second account to send money between accounts.",
                                            style = typography.bodySmall,
                                            color = colors.onSurfaceVariant
                                        )
                                    }
                                    FilledTonalButton(onClick = onAddAccount) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Add")
                                    }
                                }
                            }
                        }
                    }

                    item(key = "transactions-header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Account Transactions",
                                style = typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onBackground
                            )
                            Text(
                                text = "${state.transactions.size} ${if (state.transactions.size == 1) "entry" else "entries"}",
                                style = typography.labelMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }

                    if (state.transactions.isEmpty()) {
                        item(key = "empty-transactions") {
                            OutlinedCard(
                                shape = shapes.large,
                                colors = CardDefaults.outlinedCardColors(containerColor = colors.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
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
                                        text = "No transactions for this account yet",
                                        style = typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Transactions recorded with ${account.name} or inter-account transfers will appear here.",
                                        style = typography.bodyMedium,
                                        color = colors.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(
                            items = state.transactions,
                            key = { it.transaction.id }
                        ) { item ->
                            AccountTransactionRow(
                                item = item,
                                onClick = { onOpenTransaction(item.transaction.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountOverviewCard(
    account: Account,
    balanceFormatted: String,
    isMakingDefault: Boolean,
    onSendMoney: () -> Unit,
    onMakeDefault: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    val showBankName = account.bankName.isNotBlank() && !account.bankName.equals(account.name, ignoreCase = true)
    val typeLabel = account.accountType?.takeIf { it.isNotBlank() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(shapes.medium)
                        .background(colors.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (account.isCashWallet) AppIcons.Wallet else Icons.Outlined.AccountBox,
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = account.name,
                        style = typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (showBankName) {
                        Text(
                            text = account.bankName,
                            style = typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = shapes.small,
                    color = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer
                ) {
                    Text(
                        text = account.currency,
                        style = typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                if (typeLabel != null) {
                    Surface(
                        shape = shapes.small,
                        color = colors.surfaceVariant,
                        contentColor = colors.onSurfaceVariant
                    ) {
                        Text(
                            text = typeLabel,
                            style = typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                if (account.isDefault) {
                    Surface(
                        shape = shapes.small,
                        color = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Default",
                                style = typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else if (!account.isCashWallet) {
                    Surface(
                        shape = shapes.small,
                        color = colors.surfaceVariant,
                        contentColor = colors.onSurfaceVariant,
                        modifier = Modifier
                            .clip(shapes.small)
                            .clickable(
                                enabled = !isMakingDefault,
                                role = Role.Button,
                                onClick = onMakeDefault
                            )
                    ) {
                        Text(
                            text = if (isMakingDefault) "Setting default..." else "Set as default",
                            style = typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Divider(color = colors.outlineVariant)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Total Balance",
                    style = typography.labelLarge,
                    color = colors.onSurfaceVariant
                )
                Text(
                    text = balanceFormatted,
                    style = typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
            }

            Button(
                onClick = onSendMoney,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Transactions,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Send Money",
                    style = typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun AccountTransactionRow(
    item: AccountTransactionItemUiModel,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    val tx = item.transaction
    val isIncome = tx.type == TransactionType.INCOME
    val isTransfer = tx.category.equals(TransactionTags.MONEY_TRANSFER, ignoreCase = true)

    val badgeContainerColor = when {
        isTransfer -> colors.secondaryContainer
        isIncome -> colors.primaryContainer
        else -> colors.errorContainer.copy(alpha = 0.7f)
    }
    val badgeContentColor = when {
        isTransfer -> colors.onSecondaryContainer
        isIncome -> colors.onPrimaryContainer
        else -> colors.onErrorContainer
    }
    val amountColor = if (isIncome) colors.primary else colors.error

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.medium)
            .clickable(role = Role.Button, onClick = onClick),
        shape = shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(badgeContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isTransfer -> AppIcons.Transactions
                        isIncome -> AppIcons.ArrowDownward
                        else -> AppIcons.ArrowUpward
                    },
                    contentDescription = null,
                    tint = badgeContentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = tx.category.ifBlank { "Uncategorized" },
                        style = typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isTransfer) {
                        Surface(
                            shape = shapes.extraSmall,
                            color = colors.secondaryContainer,
                            contentColor = colors.onSecondaryContainer
                        ) {
                            Text(
                                text = "Transfer",
                                style = typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (item.hasReceipt) {
                        Surface(
                            shape = shapes.extraSmall,
                            color = colors.tertiaryContainer,
                            contentColor = colors.onTertiaryContainer
                        ) {
                            Text(
                                text = "Receipt",
                                style = typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (tx.note.isNotBlank()) {
                    Text(
                        text = tx.note,
                        style = typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = item.formattedDate,
                    style = typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.formattedAmount,
                    style = typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                    maxLines = 1
                )
                if (!tx.currency.equals(tx.targetCurrency, ignoreCase = true) && tx.conversion != null) {
                    Text(
                        text = "Rate ${tx.conversion.rate}",
                        style = typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AccountDetailErrorBanner(message: String) {
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

private val previewAccount = Account(
    id = "acc-1",
    name = "Daily Checking",
    bankName = "Commercial Bank",
    accountType = "Checking",
    currency = "LKR",
    isDefault = true
)

private val previewAccountDetailState = AccountDetailState(
    accountId = "acc-1",
    isLoading = false,
    account = previewAccount,
    balanceMinor = BigInteger.valueOf(125_400_00L),
    balanceFormatted = "LKR 125,400.00",
    otherAccountsCount = 2,
    transactions = listOf(
        AccountTransactionItemUiModel(
            transaction = Transaction(
                id = "tx-1",
                type = TransactionType.EXPENSE,
                amountMinor = 15_000_00L,
                category = TransactionTags.MONEY_TRANSFER,
                note = "Transfer to USD Savings (USD 50.00 @ 0.003333)",
                dateEpochMillis = 1_710_000_000_000L,
                accountId = "acc-1",
                currency = "LKR",
                targetCurrency = "LKR"
            ),
            resolvedAmountMinor = BigInteger.valueOf(15_000_00L),
            formattedAmount = "-LKR 15,000.00",
            formattedDate = "Mar 10, 2024"
        ),
        AccountTransactionItemUiModel(
            transaction = Transaction(
                id = "tx-2",
                type = TransactionType.INCOME,
                amountMinor = 180_000_00L,
                category = "Salary",
                note = "Monthly salary credit",
                dateEpochMillis = 1_709_500_000_000L,
                accountId = "acc-1",
                currency = "LKR",
                targetCurrency = "LKR"
            ),
            resolvedAmountMinor = BigInteger.valueOf(180_000_00L),
            formattedAmount = "+LKR 180,000.00",
            formattedDate = "Mar 04, 2024",
            hasReceipt = true
        )
    )
)

@Preview(showBackground = true)
@Composable
private fun AccountDetailLightPreview() {
    ClarityTheme(darkTheme = false) {
        AccountDetailContent(
            state = previewAccountDetailState,
            onBack = {},
            onEditAccount = {},
            onSendMoney = {},
            onOpenTransaction = {}
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AccountDetailDarkPreview() {
    ClarityTheme(darkTheme = true) {
        AccountDetailContent(
            state = previewAccountDetailState,
            onBack = {},
            onEditAccount = {},
            onSendMoney = {},
            onOpenTransaction = {}
        )
    }
}
