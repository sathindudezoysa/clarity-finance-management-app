package com.example.clarity.domain.repository

import com.example.clarity.domain.model.SampleModel
import com.example.clarity.domain.util.Resource
import kotlinx.coroutines.flow.Flow

interface SampleRepository {
    fun getHelloMessage(): Flow<Resource<SampleModel>>
}
