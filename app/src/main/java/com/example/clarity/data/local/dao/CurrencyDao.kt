package com.example.clarity.data.local.dao

import androidx.room.*
import com.example.clarity.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CurrencyDao {
    @Query("SELECT * FROM exchange_rates WHERE source = :source AND target = :target")
    abstract suspend fun rate(source: String, target: String): ExchangeRateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putRates(rates: List<ExchangeRateEntity>)

    @Query("SELECT * FROM currency_preferences WHERE userId = :uid")
    abstract suspend fun preference(uid: String): CurrencyPreferenceEntity?

    @Query("SELECT * FROM currency_preferences WHERE userId = :uid")
    abstract fun observePreference(uid: String): Flow<CurrencyPreferenceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putPreference(preference: CurrencyPreferenceEntity)

    @Transaction
    open suspend fun editPreference(uid: String, currency: String, now: Long) {
        val old = preference(uid)
        require(old == null || old.updatedAt < Long.MAX_VALUE) { "Preference timestamp out of range" }
        putPreference(CurrencyPreferenceEntity(uid, currency, maxOf(now, (old?.updatedAt ?: 0) + 1), true))
    }

    @Transaction
    open suspend fun reconcilePreference(remote: CurrencyPreferenceEntity, uploaded: CurrencyPreferenceEntity?) {
        val local = preference(remote.userId)
        if (local == uploaded || local == null || (!local.pendingSync && remote.updatedAt >= local.updatedAt)) {
            putPreference(remote.copy(pendingSync = false))
        }
    }
}
