package com.vaak.data.di

import android.content.Context
import com.vaak.data.audio.StatFsStorageInfo
import com.vaak.data.audio.StorageInfo
import com.vaak.data.repo.Entitlements
import com.vaak.data.repo.FreePlan
import com.vaak.data.work.AnalysisPipeline
import com.vaak.data.work.RemoteAnalysisPipeline
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Release builds: the real Worker pipeline and real free storage. No debug hooks (build-plan "Debug hooks"). */
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
    fun entitlements(): Entitlements = FreePlan
}
