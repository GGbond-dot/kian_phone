package com.kian.khup.core.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class AiProviderMode {
    LocalFirst,
    LocalOnly,
    ApiOnly,
}

data class AiSettings(
    val providerMode: AiProviderMode = AiProviderMode.LocalFirst,
    val apiBaseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val apiModel: String = "",
) {
    val hasApiConfig: Boolean =
        apiBaseUrl.isNotBlank() && apiKey.isNotBlank() && apiModel.isNotBlank()
}

@Singleton
class AiSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val securePrefs = context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)

    fun observeSettings(): Flow<AiSettings> = callbackFlow {
        fun emitCurrent() {
            trySend(currentSettings())
        }

        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            emitCurrent()
        }
        emitCurrent()
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun currentSettings(): AiSettings =
        AiSettings(
            providerMode = runCatching {
                AiProviderMode.valueOf(prefs.getString(KEY_PROVIDER_MODE, null) ?: AiProviderMode.LocalFirst.name)
            }.getOrDefault(AiProviderMode.LocalFirst),
            apiBaseUrl = prefs.getString(KEY_API_BASE_URL, null) ?: "https://api.openai.com/v1",
            apiKey = readApiKey(),
            apiModel = prefs.getString(KEY_API_MODEL, null).orEmpty(),
        )

    fun setProviderMode(mode: AiProviderMode) {
        prefs.edit().putString(KEY_PROVIDER_MODE, mode.name).apply()
    }

    fun setApiBaseUrl(baseUrl: String) {
        prefs.edit().putString(KEY_API_BASE_URL, baseUrl.trim().trimEnd('/')).apply()
    }

    fun setApiKey(apiKey: String) {
        writeApiKey(apiKey.trim())
    }

    fun setApiModel(model: String) {
        prefs.edit().putString(KEY_API_MODEL, model.trim()).apply()
    }

    private fun readApiKey(): String {
        migrateLegacyApiKeyIfNeeded()
        val encrypted = securePrefs.getString(KEY_API_KEY_ENCRYPTED, null) ?: return ""
        return runCatching { decrypt(encrypted) }.getOrDefault("")
    }

    private fun writeApiKey(apiKey: String) {
        if (apiKey.isBlank()) {
            securePrefs.edit().remove(KEY_API_KEY_ENCRYPTED).apply()
            prefs.edit()
                .remove(KEY_API_KEY)
                .putBoolean(KEY_API_KEY_PRESENT, false)
                .apply()
        } else {
            securePrefs.edit().putString(KEY_API_KEY_ENCRYPTED, encrypt(apiKey)).apply()
            prefs.edit()
                .remove(KEY_API_KEY)
                .putBoolean(KEY_API_KEY_PRESENT, true)
                .apply()
        }
    }

    private fun migrateLegacyApiKeyIfNeeded() {
        if (securePrefs.contains(KEY_API_KEY_ENCRYPTED)) {
            prefs.edit()
                .remove(KEY_API_KEY)
                .putBoolean(KEY_API_KEY_PRESENT, true)
                .apply()
            return
        }
        val legacy = prefs.getString(KEY_API_KEY, null)?.trim().orEmpty()
        if (legacy.isNotBlank()) {
            securePrefs.edit().putString(KEY_API_KEY_ENCRYPTED, encrypt(legacy)).apply()
            prefs.edit()
                .remove(KEY_API_KEY)
                .putBoolean(KEY_API_KEY_PRESENT, true)
                .apply()
        }
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + cipherText
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(payloadBase64: String): String {
        val payload = Base64.decode(payloadBase64, Base64.NO_WRAP)
        require(payload.size > GCM_IV_BYTES) { "Invalid API key payload" }
        val iv = payload.copyOfRange(0, GCM_IV_BYTES)
        val cipherText = payload.copyOfRange(GCM_IV_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherText).toString(Charsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "khup.ai_settings"
        const val SECURE_PREFS_NAME = "khup.ai_secure_settings"
        const val KEY_PROVIDER_MODE = "provider_mode"
        const val KEY_API_BASE_URL = "api_base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_API_KEY_PRESENT = "api_key_present"
        const val KEY_API_KEY_ENCRYPTED = "api_key_encrypted"
        const val KEY_API_MODEL = "api_model"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEYSTORE_ALIAS = "khup_ai_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
