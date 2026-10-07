package com.example.clarity.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.User
import com.example.clarity.domain.usecase.GetUserProfileUseCase
import com.example.clarity.domain.usecase.UpdateUserProfileUseCase
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileState(
    val user: User? = null,
    val name: String = "",
    val preferredCurrency: String = "USD",
    val photoUri: String? = null, 
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val updateUserProfileUseCase: UpdateUserProfileUseCase,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Not authenticated") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val result = getUserProfileUseCase(uid)
            result.onSuccess { user ->
                _state.update { 
                    it.copy(
                        user = user,
                        name = user.displayName ?: "",
                        preferredCurrency = user.preferredCurrency ?: "USD",
                        isLoading = false
                    ) 
                }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun onNameChange(name: String) {
        _state.update { it.copy(name = name) }
    }

    fun onCurrencyChange(currency: String) {
        _state.update { it.copy(preferredCurrency = currency) }
    }

    fun onPhotoSelected(uri: String?) {
        _state.update { it.copy(photoUri = uri) }
    }

    fun saveProfile() {
        if (_state.value.name.isBlank()) {
            _state.update { it.copy(errorMessage = "Name cannot be empty") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null, isSuccess = false) }
            
            val currentUser = _state.value.user ?: return@launch
            val updatedUser = currentUser.copy(
                displayName = _state.value.name,
                preferredCurrency = _state.value.preferredCurrency,
                isSetupComplete = true
            )

            val result = updateUserProfileUseCase(updatedUser, _state.value.photoUri)
            result.onSuccess {
                _state.update { it.copy(isLoading = false, isSuccess = true, user = updatedUser, photoUri = null) }
                // Reload profile or we just rely on local state update. 
                // We clear photoUri so that next time we don't re-upload if we save again.
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }
}
