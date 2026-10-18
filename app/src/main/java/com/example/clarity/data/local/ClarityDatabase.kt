package com.example.clarity.data.local

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
    entities = [SampleEntity::class, TransactionEntity::class, TransactionTagEntity::class, AccountEntity::class],
    version = ClarityDatabase.SCHEMA_VERSION,
    exportSchema = false
)
abstract class ClarityDatabase : RoomDatabase() {
    abstract val sampleDao: SampleDao
    abstract val transactionDao: TransactionDao
    abstract val transactionTagDao: TransactionTagDao
    abstract val accountDao: AccountDao

    companion object {
        const val DATABASE_NAME = "clarity_db"
        const val SCHEMA_VERSION = 6
    }
}
