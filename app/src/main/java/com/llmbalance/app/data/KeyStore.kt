package com.llmbalance.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class SavedKey(
    val id: String,
    val providerId: String,
    val apiKey: String,
    val label: String
)

class KeyStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("llm_balance_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEYS_KEY = "saved_keys"
    }

    fun getAllKeys(): List<SavedKey> {
        val json = prefs.getString(KEYS_KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                SavedKey(
                    id = obj.getString("id"),
                    providerId = obj.getString("providerId"),
                    apiKey = obj.getString("apiKey"),
                    label = obj.optString("label", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveKey(key: SavedKey) {
        val keys = getAllKeys().toMutableList()
        val existing = keys.indexOfFirst { it.id == key.id }
        if (existing >= 0) {
            keys[existing] = key
        } else {
            keys.add(key)
        }
        writeKeys(keys)
    }

    fun deleteKey(id: String) {
        val keys = getAllKeys().filter { it.id != id }
        writeKeys(keys)
    }

    private fun writeKeys(keys: List<SavedKey>) {
        val arr = JSONArray()
        keys.forEach { key ->
            arr.put(JSONObject().apply {
                put("id", key.id)
                put("providerId", key.providerId)
                put("apiKey", key.apiKey)
                put("label", key.label)
            })
        }
        prefs.edit().putString(KEYS_KEY, arr.toString()).apply()
    }
}
