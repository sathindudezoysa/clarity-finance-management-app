package com.example.clarity.presentation.transactions.proofs

import com.example.clarity.presentation.navigation.transactionIdArgument
import com.example.clarity.domain.model.ProofPolicy
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
        if (id == null) state.value = TransactionProofState(isLoading = false, error = ProofMessages.LOAD_ERROR)
        else {
            viewModelScope.launch {
                observe(id).catch { error ->
                    if (error is CancellationException) throw error
                    state.update { it.copy(isLoading = false, error = ProofMessages.LOAD_ERROR) }
                }.collect { rows -> state.update { it.copy(proofs = rows, isLoading = false) } }
            }
            // Network failure never hides locally saved proofs; retry is available below the list.
            viewModelScope.launch { refresh(id).onFailure { state.update { it.copy(error = ProofMessages.SYNC_ERROR) } } }
        }
    }

    fun attachFile(uri: String) = action(ProofMessages.ATTACH_ERROR) { attach(checkNotNull(id), uri).getOrThrow() }
    fun openFile(proofId: String) = action(ProofMessages.OPEN_ERROR) { opens.send(open(checkNotNull(id), proofId).getOrThrow()) }
    fun verifyAmount(proofId: String, amount: Long, verified: Boolean) = action(ProofMessages.VERIFY_ERROR) {
        verify(checkNotNull(id), proofId, amount, verified).getOrThrow()
    }
    fun removeMissing(proofId: String) = action(ProofMessages.REMOVE_ERROR) { removeUnavailable(checkNotNull(id), proofId).getOrThrow() }
    fun retry() = action(ProofMessages.SYNC_ERROR) { refresh(checkNotNull(id)).getOrThrow() }
    fun viewerUnavailable() { state.update { it.copy(error = ProofMessages.VIEWER_ERROR) } }

    private fun action(errorMessage: String, block: suspend () -> Unit) {
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
