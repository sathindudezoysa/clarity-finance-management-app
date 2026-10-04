package com.example.clarity.domain.usecase

import com.example.clarity.domain.model.SampleModel
import com.example.clarity.domain.repository.SampleRepository
import com.example.clarity.domain.util.Resource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetHelloMessageUseCase @Inject constructor(
    private val repository: SampleRepository
) {
    operator fun invoke(): Flow<Resource<SampleModel>> {
        return repository.getHelloMessage()
    }
}
