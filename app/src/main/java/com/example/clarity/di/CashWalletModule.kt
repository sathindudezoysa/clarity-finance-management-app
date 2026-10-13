package com.example.clarity.di

import com.example.clarity.data.repository.CashWalletRepositoryImpl
import com.example.clarity.domain.repository.CashWalletRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CashWalletModule {
    @Binds @Singleton
    abstract fun bindCashWalletRepository(impl: CashWalletRepositoryImpl): CashWalletRepository
}
