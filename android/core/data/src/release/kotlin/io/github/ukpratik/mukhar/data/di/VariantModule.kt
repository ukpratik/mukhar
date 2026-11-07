package io.github.ukpratik.mukhar.data.di

import android.content.Context
import io.github.ukpratik.mukhar.data.analytics.AnalyticsSink
import io.github.ukpratik.mukhar.data.analytics.NoOpSink
import io.github.ukpratik.mukhar.data.audio.StatFsStorageInfo
import io.github.ukpratik.mukhar.data.audio.StorageInfo
import io.github.ukpratik.mukhar.data.billing.AccountServer
import io.github.ukpratik.mukhar.data.billing.BillingRepository
import io.github.ukpratik.mukhar.data.billing.StoreBillingRepository
import io.github.ukpratik.mukhar.data.billing.RemoteAccountServer
import io.github.ukpratik.mukhar.data.repo.EntitlementRepository
import io.github.ukpratik.mukhar.data.repo.Entitlements
import io.github.ukpratik.mukhar.data.work.AnalysisPipeline
import io.github.ukpratik.mukhar.data.work.PipelineRouter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Release builds: the analysis-mode router (on-device, own keys; Mukhar Cloud off in R1, ADR-008), real free storage, Google Play Billing and the Worker's account routes.
 * No debug hooks (build-plan "Debug hooks"). Analytics events are dropped until Firebase is added (ADR-007).
 */
@Module
@InstallIn(SingletonComponent::class)
object VariantModule {
    @Provides
    @Singleton
    fun pipeline(router: PipelineRouter): AnalysisPipeline = router

    @Provides
    @Singleton
    fun storage(@ApplicationContext context: Context): StorageInfo = StatFsStorageInfo(context)

    @Provides
    @Singleton
    fun billing(play: StoreBillingRepository): BillingRepository = play

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
