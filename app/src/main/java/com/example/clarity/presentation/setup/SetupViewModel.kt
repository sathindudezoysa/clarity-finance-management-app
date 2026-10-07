package com.example.clarity.presentation.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.User
import com.example.clarity.domain.usecase.UpdateUserProfileUseCase
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupState(
    val name: String = "",
    val photoUri: String? = null,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val updateUserProfileUseCase: UpdateUserProfileUseCase,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state.asStateFlow()

    init {
        auth.currentUser?.let { user ->
            _state.update { it.copy(
                name = user.displayName ?: ""
            ) }
        }
    }

    fun onNameChange(name: String) {
        _state.update { it.copy(name = name) }
    }

    fun onPhotoSelected(uri: String?) {
        _state.update { it.copy(photoUri = uri) }
    }

    fun completeSetup() {
        if (_state.value.name.isBlank()) {
            _state.update { it.copy(errorMessage = "Name cannot be empty") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val currentUser = auth.currentUser
            if (currentUser == null) {
                _state.update { it.copy(isLoading = false, errorMessage = "Not authenticated") }
                return@launch
            }

            val user = User(
                uid = currentUser.uid,
                email = currentUser.email,
                displayName = _state.value.name,
                photoUrl = null, 
                preferredCurrency = "USD",
                isSetupComplete = true
            )

            val result = updateUserProfileUseCase(user, _state.value.photoUri)
            result.onSuccess {
                _state.update { it.copy(isLoading = false, isSuccess = true) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }
}
