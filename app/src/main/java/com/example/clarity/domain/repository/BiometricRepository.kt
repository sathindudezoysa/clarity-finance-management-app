package com.example.clarity.domain.repository

import com.example.clarity.domain.model.BiometricAvailability
import kotlinx.coroutines.flow.Flow

interface BiometricRepository {
    fun observeBiometricEnabled(): Flow<Boolean>
    suspend fun setBiometricEnabled(enabled: Boolean)
    fun checkBiometricAvailability(): BiometricAvailability
}