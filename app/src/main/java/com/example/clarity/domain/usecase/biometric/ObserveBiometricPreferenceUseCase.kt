package com.example.clarity.domain.usecase.biometric

import com.example.clarity.domain.repository.BiometricRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveBiometricPreferenceUseCase @Inject constructor(
    private val repository: BiometricRepository
) {
    operator fun invoke(): Flow<Boolean> = repository.observeBiometricEnabled()
}