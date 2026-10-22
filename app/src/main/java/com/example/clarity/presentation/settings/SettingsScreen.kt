package com.example.clarity.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.domain.model.Currencies

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onBankAccounts: () -> Unit,
    onSignOut: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var currencyMenuExpanded by remember { mutableStateOf(false) }
    var defaultAccountMenuExpanded by remember { mutableStateOf(false) }
    var isEditingName by remember { mutableStateOf(false) }

    LaunchedEffect(state.destination) {
        when (state.destination) {
            SettingsDestination.BACK -> onBack()
            SettingsDestination.BANK_ACCOUNTS -> onBankAccounts()
            SettingsDestination.LOGIN -> onSignOut()
            null -> return@LaunchedEffect
        }
        viewModel.onNavigationHandled()
    }

    val colors = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Profile & Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = viewModel::onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.primary
                        )
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(colors.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background
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
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. Profile Hero Card
                ProfileHeroCard(
                    displayName = state.displayName,
                    email = state.email,
                    isVerified = state.isVerified,
                    tierLabel = state.tierLabel,
                    streakLabel = state.streakLabel,
                    isEditingName = isEditingName,
                    isSavingName = state.isUpdatingProfile,
                    profileError = state.profileError,
                    onToggleEditName = { isEditingName = !isEditingName },
                    onDisplayNameChange = viewModel::onDisplayNameChange,
                    onSaveName = {
                        viewModel.saveProfileChanges()
                        isEditingName = false
                    }
                )

                // 2. Financial Preferences
                SettingsSection(title = "FINANCIAL PREFERENCES") {
                    Box {
                        SettingsRow(
                            icon = Icons.Outlined.Star,
                            title = "Primary Currency",
                            subtitle = currencyLabel(state.currency),
                            onClick = { if (!state.saving) currencyMenuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = currencyMenuExpanded,
                            onDismissRequest = { currencyMenuExpanded = false }
                        ) {
                            Currencies.supported.forEach { code ->
                                DropdownMenuItem(
                                    text = { Text(currencyLabel(code)) },
                                    trailingIcon = if (code == state.currency) {
                                        { Icon(Icons.Default.Check, contentDescription = null, tint = colors.primary) }
                                    } else null,
                                    onClick = {
                                        currencyMenuExpanded = false
                                        viewModel.setCurrency(code)
                                    }
                                )
                            }
                        }
                    }

                    if (state.error) {
                        Text(
                            text = "Could not save currency. Select to retry.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    SettingsDivider()

                    SettingsRow(
                        icon = Icons.Outlined.Refresh,
                        title = "Multi-Currency Sync",
                        subtitle = "Live rate conversion",
                        trailingContent = {
                            Surface(
                                shape = CircleShape,
                                color = colors.secondaryContainer
                            ) {
                                Text(
                                    text = state.currency,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    )

                    SettingsDivider()

                    Box {
                        SettingsRow(
                            icon = Icons.Outlined.AccountBox,
                            title = "Default Spending Account",
                            subtitle = state.defaultAccountName ?: "Manage bank accounts",
                            onClick = {
                                if (state.changingDefaultId == null) {
                                    if (state.bankAccounts.isEmpty()) {
                                        viewModel.onBankAccounts()
                                    } else {
                                        defaultAccountMenuExpanded = true
                                    }
                                }
                            },
                            trailingContent = if (state.changingDefaultId != null) {
                                {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            } else null
                        )
                        DropdownMenu(
                            expanded = defaultAccountMenuExpanded && state.bankAccounts.isNotEmpty(),
                            onDismissRequest = { defaultAccountMenuExpanded = false }
                        ) {
                            state.bankAccounts.forEach { account ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = account.displayLabel,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    trailingIcon = if (account.isDefault) {
                                        { Icon(Icons.Default.Check, contentDescription = null, tint = colors.primary) }
                                    } else null,
                                    enabled = state.changingDefaultId == null,
                                    onClick = {
                                        defaultAccountMenuExpanded = false
                                        viewModel.setDefaultAccount(account.id)
                                    }
                                )
                            }
                            Divider()
                            DropdownMenuItem(
                                text = { Text("Manage bank accounts") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = colors.onSurfaceVariant
                                    )
                                },
                                enabled = state.changingDefaultId == null,
                                onClick = {
                                    defaultAccountMenuExpanded = false
                                    viewModel.onBankAccounts()
                                }
                            )
                        }
                    }

                    state.defaultAccountError?.let { errorText ->
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    SettingsDivider()

                    SettingsRow(
                        icon = Icons.Outlined.Settings,
                        title = "Cash Threshold Top-up",
                        subtitle = "Auto-replenish limit",
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                state.cashThreshold?.let { threshold ->
                                    Text(
                                        text = threshold,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.primary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = colors.onSurfaceVariant
                                )
                            }
                        }
                    )
                }

                // 3. Cloud Sync & Data
                SettingsSection(title = "CLOUD SYNC & DATA") {
                    SettingsRow(
                        icon = Icons.Outlined.Refresh,
                        title = "Cloud Vault Sync",
                        subtitle = state.email ?: "Not connected",
                        trailingContent = {
                            state.cloudSyncSchedule?.let { schedule ->
                                Surface(
                                    shape = CircleShape,
                                    color = colors.secondaryContainer
                                ) {
                                    Text(
                                        text = schedule,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    )

                    SettingsDivider()

                    SettingsRow(
                        icon = Icons.Outlined.Info,
                        title = "Realtime Ledger",
                        subtitle = "Firestore Active",
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (state.pendingSync) colors.tertiary else colors.primary)
                                )
                                Text(
                                    text = if (state.pendingSync) "Pending" else "Synced",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.primary
                                )
                            }
                        }
                    )

                    SettingsDivider()

                    SettingsRow(
                        icon = Icons.Outlined.Share,
                        title = "Export Financial Audit",
                        subtitle = "Monthly CSV & PDF statements",
                        onClick = {}
                    )
                }

                // 4. Security & Biometrics
                SettingsSection(title = "SECURITY & BIOMETRICS") {
                    SettingsRow(
                        icon = Icons.Outlined.Lock,
                        title = "Face ID / Biometric Lock",
                        subtitle = "Instant app protection",
                        trailingContent = {
                            Switch(
                                checked = state.biometricEnabled ?: false,
                                onCheckedChange = viewModel::toggleBiometric
                            )
                        }
                    )

                    SettingsDivider()

                    SettingsRow(
                        icon = Icons.Outlined.Lock,
                        title = "Change Master Keypass",
                        subtitle = "Update account password",
                        onClick = {}
                    )

                    SettingsDivider()

                    SettingsRow(
                        icon = Icons.Outlined.Lock,
                        title = "Two-Factor Auth (2FA)",
                        subtitle = state.twoFactorStatus ?: "Not configured",
                        onClick = {}
                    )
                }

                // 5. Preferences & Support
                SettingsSection(title = "PREFERENCES & SUPPORT") {
                    SettingsRow(
                        icon = Icons.Outlined.Notifications,
                        title = "Notification Guardrails",
                        subtitle = "Budget caps & milestone alerts",
                        onClick = {}
                    )

                    SettingsDivider()

                    SettingsRow(
                        icon = Icons.Outlined.Settings,
                        title = "Appearance",
                        subtitle = state.appearanceMode ?: "System Adaptive",
                        onClick = {}
                    )

                    SettingsDivider()

                    SettingsRow(
                        icon = Icons.Outlined.Info,
                        title = "Help & Support",
                        subtitle = "Guides & support",
                        onClick = {}
                    )
                }

                // 6. Account Actions
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surface,
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = viewModel::onSignOut)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ExitToApp,
                            contentDescription = null,
                            tint = colors.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Log Out",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeroCard(
    displayName: String?,
    email: String?,
    isVerified: Boolean,
    tierLabel: String?,
    streakLabel: String?,
    isEditingName: Boolean,
    isSavingName: Boolean,
    profileError: String?,
    onToggleEditName: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onSaveName: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val initials = remember(displayName, email) {
        val source = displayName?.takeIf { it.isNotBlank() } ?: email?.substringBefore("@").orEmpty()
        source.split(" ", ".", "_")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "U" }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(colors.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colors.primary)
                        .clickable(onClick = onToggleEditName),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit Profile",
                        tint = colors.onPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            if (isEditingName) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = displayName.orEmpty(),
                        onValueChange = onDisplayNameChange,
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onSaveName,
                        enabled = !isSavingName,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (isSavingName) "..." else "Save",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable(onClick = onToggleEditName)
                ) {
                    Text(
                        text = displayName?.takeIf { it.isNotBlank() } ?: "Tap to set name",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface
                    )
                    if (isVerified) {
                        Surface(
                            shape = CircleShape,
                            color = colors.secondaryContainer
                        ) {
                            Text(
                                text = "Verified",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            if (profileError != null) {
                Text(
                    text = profileError,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error
                )
            }

            if (!email.isNullOrBlank()) {
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }

            if (tierLabel != null || streakLabel != null) {
                Surface(
                    shape = CircleShape,
                    color = colors.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tierLabel?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.primary
                            )
                        }
                        if (tierLabel != null && streakLabel != null) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(colors.onSurfaceVariant)
                            )
                        }
                        streakLabel?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val rowModifier = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.secondaryContainer.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (trailingContent != null) {
            trailingContent()
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    Divider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    )
}

private fun currencyLabel(code: String): String = when (code) {
    "LKR" -> "LKR (Rs) - Sri Lankan Rupee"
    "USD" -> "USD ($) - US Dollar"
    "EUR" -> "EUR (€) - Euro"
    "GBP" -> "GBP (£) - British Pound"
    "INR" -> "INR (₹) - Indian Rupee"
    "AUD" -> "AUD ($) - Australian Dollar"
    "CAD" -> "CAD ($) - Canadian Dollar"
    "SGD" -> "SGD ($) - Singapore Dollar"
    "JPY" -> "JPY (¥) - Japanese Yen"
    "KWD" -> "KWD - Kuwaiti Dinar"
    else -> code
}
