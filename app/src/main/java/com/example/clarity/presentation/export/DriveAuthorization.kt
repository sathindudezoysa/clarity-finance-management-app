package com.example.clarity.presentation.export

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope

const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

enum class DriveAuthFailure {
    NOT_CONFIGURED,
    OTHER
}

@Composable
fun rememberDriveAuthorizer(
    onToken: (String) -> Unit,
    onFailure: (DriveAuthFailure) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val currentOnToken by rememberUpdatedState(onToken)
    val currentOnFailure by rememberUpdatedState(onFailure)
    val authClient = remember(context) { Identity.getAuthorizationClient(context) }

    val resolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode != Activity.RESULT_OK || activityResult.data == null) {
            return@rememberLauncherForActivityResult
        }
        try {
            val authResult = authClient.getAuthorizationResultFromIntent(activityResult.data)
            val token = authResult.accessToken
            if (!token.isNullOrBlank()) {
                currentOnToken(token)
            } else {
                currentOnFailure(DriveAuthFailure.OTHER)
            }
        } catch (e: ApiException) {
            when (e.statusCode) {
                CommonStatusCodes.CANCELED -> Unit
                CommonStatusCodes.DEVELOPER_ERROR -> currentOnFailure(DriveAuthFailure.NOT_CONFIGURED)
                else -> currentOnFailure(DriveAuthFailure.OTHER)
            }
        } catch (_: Exception) {
            currentOnFailure(DriveAuthFailure.OTHER)
        }
    }

    return remember(authClient, resolutionLauncher) {
        {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
                .build()
            authClient.authorize(request)
                .addOnSuccessListener { result ->
                    if (result.hasResolution()) {
                        val pendingIntent = result.pendingIntent
                        if (pendingIntent != null) {
                            try {
                                resolutionLauncher.launch(
                                    IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                )
                            } catch (_: Exception) {
                                currentOnFailure(DriveAuthFailure.OTHER)
                            }
                        } else {
                            currentOnFailure(DriveAuthFailure.OTHER)
                        }
                    } else {
                        val token = result.accessToken
                        if (!token.isNullOrBlank()) {
                            currentOnToken(token)
                        } else {
                            currentOnFailure(DriveAuthFailure.OTHER)
                        }
                    }
                }
                .addOnFailureListener { error ->
                    if (error is ApiException) {
                        when (error.statusCode) {
                            CommonStatusCodes.CANCELED -> Unit
                            CommonStatusCodes.DEVELOPER_ERROR -> currentOnFailure(DriveAuthFailure.NOT_CONFIGURED)
                            else -> currentOnFailure(DriveAuthFailure.OTHER)
                        }
                    } else {
                        currentOnFailure(DriveAuthFailure.OTHER)
                    }
                }
        }
    }
}

