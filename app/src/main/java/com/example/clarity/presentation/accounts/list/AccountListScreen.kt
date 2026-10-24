package com.example.clarity.presentation.accounts.list

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material3.*
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
import com.example.clarity.presentation.accounts.AccountMessages
import com.example.clarity.presentation.common.AppIcons
import com.example.clarity.presentation.theme.ClarityTheme

@Composable
fun AccountListScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    viewModel: AccountListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AccountListContent(
        state = state,
        onBack = onBack,
        onAdd = onAdd,
        onEdit = onEdit,
        onMakeDefault = { id -> viewModel.makeDefault(id) },
        bottomBar = bottomBar
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountListContent(
    state: AccountListState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onMakeDefault: (String) -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    val addDescription = "Add account"
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bank accounts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            if (state.error != AccountMessages.SIGN_IN) {
                FloatingActionButton(
                    onClick = onAdd,
                    modifier = Modifier.semantics { contentDescription = addDescription }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.error?.let { error ->
                    item(key = "error") { AccountErrorRow(error) }
                }
                state.actionError?.let { error ->
                    item(key = "action-error") { AccountErrorRow(error) }
                }
                if (state.isLoading) {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (state.accounts.isEmpty() && state.error != AccountMessages.SIGN_IN) {
                    item(key = "empty") {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AccountBox,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Text(
                                    text = "No bank accounts yet. Add your first account to get started.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center
                                )
                                Button(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text("Add account")
                                }
                            }
                        }
                    }
                }
                items(state.accounts, key = { it.id }) { account ->
                    AccountCard(
                        account = account,
                        changingDefaultId = state.changingDefaultId,
                        onClick = { onEdit(account.id) },
                        onMakeDefault = { onMakeDefault(account.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountErrorRow(message: String) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.errorContainer.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = colors.error,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                color = colors.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AccountCard(
    account: Account,
    changingDefaultId: String?,
    onClick: () -> Unit,
    onMakeDefault: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val showBankName = account.bankName.isNotBlank() && !account.bankName.equals(account.name, ignoreCase = true)
    val typeLabel = account.accountType?.takeIf { it.isNotBlank() }
    val showSetDefault = !account.isCashWallet && !account.isDefault

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = if (showSetDefault) 8.dp else 16.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.secondaryContainer.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (account.isCashWallet) AppIcons.Wallet else Icons.Outlined.AccountBox,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (showBankName) {
                        Text(
                            text = account.bankName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = colors.surfaceVariant,
                            contentColor = colors.onSurfaceVariant
                        ) {
                            Text(
                                text = account.currency,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        if (typeLabel != null) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = colors.surfaceVariant,
                                contentColor = colors.onSurfaceVariant,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Text(
                                    text = typeLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        if (account.isDefault) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = colors.secondaryContainer,
                                contentColor = colors.onSecondaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant
                )
            }
            if (showSetDefault) {
                Divider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = colors.onSurface.copy(alpha = 0.08f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .heightIn(min = 48.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    if (changingDefaultId == account.id) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        TextButton(
                            onClick = onMakeDefault,
                            enabled = changingDefaultId == null,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Set as default")
                        }
                    }
                }
            }
        }
    }
}

private val previewAccountListState = AccountListState(
    isLoading = false,
    accounts = listOf(
        Account(
            id = "preview-1",
            name = "Daily Spending",
            bankName = "Commercial Bank",
            accountType = "Checking",
            currency = "LKR",
            isDefault = true
        ),
        Account(
            id = "preview-2",
            name = "Emergency Reserve",
            bankName = "Sampath Bank",
            accountType = "High-Yield Savings",
            currency = "USD",
            isDefault = false
        ),
        Account.defaultCashWallet(currency = "LKR", isDefault = false)
    )
)

@Composable
private fun AccountListPreviewContent(state: AccountListState = previewAccountListState) {
    ClarityTheme {
        AccountListContent(
            state = state,
            onBack = {},
            onAdd = {},
            onEdit = {},
            onMakeDefault = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountListPreview() = AccountListPreviewContent()

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AccountListDarkPreview() = AccountListPreviewContent()

@Preview(showBackground = true, fontScale = 1.5f)
@Composable
private fun AccountListLargeFontPreview() = AccountListPreviewContent()
