package com.example.clarity.di

import android.content.Context
import androidx.room.Room
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.local.dao.SampleDao
import com.example.clarity.data.local.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): ClarityDatabase {
        return Room.databaseBuilder(
            context,
            ClarityDatabase::class.java,
            ClarityDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideSampleDao(database: ClarityDatabase): SampleDao {
        return database.sampleDao
    }

    @Provides
    @Singleton
    fun provideTransactionDao(database: ClarityDatabase): TransactionDao = database.transactionDao
}
