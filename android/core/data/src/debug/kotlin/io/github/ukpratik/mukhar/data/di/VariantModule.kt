package io.github.ukpratik.mukhar.data.di

import android.content.Context
import io.github.ukpratik.mukhar.data.analytics.AnalyticsSink
import io.github.ukpratik.mukhar.data.audio.StatFsStorageInfo
import io.github.ukpratik.mukhar.data.audio.StorageInfo
import io.github.ukpratik.mukhar.data.billing.AccountServer
import io.github.ukpratik.mukhar.data.billing.BillingRepository
import io.github.ukpratik.mukhar.data.billing.StoreBillingRepository
import io.github.ukpratik.mukhar.data.billing.RemoteAccountServer
import io.github.ukpratik.mukhar.data.debug.DebugAnalysisPipeline
import io.github.ukpratik.mukhar.data.debug.DebugAnalyticsSink
import io.github.ukpratik.mukhar.data.debug.DebugControls
import io.github.ukpratik.mukhar.data.debug.DebugEntitlements
import io.github.ukpratik.mukhar.data.debug.DebugStorageInfo
import io.github.ukpratik.mukhar.data.debug.FakeAccountServer
import io.github.ukpratik.mukhar.data.debug.FakeAnalysisPipeline
import io.github.ukpratik.mukhar.data.debug.FakeBillingRepository
import io.github.ukpratik.mukhar.data.debug.FakePlayStore
import io.github.ukpratik.mukhar.data.debug.SharedPrefsFakeStore
import io.github.ukpratik.mukhar.data.repo.EntitlementRepository
import io.github.ukpratik.mukhar.data.repo.Entitlements
import io.github.ukpratik.mukhar.data.work.AnalysisPipeline
import io.github.ukpratik.mukhar.data.work.PipelineRouter
import io.github.ukpratik.mukhar.domain.Clock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Debug builds: the same analysis-mode router as release (the debug menu can force the canned fake pipeline), plus the low-storage and plan switches, fake Play billing with the
 * trial-expiry override, and an in-memory analytics log (build-plan "Debug hooks"). The billing switch applies
 * after a restart.
 */
@Module
@InstallIn(SingletonComponent::class)
object VariantModule {
    @Provides
    @Singleton
    fun controls(@ApplicationContext context: Context): DebugControls = DebugControls(context)

    @Provides
    @Singleton
    fun pipeline(controls: DebugControls, router: PipelineRouter): AnalysisPipeline =
        DebugAnalysisPipeline(controls, FakeAnalysisPipeline(controls), router)

    @Provides
    @Singleton
    fun storage(@ApplicationContext context: Context, controls: DebugControls): StorageInfo =
        DebugStorageInfo(StatFsStorageInfo(context), controls)

    @Provides
    @Singleton
    fun fakePlay(@ApplicationContext context: Context, clock: Clock): FakePlayStore = FakePlayStore(clock, SharedPrefsFakeStore(context))

    @Provides
    @Singleton
    fun billing(controls: DebugControls, fake: FakePlayStore, play: Provider<StoreBillingRepository>): BillingRepository =
        if (controls.state.value.fakeBilling) FakeBillingRepository(fake) else play.get()

    @Provides
    @Singleton
    fun accountServer(controls: DebugControls, fake: FakePlayStore, remote: Provider<RemoteAccountServer>): AccountServer =
        if (controls.state.value.fakeBilling) FakeAccountServer(fake) else remote.get()

    @Provides
    @Singleton
    fun entitlements(controls: DebugControls, repo: EntitlementRepository): Entitlements = DebugEntitlements(controls, repo)

    @Provides
    @Singleton
    fun analyticsSink(): AnalyticsSink = DebugAnalyticsSink
}
