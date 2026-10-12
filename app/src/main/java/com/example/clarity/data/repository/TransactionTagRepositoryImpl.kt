package com.example.clarity.data.repository

import com.example.clarity.data.local.dao.TransactionTagDao
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.repository.TransactionTagRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionTagRepositoryImpl @Inject constructor(
    private val dao: TransactionTagDao,
    private val authRepository: AuthRepository,
    private val ioDispatcher: CoroutineDispatcher
) : TransactionTagRepository {
    override fun observeTags(): Flow<List<String>> = authRepository.currentUser
        .distinctUntilChangedBy { it?.uid }
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else dao.observeTags(user.uid).onStart { emit(emptyList()) }.catch { error ->
                if (error is CancellationException) throw error
                // Predefined tags and manual entry remain available if suggestions cannot load.
                emit(emptyList())
            }
        }.flowOn(ioDispatcher)
}
