package com.example.clarity.transactions

import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.repository.TransactionRepository
import com.example.clarity.domain.util.Resource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeTransactionRepository : TransactionRepository {
    val reads = MutableStateFlow<Resource<List<Transaction>>>(Resource.Success(emptyList()))
    var lastAdded: Transaction? = null
    var lastUpdated: Transaction? = null
    var lastDeleted: String? = null
    var writeResult: Result<Unit> = Result.success(Unit)
    var writeGate: CompletableDeferred<Unit>? = null

    fun publish(vararg transactions: Transaction) {
        reads.value = Resource.Success(transactions.sortedWith(
            compareByDescending<Transaction> { it.dateEpochMillis }.thenByDescending { it.createdAt }
        ))
    }

    override fun observeTransactions(): Flow<Resource<List<Transaction>>> = reads
    override fun observeTransaction(id: String): Flow<Resource<Transaction?>> = reads.map { result ->
        val transaction = result.data?.firstOrNull { it.id == id }
        when (result) {
            is Resource.Loading -> Resource.Loading<Transaction?>()
            is Resource.Success -> Resource.Success<Transaction?>(transaction)
            is Resource.Error -> Resource.Error<Transaction?>(result.message.orEmpty(), transaction)
        }
    }
    override suspend fun addTransaction(t: Transaction): Result<Unit> {
        writeGate?.await()
        if (writeResult.isSuccess) {
            lastAdded = t
            publish(*(reads.value.data.orEmpty() + t).toTypedArray())
        }
        return writeResult
    }
    override suspend fun updateTransaction(t: Transaction): Result<Unit> {
        writeGate?.await()
        if (writeResult.isSuccess) {
            lastUpdated = t
            publish(*(reads.value.data.orEmpty().filterNot { it.id == t.id } + t).toTypedArray())
        }
        return writeResult
    }
    override suspend fun deleteTransaction(id: String): Result<Unit> {
        if (writeResult.isSuccess) {
            lastDeleted = id
            publish(*reads.value.data.orEmpty().filterNot { it.id == id }.toTypedArray())
        }
        return writeResult
    }
}
