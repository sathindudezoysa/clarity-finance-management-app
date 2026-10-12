package com.example.clarity.domain.repository

import kotlinx.coroutines.flow.Flow

interface TransactionTagRepository {
    fun observeTags(): Flow<List<String>>
}
