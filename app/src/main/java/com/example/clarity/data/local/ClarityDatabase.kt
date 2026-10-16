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
    entities = [SampleEntity::class, TransactionEntity::class, TransactionTagEntity::class, AccountEntity::class, TransactionProofEntity::class],
    version = 9,
    exportSchema = false
)
abstract class ClarityDatabase : RoomDatabase() {
    abstract val sampleDao: SampleDao
    abstract val transactionDao: TransactionDao
    abstract val transactionTagDao: TransactionTagDao
    abstract val accountDao: AccountDao
    abstract val transactionProofDao: TransactionProofDao

    companion object {
        const val DATABASE_NAME = "clarity_db"
    }
}
