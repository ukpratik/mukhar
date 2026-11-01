package com.vaak.data.di

import android.content.Context
import androidx.room.Room
import com.vaak.data.db.HabitDao
import com.vaak.data.db.TalkDao
import com.vaak.data.db.VaakDatabase
import com.vaak.data.db.WordDao
import com.vaak.domain.Clock
import com.vaak.domain.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): VaakDatabase =
        Room.databaseBuilder(context, VaakDatabase::class.java, VaakDatabase.NAME).build()

    @Provides
    fun talkDao(db: VaakDatabase): TalkDao = db.talkDao()

    @Provides
    fun wordDao(db: VaakDatabase): WordDao = db.wordDao()

    @Provides
    fun habitDao(db: VaakDatabase): HabitDao = db.habitDao()

    @Provides
    @Singleton
    fun clock(): Clock = SystemClock
}
