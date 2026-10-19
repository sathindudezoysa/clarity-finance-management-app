package com.example.clarity.di

import android.content.Context
import androidx.room.Room
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.local.dao.AccountDao
import com.example.clarity.data.local.dao.TransactionDao
import com.example.clarity.data.local.dao.TransactionTagDao
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
    ): ClarityDatabase = createDatabase(context, ClarityDatabase.DATABASE_NAME)

    fun createDatabase(context: Context, name: String): ClarityDatabase {
        return Room.databaseBuilder(
            context,
            ClarityDatabase::class.java,
            name
        )
            .fallbackToDestructiveMigration()
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    @Singleton
    fun provideTransactionDao(database: ClarityDatabase): TransactionDao = database.transactionDao

    @Provides
    @Singleton
    fun provideTransactionTagDao(database: ClarityDatabase): TransactionTagDao = database.transactionTagDao

    @Provides
    @Singleton
    fun provideAccountDao(database: ClarityDatabase): AccountDao = database.accountDao

    @Provides
    fun provideCurrencyDao(database: ClarityDatabase): com.example.clarity.data.local.dao.CurrencyDao = database.currencyDao
}
