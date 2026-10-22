package com.example.clarity.presentation.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.clarity.presentation.common.AppIcons

object AuthUiText {
    const val LOGIN_TITLE = "Welcome"
    const val REGISTER_TITLE = "Create Account"
    const val EMAIL_LABEL = "Email"
    const val PASSWORD_LABEL = "Password"
    const val SHOW_PASSWORD = "Show password"
    const val HIDE_PASSWORD = "Hide password"
    const val OR_DIVIDER = "or"
    const val GOOGLE_BUTTON = "Continue with Google"
}

@Composable
fun ClarityLogoMark(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = AppIcons.Wallet,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun AuthScaffoldLayout(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Scaffold(
        modifier = modifier,
        containerColor = colors.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            // Header band in primary with overlapping rounded top sheet
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.primary)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 36.dp, bottom = 28.dp, start = 24.dp, end = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ClarityLogoMark()
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onPrimary,
                            textAlign = TextAlign.Center
                        )
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                        color = colors.surface
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 600.dp)
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 28.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                content = content
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuthFieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

@Composable
fun authTextFieldColors(): TextFieldColors {
    val colors = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = colors.secondaryContainer.copy(alpha = 0.25f),
        unfocusedContainerColor = colors.secondaryContainer.copy(alpha = 0.25f),
        disabledContainerColor = colors.secondaryContainer.copy(alpha = 0.12f),
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = colors.outlineVariant
    )
}

@Composable
fun AuthOrDivider(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Divider(
            modifier = Modifier.weight(1f),
            color = colors.outlineVariant
        )
        Text(
            text = AuthUiText.OR_DIVIDER,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant
        )
        Divider(
            modifier = Modifier.weight(1f),
            color = colors.outlineVariant
        )
    }
}

@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
    ) {
        Icon(
            imageVector = AppIcons.Google,
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = AuthUiText.GOOGLE_BUTTON,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface
        )
    }
}
