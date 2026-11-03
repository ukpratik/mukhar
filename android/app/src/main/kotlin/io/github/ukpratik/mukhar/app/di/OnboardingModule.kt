package io.github.ukpratik.mukhar.app.di

import io.github.ukpratik.mukhar.app.feature.onboarding.warmup.AudioRecordWarmupMeter
import io.github.ukpratik.mukhar.app.feature.onboarding.warmup.WarmupMeter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** M3: the warm-up level meter (on-device only). */
@Module
@InstallIn(SingletonComponent::class)
abstract class OnboardingModule {
    @Binds
    abstract fun warmupMeter(impl: AudioRecordWarmupMeter): WarmupMeter
}
