package io.github.ukpratik.mukhar.data.byok

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import io.github.ukpratik.mukhar.data.util.MukharLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64

/** Authenticated encryption for the stored keys; the provider id is the associated data. */
interface KeyCipher {
    fun encrypt(plain: ByteArray, associatedData: ByteArray): ByteArray
    fun decrypt(cipher: ByteArray, associatedData: ByteArray): ByteArray

    /** Deletes the keyset and its Keystore master key ("Delete my data"). */
    fun destroy() = Unit
}

/** A Tink [Aead] as a [KeyCipher] (tests use an in-memory keyset). */
open class AeadKeyCipher(private val aead: () -> Aead) : KeyCipher {
    override fun encrypt(plain: ByteArray, associatedData: ByteArray): ByteArray = aead().encrypt(plain, associatedData)
    override fun decrypt(cipher: ByteArray, associatedData: ByteArray): ByteArray = aead().decrypt(cipher, associatedData)
}

/**
 * Tink AEAD `AES256_GCM` whose keyset lives in its own SharedPreferences file, wrapped by the Android Keystore master
 * key `android-keystore://mukhar_byok_master` (ADR-008 "Key storage"). Built lazily, off the main thread, on first use.
 * Neither the keyset prefs nor `byok.pb` are in Auto Backup or device transfer (and a restored blob couldn't be
 * decrypted anyway: Keystore keys never leave the phone).
 */
class TinkKeystoreCipher(private val context: Context) : AeadKeyCipher({ build(context) }) {
    override fun destroy() {
        context.applicationContext.deleteSharedPreferences(KEYSET_PREFS)
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(MASTER_KEY_ALIAS) }
        cached = null
    }

    companion object {
        const val KEYSET_PREFS = "mukhar_byok_keyset"
        const val KEYSET_NAME = "byok_keyset"
        const val MASTER_KEY_ALIAS = "mukhar_byok_master"
        const val MASTER_KEY_URI = "android-keystore://$MASTER_KEY_ALIAS"

        @Volatile private var cached: Aead? = null

        @Synchronized
        private fun build(context: Context): Aead = cached ?: run {
            AeadConfig.register()
            val handle = AndroidKeysetManager.Builder()
                .withSharedPref(context.applicationContext, KEYSET_NAME, KEYSET_PREFS)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
            handle.getPrimitive(RegistryConfiguration.get(), Aead::class.java).also { cached = it }
        }
    }
}

/** What the "Your keys" screen may show about a stored key: never the key itself. */
data class KeyInfo(val last4: String, val savedAtMs: Long, val checkedAtMs: Long?, val check: KeyCheck?)

@Serializable
data class StoredKey(
    @SerialName("ct") val cipherText: String,
    @SerialName("last4") val last4: String,
    @SerialName("saved_at") val savedAtMs: Long,
    @SerialName("checked_at") val checkedAtMs: Long? = null,
    @SerialName("check") val check: String? = null,
)

@Serializable
data class ByokFile(val keys: Map<String, StoredKey> = emptyMap())

/** `files/datastore/byok.pb`: JSON of [ByokFile] (ciphertexts are base64). */
object ByokFileSerializer : Serializer<ByokFile> {
    private val json = Json { ignoreUnknownKeys = true }
    override val defaultValue: ByokFile = ByokFile()

    override suspend fun readFrom(input: InputStream): ByokFile = try {
        val text = input.readBytes().decodeToString()
        if (text.isBlank()) defaultValue else json.decodeFromString(ByokFile.serializer(), text)
    } catch (e: SerializationException) {
        throw CorruptionException("byok.pb unreadable", e)
    }

    override suspend fun writeTo(t: ByokFile, output: OutputStream) {
        output.write(json.encodeToString(ByokFile.serializer(), t).encodeToByteArray())
    }

    const val FILE = "datastore/byok.pb"
}

/** Read access to the user's keys for the pipelines (a seam for tests). */
interface KeySource {
    suspend fun key(provider: ByokProvider): String?
}

/**
 * The user's own API keys, encrypted on this phone only (ADR-008). Decrypted keys are returned for one call and never
 * cached or logged. A ciphertext that no longer decrypts (Keystore reset, restored from elsewhere) is dropped.
 */
class KeyVault(
    private val store: DataStore<ByokFile>,
    private val cipher: KeyCipher,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : KeySource {

    val keys: Flow<Map<ByokProvider, KeyInfo>> = store.data.map { file ->
        file.keys.mapNotNull { (id, k) ->
            ByokProvider.fromId(id)?.let { it to KeyInfo(k.last4, k.savedAtMs, k.checkedAtMs, KeyCheck.fromKey(k.check)) }
        }.toMap()
    }

    override suspend fun key(provider: ByokProvider): String? {
        val stored = store.data.first().keys[provider.id] ?: return null
        return withContext(Dispatchers.IO) {
            try {
                cipher.decrypt(Base64.getDecoder().decode(stored.cipherText), provider.id.encodeToByteArray()).decodeToString()
            } catch (e: GeneralSecurityException) {
                MukharLog.w("byok: stored ${provider.id} key can't be decrypted; removed")
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }.also { if (it == null) remove(provider) }
    }

    /** Saves (or replaces) a key. Surrounding whitespace is dropped; a blank key is refused. */
    suspend fun save(provider: ByokProvider, key: String) {
        val clean = key.trim()
        require(clean.isNotEmpty()) { "empty key" }
        val ct = withContext(Dispatchers.IO) { cipher.encrypt(clean.encodeToByteArray(), provider.id.encodeToByteArray()) }
        val entry = StoredKey(Base64.getEncoder().encodeToString(ct), clean.takeLast(LAST_CHARS), nowMs())
        store.updateData { it.copy(keys = it.keys + (provider.id to entry)) }
    }

    suspend fun recordCheck(provider: ByokProvider, result: KeyCheck) {
        store.updateData { f ->
            val k = f.keys[provider.id] ?: return@updateData f
            f.copy(keys = f.keys + (provider.id to k.copy(checkedAtMs = nowMs(), check = result.key)))
        }
    }

    suspend fun remove(provider: ByokProvider) {
        store.updateData { it.copy(keys = it.keys - provider.id) }
    }

    /** "Delete all my data": every key, then the keyset and its master key. */
    suspend fun wipe() {
        store.updateData { ByokFile() }
        withContext(Dispatchers.IO) { runCatching { cipher.destroy() } }
    }

    companion object {
        const val LAST_CHARS = 4
    }
}
