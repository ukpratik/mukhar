package com.vaak.data.di

import android.content.Context
import com.vaak.data.analytics.AnalyticsSink
import com.vaak.data.audio.StatFsStorageInfo
import com.vaak.data.audio.StorageInfo
import com.vaak.data.billing.AccountServer
import com.vaak.data.billing.BillingRepository
import com.vaak.data.billing.PlayBillingRepository
import com.vaak.data.billing.RemoteAccountServer
import com.vaak.data.debug.DebugAnalysisPipeline
import com.vaak.data.debug.DebugAnalyticsSink
import com.vaak.data.debug.DebugControls
import com.vaak.data.debug.DebugEntitlements
import com.vaak.data.debug.DebugStorageInfo
import com.vaak.data.debug.FakeAccountServer
import com.vaak.data.debug.FakeAnalysisPipeline
import com.vaak.data.debug.FakeBillingRepository
import com.vaak.data.debug.FakePlayStore
import com.vaak.data.debug.SharedPrefsFakeStore
import com.vaak.data.repo.EntitlementRepository
import com.vaak.data.repo.Entitlements
import com.vaak.data.work.AnalysisPipeline
import com.vaak.data.work.RemoteAnalysisPipeline
import com.vaak.domain.Clock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Debug builds: the fake pipeline by default, plus the low-storage and plan switches, fake Play billing with the
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
    fun pipeline(controls: DebugControls, remote: RemoteAnalysisPipeline): AnalysisPipeline =
        DebugAnalysisPipeline(controls, FakeAnalysisPipeline(controls), remote)

    @Provides
    @Singleton
    fun storage(@ApplicationContext context: Context, controls: DebugControls): StorageInfo =
        DebugStorageInfo(StatFsStorageInfo(context), controls)

    @Provides
    @Singleton
    fun fakePlay(@ApplicationContext context: Context, clock: Clock): FakePlayStore = FakePlayStore(clock, SharedPrefsFakeStore(context))

    @Provides
    @Singleton
    fun billing(controls: DebugControls, fake: FakePlayStore, play: Provider<PlayBillingRepository>): BillingRepository =
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
