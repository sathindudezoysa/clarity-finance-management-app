package com.example.clarity.data.local

import com.example.clarity.data.local.entity.TransactionProofEntity
import com.example.clarity.data.local.dao.TransactionProofDao
import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.clarity.data.local.dao.AccountDao
import com.example.clarity.data.local.entity.AccountEntity
import com.example.clarity.data.local.dao.SampleDao
import com.example.clarity.data.local.entity.SampleEntity
import com.example.clarity.data.local.entity.TransactionEntity
import com.example.clarity.data.local.dao.TransactionDao
import com.example.clarity.data.local.dao.TransactionTagDao
import com.example.clarity.data.local.entity.TransactionTagEntity

@Database(
    entities = [SampleEntity::class, TransactionEntity::class, TransactionTagEntity::class, AccountEntity::class,
        com.example.clarity.data.local.entity.WalletBankAccountEntity::class,
        com.example.clarity.data.local.entity.WalletTopUpEntity::class,
        com.example.clarity.data.local.entity.WalletCashLinkEntity::class,
        com.example.clarity.data.local.entity.WalletTopUpOptionEntity::class,
        com.example.clarity.data.local.entity.WalletAdjustmentEntity::class, TransactionProofEntity::class],
    version = ClarityDatabase.SCHEMA_VERSION,
    exportSchema = false
)
abstract class ClarityDatabase : RoomDatabase() {
    abstract val sampleDao: SampleDao
    abstract val transactionDao: TransactionDao
    abstract val transactionTagDao: TransactionTagDao
    abstract val cashWalletDao: com.example.clarity.data.local.dao.CashWalletDao
    abstract val transactionProofDao: TransactionProofDao
    abstract val accountDao: AccountDao

    companion object {
        const val DATABASE_NAME = "clarity_db"
        const val SCHEMA_VERSION = 10
    }
}
