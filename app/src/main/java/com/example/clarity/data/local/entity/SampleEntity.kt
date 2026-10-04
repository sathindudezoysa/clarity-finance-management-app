package com.example.clarity.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sample_table")
data class SampleEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String
)
