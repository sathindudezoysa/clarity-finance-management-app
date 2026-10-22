package com.example.clarity.presentation.auth.login

import android.app.Activity
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.clarity.R
import com.example.clarity.presentation.auth.AuthFieldLabel
import com.example.clarity.presentation.auth.AuthOrDivider
import com.example.clarity.presentation.auth.AuthScaffoldLayout
import com.example.clarity.presentation.auth.AuthUiText
import com.example.clarity.presentation.auth.GoogleSignInButton
import com.example.clarity.presentation.auth.authTextFieldColors
import com.example.clarity.presentation.common.AppIcons
import com.example.clarity.presentation.theme.ClarityTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

@Composable
fun LoginScreen(
    onNavigateToMain: () -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    // Dialog state for password reset
    var resetEmail by rememberSaveable { mutableStateOf("") }

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    viewModel.onGoogleSignIn(idToken)
                } else {
                    viewModel.setErrorMessage("Google Sign In succeeded, but failed to retrieve ID token")
                }
            } catch (e: ApiException) {
                val errorMessage = when (e.statusCode) {
                    10 -> "Developer Error (10): Please ensure SHA-1 is added to Firebase project."
                    12500 -> "Sign in failed (12500): Verify your GoogleSignInOptions configuration."
                    12501 -> "Sign in canceled by user (12501)."
                    else -> "Google Sign-In failed with error code: ${e.statusCode}"
                }
                viewModel.setErrorMessage(errorMessage)
            }
        } else {
            viewModel.setErrorMessage("Google Sign-In intent canceled or failed (Result Code: ${result.resultCode})")
        }
    }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onNavigateToMain()
        }
    }

    if (state.isPasswordResetDialogVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.hidePasswordResetDialog() },
            title = { Text(text = "Reset Password") },
            text = {
                Column {
                    Text("Enter your email address to receive a password reset link.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text("Email") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onSendPasswordReset(resetEmail) }
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.hidePasswordResetDialog() }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    LoginContent(
        state = state,
        email = email,
        password = password,
        onEmailChange = { email = it },
        onPasswordChange = { password = it },
        onLoginClick = { viewModel.onLoginClick(email, password) },
        onForgotPasswordClick = { viewModel.showPasswordResetDialog() },
        onGoogleSignInClick = {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
            val googleSignInClient = GoogleSignIn.getClient(context, gso)
            googleSignInClient.signOut().addOnCompleteListener {
                launcher.launch(googleSignInClient.signInIntent)
            }
        },
        onNavigateToRegister = onNavigateToRegister
    )
}

@Composable
internal fun LoginContent(
    state: LoginState,
    email: String,
    password: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    AuthScaffoldLayout(title = AuthUiText.LOGIN_TITLE) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AuthFieldLabel(text = AuthUiText.EMAIL_LABEL)
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                placeholder = {
                    Text(
                        text = AuthUiText.EMAIL_LABEL,
                        color = colors.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = authTextFieldColors(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AuthFieldLabel(text = AuthUiText.PASSWORD_LABEL)
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                placeholder = {
                    Text(
                        text = AuthUiText.PASSWORD_LABEL,
                        color = colors.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) AppIcons.VisibilityOff else AppIcons.Visibility,
                            contentDescription = if (passwordVisible) AuthUiText.HIDE_PASSWORD else AuthUiText.SHOW_PASSWORD,
                            tint = colors.onSurfaceVariant
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = authTextFieldColors(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (!state.isLoading) onLoginClick() }
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onForgotPasswordClick)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Forgot Password?",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary
                )
            }
        }

        if (state.error != null) {
            Text(
                text = state.error,
                style = MaterialTheme.typography.bodySmall,
                color = colors.error
            )
        }

        if (state.passwordResetMessage != null) {
            Text(
                text = state.passwordResetMessage,
                style = MaterialTheme.typography.bodySmall,
                color = colors.primary
            )
        }

        Button(
            onClick = onLoginClick,
            enabled = !state.isLoading,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = colors.onPrimary
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = "Login",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        AuthOrDivider()

        GoogleSignInButton(
            onClick = onGoogleSignInClick,
            enabled = !state.isLoading
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onNavigateToRegister)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.onSurfaceVariant)) {
                        append("Don't have an account? ")
                    }
                    withStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.Bold)) {
                        append("Register here")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LoginPreviewContent(state: LoginState = LoginState()) {
    ClarityTheme {
        LoginContent(
            state = state,
            email = "",
            password = "",
            onEmailChange = {},
            onPasswordChange = {},
            onLoginClick = {},
            onForgotPasswordClick = {},
            onGoogleSignInClick = {},
            onNavigateToRegister = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() = LoginPreviewContent()

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoginScreenDarkPreview() = LoginPreviewContent()

@Preview(showBackground = true, fontScale = 1.5f)
@Composable
private fun LoginScreenLargeFontPreview() = LoginPreviewContent(
    state = LoginState(error = "Invalid email or password.")
)
