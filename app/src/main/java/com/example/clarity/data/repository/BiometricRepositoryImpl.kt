package com.example.clarity.data.repository

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import com.example.clarity.domain.model.BiometricAvailability
import com.example.clarity.domain.repository.BiometricRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : BiometricRepository {

    private val prefs = context.getSharedPreferences("clarity_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_BIOMETRIC_ENABLED = "biometric_lock_enabled"
    }

    override fun observeBiometricEnabled(): Flow<Boolean> = callbackFlow {
        // Emit the initial value immediately when a collector starts listening
        trySend(prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false))

        // Listen for future changes to SharedPreferences
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == KEY_BIOMETRIC_ENABLED) {
                trySend(sharedPreferences.getBoolean(KEY_BIOMETRIC_ENABLED, false))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)

        // Clean up the listener when the coroutine scope is cancelled to prevent memory leaks
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    override fun checkBiometricAvailability(): BiometricAvailability {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
            else -> BiometricAvailability.UNKNOWN
        }
    }
}