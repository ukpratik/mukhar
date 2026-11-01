package com.vaak.app.di

import com.vaak.app.BuildConfig
import com.vaak.data.net.AppVersion
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    fun appVersion(): AppVersion = AppVersion { BuildConfig.VERSION_CODE }
}
