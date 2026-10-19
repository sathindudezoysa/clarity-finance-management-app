package com.example.clarity.presentation.transactions.proofs

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
    val chooserTitle = "Open proof"
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
        onOpen = viewModel::openFile, onVerify = { id, verified -> viewModel.verifyAmount(id, amountMinor, verified) }, onRetry = viewModel::retry, onRemoveUnavailable = viewModel::removeMissing)
}

@Composable
fun TransactionProofContent(
    state: TransactionProofState, amountMinor: Long, enabled: Boolean,
    onAttach: () -> Unit, onOpen: (String) -> Unit, onVerify: (String, Boolean) -> Unit, onRetry: () -> Unit,
    onRemoveUnavailable: (String) -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Text("Transaction proofs", style = MaterialTheme.typography.titleMedium)
        if (state.proofs.any { it.verifies(amountMinor) }) ReceiptVerifiedIcon()
        Text("Attach up to 10 photos or PDFs, 10 MB each. Open a receipt and check its amount before marking it verified. Verification records your review.", style = MaterialTheme.typography.bodySmall)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        if (state.isLoading || state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        val canAct = enabled && !state.isBusy && !state.isLoading
        Button(onClick = onAttach, enabled = canAct && state.proofs.size < ProofPolicy.MAX_ATTACHMENTS,
            modifier = Modifier.fillMaxWidth()) { Text("Attach photo or PDF") }
        if (!state.isLoading && state.proofs.isEmpty()) Text("No proofs attached yet.")
        state.proofs.forEach { proof ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(proof.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(String.format("%s · %d KB", proof.mimeType, (proof.sizeBytes + 1023) / 1024), style = MaterialTheme.typography.bodySmall)
                    if (proof.unavailable) {
                        Text("This proof is missing from this device and cloud storage. Remove it, then attach the original again.", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { onRemoveUnavailable(proof.id) }, enabled = canAct) {
                            Text("Remove missing proof")
                        }
                    } else {
                        Text(if (proof.pendingUpload) "Saved on this device · upload pending" else "Uploaded securely", style = MaterialTheme.typography.bodySmall)
                        if (proof.verifies(amountMinor)) ReceiptVerifiedIcon()
                        TextButton(onClick = { onOpen(proof.id) }, enabled = canAct) { Text("Open proof") }
                        TextButton(onClick = { onVerify(proof.id, !proof.verifies(amountMinor)) }, enabled = canAct) {
                            Text(if (proof.verifies(amountMinor)) "Clear amount verification"
                                else "Mark receipt amount ${MoneyUtils.formatMinor(amountMinor)} as verified")
                        }
                    }
                }
            }
        }
        TextButton(onClick = onRetry, enabled = canAct) { Text("Refresh / retry upload") }
    }
}

@Composable
private fun ReceiptVerifiedIcon() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(R.drawable.ic_receipt_verified), "Receipt amount verified by you", tint = MaterialTheme.colorScheme.primary)
        Text("Receipt amount verified by you", color = MaterialTheme.colorScheme.primary)
    }
}
