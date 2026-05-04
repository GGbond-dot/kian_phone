package com.kian.khup.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class InterventionSettings(
    val douyinLimitMinutes: Int = 30,
    val xiaohongshuLimitMinutes: Int = 20,
)

@Singleton
class InterventionSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun observeSettings(): Flow<InterventionSettings> = callbackFlow {
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

    fun currentSettings(): InterventionSettings =
        InterventionSettings(
            douyinLimitMinutes = prefs.getInt(KEY_DOUYIN_LIMIT_MINUTES, 30),
            xiaohongshuLimitMinutes = prefs.getInt(KEY_XIAOHONGSHU_LIMIT_MINUTES, 20),
        )

    fun setDouyinLimitMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_DOUYIN_LIMIT_MINUTES, minutes.coerceIn(1, 240)).apply()
    }

    fun setXiaohongshuLimitMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_XIAOHONGSHU_LIMIT_MINUTES, minutes.coerceIn(1, 240)).apply()
    }

    companion object {
        private const val PREFS_NAME = "khup.intervention_settings"
        private const val KEY_DOUYIN_LIMIT_MINUTES = "douyin_limit_minutes"
        private const val KEY_XIAOHONGSHU_LIMIT_MINUTES = "xiaohongshu_limit_minutes"
    }
}
