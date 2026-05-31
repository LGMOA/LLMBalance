package com.llmbalance.app.data

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ntfy_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_TOPIC = "topic"
        private const val KEY_ENABLED = "ntfy_enabled"
        private const val KEY_LANGUAGE = "language"

        const val DEFAULT_SERVER = "http://8.136.48.225:8088"
        const val DEFAULT_TOPIC = "78c1e32ed172e4180c274d2b1781a569"
        const val DEFAULT_LANGUAGE = "zh"
    }

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER) ?: DEFAULT_SERVER
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var topic: String
        get() = prefs.getString(KEY_TOPIC, DEFAULT_TOPIC) ?: DEFAULT_TOPIC
        set(value) = prefs.edit().putString(KEY_TOPIC, value).apply()

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    fun isConfigured(): Boolean = serverUrl.isNotBlank() && topic.isNotBlank()
}
