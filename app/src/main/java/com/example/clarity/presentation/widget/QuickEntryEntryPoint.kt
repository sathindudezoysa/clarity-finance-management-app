package com.example.clarity.presentation.widget

import android.content.Context
import com.example.clarity.domain.repository.WidgetQuickEntryRepository
import com.example.clarity.domain.usecase.widget.GetWidgetCategoriesUseCase
import com.example.clarity.domain.usecase.widget.GetWidgetWalletSummaryUseCase
import com.example.clarity.domain.usecase.widget.RecordWidgetTransactionUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Widgets are not Hilt-managed Android components, so they reach use cases through this entry point. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface QuickEntryEntryPoint {
    fun categories(): GetWidgetCategoriesUseCase
    fun walletSummary(): GetWidgetWalletSummaryUseCase
    fun record(): RecordWidgetTransactionUseCase
    fun currentUser(): com.example.clarity.domain.usecase.auth.ObserveCurrentUserUseCase
    fun quickEntry(): WidgetQuickEntryRepository

    companion object {
        fun from(context: Context): QuickEntryEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, QuickEntryEntryPoint::class.java)
    }
}
