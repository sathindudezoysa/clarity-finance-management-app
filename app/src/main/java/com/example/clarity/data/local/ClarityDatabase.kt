package com.example.clarity.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.clarity.data.local.dao.SampleDao
import com.example.clarity.data.local.entity.SampleEntity

@Database(
    entities = [SampleEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ClarityDatabase : RoomDatabase() {
    abstract val sampleDao: SampleDao

    companion object {
        const val DATABASE_NAME = "clarity_db"
    }
}
