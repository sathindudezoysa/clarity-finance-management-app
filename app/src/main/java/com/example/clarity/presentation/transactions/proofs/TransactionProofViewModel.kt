package com.example.clarity.presentation.transactions.proofs

import com.example.clarity.presentation.navigation.transactionIdArgument
import com.example.clarity.domain.model.ProofPolicy
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.R
import com.example.clarity.domain.model.ProofOpenFile
import com.example.clarity.domain.usecase.transaction.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionProofViewModel @Inject constructor(
    savedState: SavedStateHandle,
    observe: ObserveTransactionProofsUseCase,
    private val attach: AttachTransactionProofUseCase,
    private val open: OpenTransactionProofUseCase,
    private val verify: VerifyReceiptAmountUseCase,
    private val refresh: RefreshTransactionProofsUseCase,
    private val removeUnavailable: RemoveUnavailableProofUseCase
) : ViewModel() {
    private val id = savedState.transactionIdArgument()?.takeIf { runCatching { ProofPolicy.requireId(it) }.isSuccess }
    private val state = MutableStateFlow(TransactionProofState())
    val uiState = state.asStateFlow()
    private val opens = Channel<ProofOpenFile>(Channel.BUFFERED)
    val openFiles = opens.receiveAsFlow()

    init {
        if (id == null) state.value = TransactionProofState(isLoading = false, error = R.string.proofs_load_error)
        else {
            viewModelScope.launch {
                observe(id).catch { error ->
                    if (error is CancellationException) throw error
                    state.update { it.copy(isLoading = false, error = R.string.proofs_load_error) }
                }.collect { rows -> state.update { it.copy(proofs = rows, isLoading = false) } }
            }
            // Network failure never hides locally saved proofs; retry is available below the list.
            viewModelScope.launch { refresh(id).onFailure { state.update { it.copy(error = R.string.proofs_sync_error) } } }
        }
    }

    fun attachFile(uri: String) = action(R.string.proofs_attach_error) { attach(checkNotNull(id), uri).getOrThrow() }
    fun openFile(proofId: String) = action(R.string.proofs_open_error) { opens.send(open(checkNotNull(id), proofId).getOrThrow()) }
    fun verifyAmount(proofId: String, amount: Long, verified: Boolean) = action(R.string.proofs_verify_error) {
        verify(checkNotNull(id), proofId, amount, verified).getOrThrow()
    }
    fun removeMissing(proofId: String) = action(R.string.proofs_remove_error) { removeUnavailable(checkNotNull(id), proofId).getOrThrow() }
    fun retry() = action(R.string.proofs_sync_error) { refresh(checkNotNull(id)).getOrThrow() }
    fun viewerUnavailable() { state.update { it.copy(error = R.string.proofs_viewer_error) } }

    private fun action(errorMessage: Int, block: suspend () -> Unit) {
        if (id == null || state.value.isBusy) return
        state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            try { block() } catch (error: Exception) {
                if (error is CancellationException) throw error
                state.update { it.copy(error = errorMessage) }
            } finally { state.update { it.copy(isBusy = false) } }
        }
    }
}
