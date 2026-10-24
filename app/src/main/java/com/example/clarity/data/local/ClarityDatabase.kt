package com.example.clarity.data.local

import com.example.clarity.data.local.entity.TransactionProofEntity
import com.example.clarity.data.local.dao.TransactionProofDao
import com.example.clarity.data.local.dao.IncomeSourceDao
import com.example.clarity.data.local.entity.IncomeSourceEntity
import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.clarity.data.local.dao.AccountDao
import com.example.clarity.data.local.dao.GoalDao
import com.example.clarity.data.local.entity.AccountEntity
import com.example.clarity.data.local.entity.GoalEntity
import com.example.clarity.data.local.entity.TransactionEntity
import com.example.clarity.data.local.dao.TransactionDao
import com.example.clarity.data.local.dao.TransactionTagDao
import com.example.clarity.data.local.entity.TransactionTagEntity

@Database(
    entities = [
        TransactionEntity::class,
        TransactionTagEntity::class,
        AccountEntity::class,
        com.example.clarity.data.local.entity.ExchangeRateEntity::class,
        com.example.clarity.data.local.entity.CurrencyPreferenceEntity::class,
        TransactionProofEntity::class,
        IncomeSourceEntity::class,
        com.example.clarity.data.local.entity.BudgetEntity::class,
        com.example.clarity.data.local.entity.BudgetAlertEntity::class,
        com.example.clarity.data.local.entity.BudgetResolutionEntity::class,
        GoalEntity::class
    ],
    version = ClarityDatabase.SCHEMA_VERSION,
    exportSchema = false
)
abstract class ClarityDatabase : RoomDatabase() {
    abstract val budgetDao: com.example.clarity.data.local.dao.BudgetDao
    abstract val transactionDao: TransactionDao
    abstract val transactionTagDao: TransactionTagDao
    abstract val incomeSourceDao: IncomeSourceDao
    abstract val transactionProofDao: TransactionProofDao
    abstract val accountDao: AccountDao
    abstract val currencyDao: com.example.clarity.data.local.dao.CurrencyDao
    abstract val goalDao: GoalDao

    companion object {
        const val DATABASE_NAME = "clarity_db"
        const val SCHEMA_VERSION = 16
    }
}
