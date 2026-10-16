package com.example.clarity.presentation.transactions.proofs

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.clarity.R
import com.example.clarity.domain.model.ProofPolicy
import com.example.clarity.domain.util.MoneyUtils

@Composable
fun TransactionProofSection(amountMinor: Long, enabled: Boolean, viewModel: TransactionProofViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val chooserTitle = stringResource(R.string.proofs_open)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.attachFile(it.toString()) }
    }
    LaunchedEffect(viewModel, context, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.openFiles.collect { file ->
                val uri = Uri.parse(file.uri)
                if (uri.scheme != "content" || uri.authority != "${context.packageName}.proofs") {
                    viewModel.viewerUnavailable()
                } else try {
                    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, file.mimeType)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        .apply { clipData = ClipData.newRawUri("Proof", uri) }
                    context.startActivity(Intent.createChooser(intent, chooserTitle))
                } catch (_: ActivityNotFoundException) { viewModel.viewerUnavailable() }
                  catch (_: SecurityException) { viewModel.viewerUnavailable() }
            }
        }
    }
    TransactionProofContent(state, amountMinor, enabled,
        onAttach = { picker.launch(ProofPolicy.mimeTypes.toTypedArray()) },
        onOpen = viewModel::openFile, onVerify = { id, verified -> viewModel.verifyAmount(id, amountMinor, verified) }, onRetry = viewModel::retry)
}

@Composable
fun TransactionProofContent(
    state: TransactionProofState, amountMinor: Long, enabled: Boolean,
    onAttach: () -> Unit, onOpen: (String) -> Unit, onVerify: (String, Boolean) -> Unit, onRetry: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Divider()
        Text(stringResource(R.string.proofs_title), style = MaterialTheme.typography.titleMedium)
        if (state.proofs.any { it.verifies(amountMinor) }) ReceiptVerifiedIcon()
        Text(stringResource(R.string.proofs_hint), style = MaterialTheme.typography.bodySmall)
        state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        if (state.isLoading || state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        val canAct = enabled && !state.isBusy && !state.isLoading
        Button(onClick = onAttach, enabled = canAct && state.proofs.size < ProofPolicy.MAX_ATTACHMENTS,
            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.proofs_attach)) }
        if (!state.isLoading && state.proofs.isEmpty()) Text(stringResource(R.string.proofs_empty))
        state.proofs.forEach { proof ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(proof.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.proofs_file_info, proof.mimeType, (proof.sizeBytes + 1023) / 1024), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(if (proof.pendingUpload) R.string.proofs_pending else R.string.proofs_uploaded), style = MaterialTheme.typography.bodySmall)
                    if (proof.verifies(amountMinor)) ReceiptVerifiedIcon()
                    TextButton(onClick = { onOpen(proof.id) }, enabled = canAct) { Text(stringResource(R.string.proofs_open)) }
                    TextButton(onClick = { onVerify(proof.id, !proof.verifies(amountMinor)) }, enabled = canAct) {
                        Text(if (proof.verifies(amountMinor)) stringResource(R.string.proofs_unverify)
                            else stringResource(R.string.proofs_verify, MoneyUtils.formatMinor(amountMinor)))
                    }
                }
            }
        }
        TextButton(onClick = onRetry, enabled = canAct) { Text(stringResource(R.string.proofs_refresh)) }
    }
}

@Composable
private fun ReceiptVerifiedIcon() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(R.drawable.ic_receipt_verified), stringResource(R.string.proofs_verified), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.proofs_verified), color = MaterialTheme.colorScheme.primary)
    }
}
