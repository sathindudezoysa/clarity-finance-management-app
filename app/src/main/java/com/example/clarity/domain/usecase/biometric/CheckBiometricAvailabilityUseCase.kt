package com.example.clarity.domain.usecase.biometric

import com.example.clarity.domain.model.BiometricAvailability
import com.example.clarity.domain.repository.BiometricRepository
import javax.inject.Inject

class CheckBiometricAvailabilityUseCase @Inject constructor(
    private val repository: BiometricRepository
) {
    operator fun invoke(): BiometricAvailability = repository.checkBiometricAvailability()
}