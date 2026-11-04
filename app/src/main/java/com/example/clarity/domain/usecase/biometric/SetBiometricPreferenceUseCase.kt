package com.example.clarity.domain.usecase.biometric

import com.example.clarity.domain.repository.BiometricRepository
import javax.inject.Inject

class SetBiometricPreferenceUseCase @Inject constructor(
    private val repository: BiometricRepository
) {
    suspend operator fun invoke(enabled: Boolean) {
        repository.setBiometricEnabled(enabled)
    }
}