package com.llmbalance.app.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llmbalance.app.data.BalanceApi
import com.llmbalance.app.data.KeyStore
import com.llmbalance.app.data.Providers
import com.llmbalance.app.data.QueryResult
import com.llmbalance.app.data.SavedKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class KeyWithBalance(
    val savedKey: SavedKey,
    val balanceResult: QueryResult? = null,
    val isQuerying: Boolean = false
)

class MainViewModel(private val keyStore: KeyStore) : ViewModel() {

    private val _keys = MutableLiveData<List<KeyWithBalance>>(emptyList())
    val keys: LiveData<List<KeyWithBalance>> = _keys

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var autoQueryJob: Job? = null

    companion object {
        private const val AUTO_QUERY_INTERVAL_MS = 5_000L
    }

    init {
        loadKeys()
        startAutoQuery()
    }

    private fun loadKeys() {
        _keys.value = keyStore.getAllKeys().map { KeyWithBalance(savedKey = it) }
    }

    fun addKey(providerId: String, apiKey: String, label: String) {
        val id = "${providerId}_${System.currentTimeMillis()}"
        val savedKey = SavedKey(
            id = id,
            providerId = providerId,
            apiKey = apiKey,
            label = label
        )
        keyStore.saveKey(savedKey)
        loadKeys()
    }

    fun deleteKey(id: String) {
        keyStore.deleteKey(id)
        loadKeys()
    }

    private fun queryBalance(keyWithBalance: KeyWithBalance) {
        val index = _keys.value?.indexOfFirst { it.savedKey.id == keyWithBalance.savedKey.id } ?: return
        val updatedList = _keys.value?.toMutableList() ?: return
        updatedList[index] = keyWithBalance.copy(isQuerying = true)
        _keys.value = updatedList

        viewModelScope.launch {
            val provider = Providers.getById(keyWithBalance.savedKey.providerId)
            val result = withContext(Dispatchers.IO) {
                if (provider != null) {
                    BalanceApi.queryBalance(provider, keyWithBalance.savedKey.apiKey)
                } else {
                    QueryResult(success = false, errorMessage = "Unknown provider")
                }
            }

            val currentList = _keys.value?.toMutableList() ?: return@launch
            val currentIndex = currentList.indexOfFirst { it.savedKey.id == keyWithBalance.savedKey.id }
            if (currentIndex >= 0) {
                currentList[currentIndex] = keyWithBalance.copy(
                    balanceResult = result,
                    isQuerying = false
                )
                _keys.value = currentList
            }
        }
    }

    fun queryAll() {
        _keys.value?.forEach { key ->
            if (!key.isQuerying) {
                queryBalance(key)
            }
        }
    }

    private fun startAutoQuery() {
        autoQueryJob?.cancel()
        autoQueryJob = viewModelScope.launch {
            while (isActive) {
                delay(AUTO_QUERY_INTERVAL_MS)
                queryAll()
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
