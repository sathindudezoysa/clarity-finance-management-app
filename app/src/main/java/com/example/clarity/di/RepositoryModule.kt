package com.example.clarity.di

import com.example.clarity.data.remote.IncomeSourceRemoteDataSource
import com.example.clarity.data.remote.FirestoreIncomeSourceDataSource
import com.example.clarity.domain.repository.IncomeSourceRepository
import com.example.clarity.data.repository.IncomeSourceRepositoryImpl
import com.example.clarity.domain.repository.TransactionProofRepository
import com.example.clarity.data.repository.TransactionProofRepositoryImpl
import com.example.clarity.data.remote.FirebaseProofDataSource
import com.example.clarity.data.remote.ProofRemoteDataSource
import com.example.clarity.data.repository.AuthRepositoryImpl
import com.example.clarity.data.repository.AccountRepositoryImpl
import com.example.clarity.data.sync.AccountUploader
import com.example.clarity.data.remote.AccountRemoteDataSource
import com.example.clarity.data.remote.FirestoreAccountDataSource
import com.example.clarity.domain.repository.AccountRepository
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.data.repository.TransactionRepositoryImpl
import com.example.clarity.domain.repository.TransactionRepository
import com.example.clarity.data.repository.TransactionTagRepositoryImpl
import com.example.clarity.domain.repository.TransactionTagRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindRates(impl: com.example.clarity.data.repository.ExchangeRateRepositoryImpl): com.example.clarity.domain.repository.ExchangeRateRepository

    @Binds
    abstract fun bindRateSource(impl: com.example.clarity.data.remote.HttpsExchangeRateRemoteDataSource): com.example.clarity.data.remote.ExchangeRateRemoteDataSource

    @Binds
    abstract fun bindCurrencyPreferences(impl: com.example.clarity.data.repository.CurrencyPreferenceRepositoryImpl): com.example.clarity.domain.repository.CurrencyPreferenceRepository
    companion object {
        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(ioDispatcher: CoroutineDispatcher): CoroutineScope =
            CoroutineScope(SupervisorJob() + ioDispatcher)
    }

    @Binds @Singleton
    abstract fun bindIncomeRemote(impl: FirestoreIncomeSourceDataSource): IncomeSourceRemoteDataSource

    @Binds @Singleton
    abstract fun bindIncomeSourceRepository(impl: IncomeSourceRepositoryImpl): IncomeSourceRepository

    @Binds
    @Singleton
    abstract fun bindTransactionProofRepository(impl: TransactionProofRepositoryImpl): TransactionProofRepository

    @Binds
    @Singleton
    abstract fun bindProofRemoteDataSource(impl: FirebaseProofDataSource): ProofRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindTransactionTagRepository(impl: TransactionTagRepositoryImpl): TransactionTagRepository

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    @Singleton
    internal abstract fun bindAccountUploader(impl: AccountRepositoryImpl): AccountUploader

    @Binds
    @Singleton
    abstract fun bindAccountRemoteDataSource(impl: FirestoreAccountDataSource): AccountRemoteDataSource
}
