package com.example.clarity.di

import com.example.clarity.data.repository.AuthRepositoryImpl
import com.example.clarity.data.repository.SampleRepositoryImpl
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.repository.SampleRepository
import com.example.clarity.domain.repository.UserRepository
import com.example.clarity.data.repository.UserRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSampleRepository(
        impl: SampleRepositoryImpl
    ): SampleRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        impl: UserRepositoryImpl
    ): UserRepository
}
