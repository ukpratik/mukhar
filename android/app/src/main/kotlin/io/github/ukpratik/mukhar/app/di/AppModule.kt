package io.github.ukpratik.mukhar.app.di

import io.github.ukpratik.mukhar.app.BuildConfig
import io.github.ukpratik.mukhar.data.net.AppVersion
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
