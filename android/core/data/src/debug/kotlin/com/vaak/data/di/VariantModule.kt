package com.vaak.data.di

import android.content.Context
import com.vaak.data.audio.StatFsStorageInfo
import com.vaak.data.audio.StorageInfo
import com.vaak.data.debug.DebugAnalysisPipeline
import com.vaak.data.debug.DebugControls
import com.vaak.data.debug.DebugEntitlements
import com.vaak.data.debug.DebugStorageInfo
import com.vaak.data.debug.FakeAnalysisPipeline
import com.vaak.data.repo.Entitlements
import com.vaak.data.work.AnalysisPipeline
import com.vaak.data.work.RemoteAnalysisPipeline
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Debug builds: the fake pipeline by default, plus the low-storage and plan switches (build-plan "Debug hooks"). */
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
    fun entitlements(controls: DebugControls): Entitlements = DebugEntitlements(controls)
}
