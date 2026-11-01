package com.vaak.data.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.vaak.data.BuildConfig
import com.vaak.data.audio.AacEncoder
import com.vaak.data.audio.AudioRecordSource
import com.vaak.data.audio.Recorder
import com.vaak.data.audio.RecordingFiles
import com.vaak.data.audio.StorageInfo
import com.vaak.data.content.PromptRepository
import com.vaak.data.db.HabitDao
import com.vaak.data.db.TalkDao
import com.vaak.data.db.VaakDatabase
import com.vaak.data.db.WordDao
import com.vaak.data.net.AppVersion
import com.vaak.data.net.CredentialsProvider
import com.vaak.data.net.ErrorMapper
import com.vaak.data.net.HeadersInterceptor
import com.vaak.data.net.NoCredentials
import com.vaak.data.net.VaakApi
import com.vaak.data.prefs.SettingsStore
import com.vaak.data.prefs.UserSettings
import com.vaak.data.repo.HabitRepository
import com.vaak.data.repo.TalkRepository
import com.vaak.data.util.ApplicationScope
import com.vaak.data.util.IdGenerator
import com.vaak.data.work.AnalysisPipeline
import com.vaak.data.work.AnalysisScheduler
import com.vaak.data.work.AndroidTalkNotifier
import com.vaak.data.work.TalkNotifier
import com.vaak.data.work.WorkManagerAnalysisScheduler
import com.vaak.domain.Clock
import com.vaak.domain.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
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

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

    @Provides
    @Singleton
    @ApplicationScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun settings(@ApplicationContext context: Context): UserSettings = SettingsStore(context)

    @Provides
    @Singleton
    fun credentials(): CredentialsProvider = NoCredentials

    @Provides
    @Singleton
    fun api(json: Json, credentials: CredentialsProvider, version: AppVersion): VaakApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS) // STT timeout is 25 s server-side (HLD §8.1)
            .writeTimeout(35, TimeUnit.SECONDS)
            .addInterceptor(HeadersInterceptor(version) { runBlocking { credentials.idToken() to credentials.sessionToken() } })
            .build()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(VaakApi::class.java)
    }

    @Provides
    @Singleton
    fun errorMapper(json: Json): ErrorMapper = ErrorMapper(json)

    @Provides
    @Singleton
    fun recorder(): Recorder = Recorder({ AudioRecordSource() }, { AacEncoder(it) }, Dispatchers.IO)

    @Provides
    @Singleton
    fun recordingFiles(@ApplicationContext context: Context): RecordingFiles = RecordingFiles(context.filesDir)

    @Provides
    @Singleton
    fun scheduler(@ApplicationContext context: Context): AnalysisScheduler =
        WorkManagerAnalysisScheduler { WorkManager.getInstance(context) }

    @Provides
    @Singleton
    fun notifier(@ApplicationContext context: Context): TalkNotifier = AndroidTalkNotifier(context)

    @Provides
    @Singleton
    fun prompts(@ApplicationContext context: Context, habitDao: HabitDao, clock: Clock, json: Json): PromptRepository =
        PromptRepository(context, habitDao, clock, json)

    @Provides
    @Singleton
    fun habits(habitDao: HabitDao): HabitRepository = HabitRepository(habitDao)

    @Provides
    @Singleton
    fun talks(
        talkDao: TalkDao,
        wordDao: WordDao,
        habits: HabitRepository,
        settings: UserSettings,
        pipeline: AnalysisPipeline,
        prompts: PromptRepository,
        scheduler: AnalysisScheduler,
        files: RecordingFiles,
        storage: StorageInfo,
        clock: Clock,
        json: Json,
        @ApplicationScope scope: CoroutineScope,
    ): TalkRepository = TalkRepository(
        talkDao, wordDao, habits, settings, pipeline, prompts, scheduler, files, storage, clock, IdGenerator.Random, json, scope,
    )
}
