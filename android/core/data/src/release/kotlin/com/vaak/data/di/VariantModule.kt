package com.vaak.data.di

import android.content.Context
import com.vaak.data.analytics.AnalyticsSink
import com.vaak.data.analytics.NoOpSink
import com.vaak.data.audio.StatFsStorageInfo
import com.vaak.data.audio.StorageInfo
import com.vaak.data.billing.AccountServer
import com.vaak.data.billing.BillingRepository
import com.vaak.data.billing.PlayBillingRepository
import com.vaak.data.billing.RemoteAccountServer
import com.vaak.data.repo.EntitlementRepository
import com.vaak.data.repo.Entitlements
import com.vaak.data.work.AnalysisPipeline
import com.vaak.data.work.RemoteAnalysisPipeline
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Release builds: the real Worker pipeline, real free storage, Google Play Billing and the Worker's account routes.
 * No debug hooks (build-plan "Debug hooks"). Analytics events are dropped until Firebase is added (ADR-007).
 */
@Module
@InstallIn(SingletonComponent::class)
object VariantModule {
    @Provides
    @Singleton
    fun pipeline(remote: RemoteAnalysisPipeline): AnalysisPipeline = remote

    @Provides
    @Singleton
    fun storage(@ApplicationContext context: Context): StorageInfo = StatFsStorageInfo(context)

    @Provides
    @Singleton
    fun billing(play: PlayBillingRepository): BillingRepository = play

    @Provides
    @Singleton
    fun accountServer(remote: RemoteAccountServer): AccountServer = remote

    @Provides
    @Singleton
    fun entitlements(repo: EntitlementRepository): Entitlements = repo

    @Provides
    @Singleton
    fun analyticsSink(): AnalyticsSink = NoOpSink
}
