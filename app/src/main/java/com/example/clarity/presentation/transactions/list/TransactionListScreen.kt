package com.example.clarity.presentation.transactions.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.util.MoneyUtils
import com.example.clarity.presentation.common.AppIcons
import com.example.clarity.presentation.navigation.BottomNavDestination
import com.example.clarity.presentation.navigation.ClarityTopBar
import com.example.clarity.presentation.theme.ClarityTheme
import com.example.clarity.presentation.transactions.TransactionMessages
import com.example.clarity.presentation.transactions.formatTransactionDate

@Composable
fun TransactionListScreen(
    onBack: () -> Unit = {},
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onNavigateToProfile: () -> Unit = {},
    topBar: @Composable () -> Unit = {
        ClarityTopBar(
            title = BottomNavDestination.TRANSACTIONS.label,
            onProfileClick = onNavigateToProfile
        )
    },
    bottomBar: @Composable () -> Unit = {},
    viewModel: TransactionListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TransactionListContent(state, onBack, onAdd, onOpen, topBar, bottomBar)
}

@Composable
private fun TransactionListContent(
    state: TransactionListState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    topBar: @Composable () -> Unit = {
        ClarityTopBar(
            title = BottomNavDestination.TRANSACTIONS.label,
            onProfileClick = {}
        )
    },
    bottomBar: @Composable () -> Unit = {}
) {
    val addDescription = "Add transaction"
    val colors = MaterialTheme.colorScheme
    Scaffold(
        topBar = topBar,
        bottomBar = bottomBar,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                modifier = Modifier.semantics { contentDescription = addDescription }
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 88.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.pendingCount > 0) {
                        Text(
                            text = String.format("Incomplete totals: %d pending conversion(s) excluded.", state.pendingCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    if (state.totalsByCurrency.isNotEmpty()) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                state.totalsByCurrency.entries.forEachIndexed { index, (currency, totals) ->
                                    if (index > 0) {
                                        Divider(color = colors.onSurface.copy(alpha = 0.08f))
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = currency,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.onSurfaceVariant
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Total(Modifier.weight(1f), "Income", MoneyUtils.formatMinor(totals.income, currency))
                                            Total(Modifier.weight(1f), "Expense", MoneyUtils.formatMinor(totals.expense, currency))
                                            Total(Modifier.weight(1f), "Net", MoneyUtils.formatMinor(totals.net, currency))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (state.transactions.any { it.conversion?.provider == "ExchangeRate-API" }) {
                        com.example.clarity.presentation.transactions.RateAttribution()
                    }
                }
            }
            state.error?.let { error -> item { Text(error, color = colors.error) } }
            if (state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.transactions.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(colors.secondaryContainer.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.Transactions,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                text = "No transactions yet. Add your first income or expense.",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            items(state.transactions, key = { it.id }) { transaction ->
                val isIncome = transaction.type == TransactionType.INCOME
                val accountLabel = state.accountNames[transaction.accountId] ?: if (transaction.accountId == null)
                    TransactionMessages.ACCOUNT_UNASSIGNED else TransactionMessages.ACCOUNT_UNAVAILABLE
                val formattedAmount = transaction.currency + " " + MoneyUtils.formatMinor(transaction.amountMinor, transaction.currency)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onOpen(transaction.id) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colors.secondaryContainer.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isIncome) AppIcons.ArrowDownward else AppIcons.ArrowUpward,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = transaction.category,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (isIncome) "+$formattedAmount" else "-$formattedAmount",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primary,
                                    textAlign = TextAlign.End
                                )
                            }
                            Text(
                                text = "Account: $accountLabel",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            com.example.clarity.presentation.transactions.ConversionInfo(
                                transaction.targetCurrency,
                                transaction.conversion,
                                transaction.pendingConversion
                            )
                            if (transaction.note.isNotBlank()) {
                                Text(
                                    text = transaction.note,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Text(
                                text = formatTransactionDate(transaction.dateEpochMillis),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Total(modifier: Modifier, label: String, value: String) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Preview(showBackground = true)
@Composable
private fun TransactionListPreview() {
    ClarityTheme {
        TransactionListContent(
            TransactionListState(isLoading = false, transactions = listOf(Transaction(amountMinor = 1250))),
            {}, {}, {}
        )
    }
}
