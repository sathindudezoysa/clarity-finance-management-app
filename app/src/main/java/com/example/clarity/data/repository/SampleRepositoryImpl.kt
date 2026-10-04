package com.example.clarity.data.repository

import com.example.clarity.data.local.dao.SampleDao
import com.example.clarity.data.mapper.toDomain
import com.example.clarity.data.mapper.toEntity
import com.example.clarity.data.remote.FirestoreDataSource
import com.example.clarity.domain.model.SampleModel
import com.example.clarity.domain.repository.SampleRepository
import com.example.clarity.domain.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class SampleRepositoryImpl @Inject constructor(
    private val sampleDao: SampleDao,
    private val firestoreDataSource: FirestoreDataSource
) : SampleRepository {

    override fun getHelloMessage(): Flow<Resource<SampleModel>> = flow {
        emit(Resource.Loading())

        val initialModel = SampleModel(
            id = "1",
            title = "Hello World!",
            description = "Welcome to Clarity Architecture (Clean Architecture + Single Activity + Fragments + Hilt + Firebase + Room)."
        )

        // Save local cache state in Room DB for offline network failure protection
        sampleDao.insertSample(initialModel.toEntity())

        // Read from local Room DB cache
        val localData = sampleDao.getSampleById("1")
        emit(Resource.Success(initialModel))
    }
}
