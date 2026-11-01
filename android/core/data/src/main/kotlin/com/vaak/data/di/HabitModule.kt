package com.vaak.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.vaak.data.baseline.BaselineRecorder
import com.vaak.data.baseline.TalkBaselineRecorder
import com.vaak.data.today.RoomTalkStatusSource
import com.vaak.data.today.TalkStatusSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Settings DataStore, the S05 baseline on the talk pipeline, and Today's view of the talk queue. */
@Module
@InstallIn(SingletonComponent::class)
abstract class HabitModule {
    @Binds
    abstract fun baselineRecorder(impl: TalkBaselineRecorder): BaselineRecorder

    @Binds
    abstract fun talkStatus(impl: RoomTalkStatusSource): TalkStatusSource

    companion object {
        /** `files/datastore/settings.preferences_pb`, the path the backup rules include (LLD §2.1). */
        @Provides
        @Singleton
        fun settingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("settings") })
    }
}
