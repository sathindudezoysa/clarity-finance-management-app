package com.example.clarity.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.clarity.data.local.entity.SampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SampleDao {

    @Query("SELECT * FROM sample_table WHERE id = :id LIMIT 1")
    fun getSampleById(id: String): Flow<SampleEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(entity: SampleEntity)

    @Query("DELETE FROM sample_table")
    suspend fun clearAll()
}
