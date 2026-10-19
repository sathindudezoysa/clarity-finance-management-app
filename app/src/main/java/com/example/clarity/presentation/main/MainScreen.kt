package com.example.clarity.presentation.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.presentation.budgets.BudgetDashboardSummary
import com.example.clarity.presentation.income.IncomeMonthlyPrompt

@Composable
fun MainScreen(
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToCashWallet: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToIncome: () -> Unit = {},
    onNavigateToBudgets: (String?) -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToTransactions,
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                shape = CircleShape,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Home", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.surfaceVariant)
                ) {
                    Icon(Icons.Outlined.Person, contentDescription = "Settings", tint = colors.onSurfaceVariant)
                }
            }

            IncomeMonthlyPrompt(onReview = onNavigateToIncome)
            BudgetDashboardSummary(onOpen = onNavigateToBudgets)

            // Hero Net Worth Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.primary,
                contentColor = colors.onPrimary,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable(onClick = viewModel::togglePrivacy)
                    ) {
                        Text("TOTAL NET WORTH", style = MaterialTheme.typography.labelMedium, color = colors.secondary)
                        Text(if (state.isHidden) "🙈" else "👁", style = MaterialTheme.typography.labelMedium)
                    }
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (state.isHidden) "••••••••" else state.netWorth,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(state.currency, style = MaterialTheme.typography.labelSmall, color = colors.secondary, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Divider(color = colors.onPrimary.copy(alpha = 0.15f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlowStatBox(
                            modifier = Modifier.weight(1f),
                            label = "↓ Income",
                            amount = if (state.isHidden) "••••••" else "${state.currency} ${state.income}",
                            subtitle = state.incomeSubtitle,
                            onClick = onNavigateToIncome
                        )
                        FlowStatBox(
                            modifier = Modifier.weight(1f),
                            label = "↑ Expenses",
                            amount = if (state.isHidden) "••••••" else "${state.currency} ${state.expenses}",
                            subtitle = state.expenseSubtitle,
                            onClick = onNavigateToTransactions
                        )
                    }
                }
            }

            // Cash Wallet Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onNavigateToCashWallet)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(colors.secondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("💳", style = MaterialTheme.typography.titleMedium)
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("CASH WALLET", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                                Box(Modifier.size(6.dp).clip(CircleShape).background(colors.tertiary))
                            }
                            Text(
                                text = if (state.isHidden) "••••" else "${state.currency} ${state.cashBalance}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("Physical cash balance", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surfaceVariant,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onNavigateToCashWallet)
                    ) {
                        Text(
                            text = "⊕ Top Up",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowStatBox(
    modifier: Modifier = Modifier,
    label: String,
    amount: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.onPrimary.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.secondary)
        Text(amount, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.onPrimary)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.secondary.copy(alpha = 0.8f))
    }
}
