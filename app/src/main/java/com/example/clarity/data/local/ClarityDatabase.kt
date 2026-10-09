package com.example.clarity.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.clarity.data.local.dao.SampleDao
import com.example.clarity.data.local.entity.SampleEntity
import com.example.clarity.data.local.entity.TransactionEntity
import com.example.clarity.data.local.dao.TransactionDao

@Database(
    entities = [SampleEntity::class, TransactionEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ClarityDatabase : RoomDatabase() {
    abstract val sampleDao: SampleDao
    abstract val transactionDao: TransactionDao

    companion object {
        const val DATABASE_NAME = "clarity_db"
    }
}
