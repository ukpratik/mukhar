package io.github.ukpratik.mukhar.data.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import io.github.ukpratik.mukhar.data.BuildConfig
import io.github.ukpratik.mukhar.data.audio.AacEncoder
import io.github.ukpratik.mukhar.data.audio.AudioRecordSource
import io.github.ukpratik.mukhar.data.audio.Recorder
import io.github.ukpratik.mukhar.data.audio.RecordingFiles
import io.github.ukpratik.mukhar.data.audio.StorageInfo
import io.github.ukpratik.mukhar.data.content.PromptRepository
import io.github.ukpratik.mukhar.data.content.WordCatalog
import io.github.ukpratik.mukhar.data.analytics.Analytics
import io.github.ukpratik.mukhar.data.analytics.AnalyticsSink
import io.github.ukpratik.mukhar.data.analytics.ConsentAnalytics
import io.github.ukpratik.mukhar.data.billing.AccountServer
import io.github.ukpratik.mukhar.data.billing.BillingRepository
import io.github.ukpratik.mukhar.data.db.ProgressDao
import io.github.ukpratik.mukhar.data.repo.DataWiper
import io.github.ukpratik.mukhar.data.repo.EntitlementRepository
import io.github.ukpratik.mukhar.data.work.ReminderScheduler
import java.io.File
import io.github.ukpratik.mukhar.data.repo.Entitlements
import io.github.ukpratik.mukhar.data.work.AccountSync
import io.github.ukpratik.mukhar.data.work.AlarmTrialReminderScheduler
import io.github.ukpratik.mukhar.data.work.TrialReminderScheduler
import io.github.ukpratik.mukhar.data.work.WorkManagerAccountSync
import io.github.ukpratik.mukhar.data.repo.SentenceChecker
import io.github.ukpratik.mukhar.data.repo.WordRepository
import io.github.ukpratik.mukhar.data.tts.AndroidSpeaker
import io.github.ukpratik.mukhar.data.tts.Speaker
import io.github.ukpratik.mukhar.data.db.HabitDao
import io.github.ukpratik.mukhar.data.db.TalkDao
import io.github.ukpratik.mukhar.data.db.MukharDatabase
import io.github.ukpratik.mukhar.data.db.WordDao
import io.github.ukpratik.mukhar.data.net.AppVersion
import io.github.ukpratik.mukhar.data.net.CredentialsProvider
import io.github.ukpratik.mukhar.data.net.ErrorMapper
import io.github.ukpratik.mukhar.data.net.HeadersInterceptor
import io.github.ukpratik.mukhar.data.net.NoCredentials
import io.github.ukpratik.mukhar.data.net.MukharApi
import io.github.ukpratik.mukhar.data.prefs.SettingsStore
import io.github.ukpratik.mukhar.data.prefs.UserSettings
import io.github.ukpratik.mukhar.data.repo.HabitRepository
import io.github.ukpratik.mukhar.data.repo.TalkRepository
import io.github.ukpratik.mukhar.data.time.OverridableClock
import io.github.ukpratik.mukhar.data.util.ApplicationScope
import io.github.ukpratik.mukhar.data.util.IdGenerator
import io.github.ukpratik.mukhar.data.work.AnalysisPipeline
import io.github.ukpratik.mukhar.data.work.AnalysisScheduler
import io.github.ukpratik.mukhar.data.work.AndroidTalkNotifier
import io.github.ukpratik.mukhar.data.work.TalkNotifier
import io.github.ukpratik.mukhar.data.work.WorkManagerAnalysisScheduler
import io.github.ukpratik.mukhar.domain.Clock
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import io.github.ukpratik.mukhar.data.byok.AnthropicClient
import io.github.ukpratik.mukhar.data.ideas.FileIdeasCache
import io.github.ukpratik.mukhar.data.ideas.SpeakingIdeasRepository
import io.github.ukpratik.mukhar.data.byok.ByokFile
import io.github.ukpratik.mukhar.data.byok.ByokFileSerializer
import io.github.ukpratik.mukhar.data.byok.ByokHttp
import io.github.ukpratik.mukhar.data.byok.ByokHttpClient
import io.github.ukpratik.mukhar.data.byok.ByokKeyRepository
import io.github.ukpratik.mukhar.data.byok.ByokUsageLog
import io.github.ukpratik.mukhar.data.byok.DeepgramClient
import io.github.ukpratik.mukhar.data.byok.KeyVault
import io.github.ukpratik.mukhar.data.byok.SarvamClient
import io.github.ukpratik.mukhar.data.byok.TinkKeystoreCipher
import io.github.ukpratik.mukhar.data.local.LocalAnalysisPipeline
import io.github.ukpratik.mukhar.data.local.LocalTranscriber
import io.github.ukpratik.mukhar.data.local.ModelManager
import io.github.ukpratik.mukhar.data.local.NoOpModelManager
import io.github.ukpratik.mukhar.data.local.SynonymRepository
import io.github.ukpratik.mukhar.data.local.SynonymSource
import io.github.ukpratik.mukhar.data.local.UnavailableLocalTranscriber
import io.github.ukpratik.mukhar.data.audio.MediaCodecPcmDecoder
import io.github.ukpratik.mukhar.data.repo.RecordingsRepository
import io.github.ukpratik.mukhar.data.util.MukharLog
import io.github.ukpratik.mukhar.data.work.ByokAnalysisPipeline
import io.github.ukpratik.mukhar.data.work.PipelineRouter
import io.github.ukpratik.mukhar.data.work.RemoteAnalysisPipeline
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): MukharDatabase =
        Room.databaseBuilder(context, MukharDatabase::class.java, MukharDatabase.NAME).addMigrations(*MukharDatabase.MIGRATIONS).build()

    @Provides
    fun talkDao(db: MukharDatabase): TalkDao = db.talkDao()

    @Provides
    fun wordDao(db: MukharDatabase): WordDao = db.wordDao()

    @Provides
    fun habitDao(db: MukharDatabase): HabitDao = db.habitDao()

    @Provides
    fun progressDao(db: MukharDatabase): ProgressDao = db.progressDao()

    /** The system clock, shiftable from the debug clock screen in debug builds only (build plan "Debug hooks"). */
    @Provides
    fun clock(clock: OverridableClock): Clock = clock

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

    /** One `settings` DataStore (HabitModule provides it); the talk flow sees it through [UserSettings]. */
    @Provides
    fun settings(store: SettingsStore): UserSettings = store

    @Provides
    @Singleton
    fun credentials(): CredentialsProvider = NoCredentials

    @Provides
    @Singleton
    fun api(json: Json, credentials: CredentialsProvider, version: AppVersion): MukharApi {
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
            .create(MukharApi::class.java)
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
    fun prompts(@ApplicationContext context: Context, habitDao: HabitDao, clock: Clock, json: Json, settings: SettingsStore): PromptRepository =
        PromptRepository(context, habitDao, clock, json, done = settings::donePromptIds)

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
        words: WordRepository,
        recordings: RecordingsRepository,
    ): TalkRepository = TalkRepository(
        talkDao, wordDao, habits, settings, pipeline, prompts, scheduler, files, storage, clock, IdGenerator.Random, json, scope, words,
        MediaCodecPcmDecoder(), recordings,
    )

    @Provides
    @Singleton
    fun recordings(talkDao: TalkDao, files: RecordingFiles, settings: SettingsStore, clock: Clock): RecordingsRepository =
        RecordingsRepository(talkDao, files, settings, clock)

    @Provides
    @Singleton
    fun wordCatalog(@ApplicationContext context: Context, json: Json): WordCatalog = WordCatalog(context, json)

    @Provides
    @Singleton
    fun words(wordDao: WordDao, catalog: WordCatalog, settings: SettingsStore, habits: HabitRepository, clock: Clock): WordRepository =
        WordRepository(wordDao, catalog, settings, habits, clock)

    @Provides
    @Singleton
    fun sentenceChecker(pipeline: AnalysisPipeline, settings: SettingsStore, entitlements: Entitlements, clock: Clock): SentenceChecker =
        SentenceChecker(pipeline, settings, entitlements, clock, IdGenerator.Random)

    @Provides
    @Singleton
    fun speaker(impl: AndroidSpeaker): Speaker = impl

    // ---- M5: analytics, billing, account ----

    /** Records only while "Share usage and crash reports" is on (D-026); see [ConsentAnalytics]. */
    @Provides
    @Singleton
    fun analytics(settings: SettingsStore, sink: AnalyticsSink, @ApplicationScope scope: CoroutineScope): Analytics =
        ConsentAnalytics(settings.analyticsConsent.stateIn(scope, SharingStarted.Eagerly, false), sink)

    @Provides
    @Singleton
    fun accountSync(@ApplicationContext context: Context): AccountSync = WorkManagerAccountSync { WorkManager.getInstance(context) }

    @Provides
    @Singleton
    fun trialReminders(impl: AlarmTrialReminderScheduler): TrialReminderScheduler = impl

    @Provides
    @Singleton
    fun entitlementRepository(
        billing: BillingRepository,
        server: AccountServer,
        settings: SettingsStore,
        clock: Clock,
        reminders: TrialReminderScheduler,
        sync: AccountSync,
        analytics: Analytics,
        @ApplicationScope scope: CoroutineScope,
    ): EntitlementRepository = EntitlementRepository(billing, server, settings, clock, reminders, sync, analytics, scope)

    @Provides
    @Singleton
    fun dataWiper(
        db: MukharDatabase,
        settings: SettingsStore,
        files: RecordingFiles,
        @ApplicationContext context: Context,
        sync: AccountSync,
        trialReminders: TrialReminderScheduler,
        analytics: Analytics,
        reminders: ReminderScheduler,
        vault: KeyVault,
    ): DataWiper = DataWiper(db, settings, listOf(files.dir, File(context.cacheDir, SHARE_DIR), File(context.filesDir, FileIdeasCache.DIR)), sync, trialReminders, analytics) {
        reminders.reschedule()
        vault.wipe() // own API keys, their Tink keyset and the Keystore master key (ADR-008)
    }

    // ---- 2A (ADR-008): analysis modes, on-device pipeline, own keys ----

    /** `files/datastore/byok.pb`: encrypted own keys, excluded from Auto Backup and device transfer. */
    @Provides
    @Singleton
    fun byokStore(@ApplicationContext context: Context): DataStore<ByokFile> =
        DataStoreFactory.create(ByokFileSerializer) { File(context.filesDir, ByokFileSerializer.FILE) }

    @Provides
    @Singleton
    fun keyVault(store: DataStore<ByokFile>, @ApplicationContext context: Context): KeyVault = KeyVault(store, TinkKeystoreCipher(context))

    @Provides
    @Singleton
    @ByokHttpClient
    fun byokHttp(): OkHttpClient = ByokHttp.client(MukharLog::i)

    @Provides
    @Singleton
    fun deepgram(@ByokHttpClient http: OkHttpClient, json: Json): DeepgramClient = DeepgramClient(http, json)

    @Provides
    @Singleton
    fun sarvam(@ByokHttpClient http: OkHttpClient, json: Json): SarvamClient = SarvamClient(http, json, decoder = MediaCodecPcmDecoder())

    @Provides
    @Singleton
    fun anthropic(@ByokHttpClient http: OkHttpClient, json: Json): AnthropicClient = AnthropicClient(http, json)

    @Provides
    @Singleton
    fun byokKeys(vault: KeyVault, deepgram: DeepgramClient, sarvam: SarvamClient, anthropic: AnthropicClient): ByokKeyRepository =
        ByokKeyRepository(vault, deepgram, sarvam, anthropic)

    @Provides
    @Singleton
    fun synonyms(@ApplicationContext context: Context, json: Json): SynonymSource = SynonymRepository(context, json)

    /** 2A: no offline engine yet ("model not installed"). Stage 2B binds the Vosk transcriber here. */
    @Provides
    @Singleton
    fun localTranscriber(): LocalTranscriber = UnavailableLocalTranscriber

    /** 2A: models listed, not downloadable yet. Stage 2B binds the Vosk model download manager here. */
    @Provides
    @Singleton
    fun modelManager(): ModelManager = NoOpModelManager()

    @Provides
    @Singleton
    fun usageLog(settings: SettingsStore, clock: Clock): ByokUsageLog = ByokUsageLog(settings, clock)

    @Provides
    @Singleton
    fun localPipeline(transcriber: LocalTranscriber, synonyms: SynonymSource): LocalAnalysisPipeline =
        LocalAnalysisPipeline(transcriber, synonyms, IdGenerator.Random)

    @Provides
    @Singleton
    fun byokPipeline(
        vault: KeyVault,
        deepgram: DeepgramClient,
        sarvam: SarvamClient,
        anthropic: AnthropicClient,
        local: LocalAnalysisPipeline,
        transcriber: LocalTranscriber,
        usage: ByokUsageLog,
    ): ByokAnalysisPipeline = ByokAnalysisPipeline(vault, deepgram, sarvam, anthropic, local, transcriber, usage, IdGenerator.Random)

    /** US-078: AI speaking ideas on the user's own Anthropic key, cached in `files/ideas`. */
    @Provides
    @Singleton
    fun speakingIdeas(vault: KeyVault, anthropic: AnthropicClient, @ApplicationContext context: Context, json: Json, usage: ByokUsageLog): SpeakingIdeasRepository =
        SpeakingIdeasRepository(vault, anthropic, FileIdeasCache(File(context.filesDir, FileIdeasCache.DIR), json), usage)

    /** THE analysis pipeline in release (and in debug unless the debug menu forces the fake): picks per call by mode. */
    @Provides
    @Singleton
    fun router(settings: SettingsStore, local: LocalAnalysisPipeline, byok: ByokAnalysisPipeline, remote: RemoteAnalysisPipeline): PipelineRouter =
        PipelineRouter({ settings.analysisMode.first() }, local, byok, remote)

    /** `cache/share/`: share-card images (S21), served by the app's FileProvider and wiped with the data. */
    const val SHARE_DIR = "share"
}
