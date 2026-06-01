package com.llmbalance.app.data

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

data class ModelUsage(
    val modelName: String,
    val callCount: Long = 0,
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val totalTokens: Long = 0
)

data class UsageInfo(
    val totalPromptTokens: Long = 0,
    val totalCompletionTokens: Long = 0,
    val totalTokens: Long = 0,
    val totalCalls: Long = 0,
    val modelUsage: List<ModelUsage> = emptyList(),
    val availableModels: List<String> = emptyList()
)

data class BalanceResult(
    val isAvailable: Boolean,
    val totalBalance: String,
    val toppedUpBalance: String,
    val grantedBalance: String,
    val currency: String,
    val rawResponse: String
)

data class QueryResult(
    val success: Boolean,
    val balanceResult: BalanceResult? = null,
    val usageInfo: UsageInfo? = null,
    val errorMessage: String? = null
)

object BalanceApi {

    fun queryBalance(provider: ProviderConfig, apiKey: String): QueryResult {
        return when (provider.id) {
            "deepseek" -> queryDeepSeek(provider, apiKey)
            "openai" -> queryOpenAI(provider, apiKey)
            "zhipu" -> queryZhipuAI(provider, apiKey)
            "moonshot" -> queryMoonshot(provider, apiKey)
            "anthropic" -> queryAnthropic(provider, apiKey)
            "groq" -> queryGroq(provider, apiKey)
            "minimax" -> queryMiniMax(provider, apiKey)
            "dashscope" -> queryDashScope(provider, apiKey)
            else -> QueryResult(success = false, errorMessage = "Unknown provider: ${provider.id}")
        }
    }

    fun queryUsage(provider: ProviderConfig, apiKey: String): QueryResult {
        return when {
            provider.supportsUsage -> {
                when (provider.id) {
                    "openai" -> queryOpenAIUsage(provider, apiKey)
                    else -> queryModelsOnly(provider, apiKey)
                }
            }
            provider.supportsModelList -> queryModelsOnly(provider, apiKey)
            else -> QueryResult(success = true, usageInfo = UsageInfo())
        }
    }

    private fun todayDateStr(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    private fun extractModelNames(body: String): List<String> {
        return try {
            val json = JSONObject(body)
            val data = json.optJSONArray("data")
            if (data != null) {
                (0 until data.length()).mapNotNull { i ->
                    val obj = data.optJSONObject(i)
                    if (obj != null) obj.optString("id", "") else null
                }
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ===== OpenAI Usage =====
    private fun queryOpenAIUsage(provider: ProviderConfig, apiKey: String): QueryResult {
        val usageEndpoint = provider.usageEndpoint ?: return QueryResult(
            success = true, usageInfo = UsageInfo()
        )
        val (code, body) = httpGet("${provider.baseUrl}$usageEndpoint?date=${todayDateStr()}", apiKey)

        if (code in 200..299) {
            return try {
                val json = JSONObject(body)
                val data = json.optJSONArray("data")
                val modelUsages = mutableListOf<ModelUsage>()
                var totalPrompt = 0L
                var totalCompletion = 0L
                var totalTokens = 0L
                var totalCalls = 0L

                if (data != null) {
                    for (i in 0 until data.length()) {
                        val item = data.optJSONObject(i) ?: continue
                        val modelName = item.optString("snapshot_id",
                            item.optString("model", "unknown"))
                        val promptTokens = item.optLong("n_context_tokens_total",
                            item.optLong("prompt_tokens", 0))
                        val completionTokens = item.optLong("n_generated_tokens_total",
                            item.optLong("completion_tokens", 0))
                        val tokens = promptTokens + completionTokens
                        val calls = item.optLong("n_requests",
                            item.optLong("num_model_requests", 0))

                        totalPrompt += promptTokens
                        totalCompletion += completionTokens
                        totalTokens += tokens
                        totalCalls += calls

                        modelUsages.add(ModelUsage(
                            modelName = modelName,
                            callCount = calls,
                            promptTokens = promptTokens,
                            completionTokens = completionTokens,
                            totalTokens = tokens
                        ))
                    }
                }

                // Also get available models
                val availableModels = if (provider.modelsEndpoint != null) {
                    val (modelCode, modelBody) = httpGet("${provider.baseUrl}${provider.modelsEndpoint}", apiKey)
                    if (modelCode in 200..299) extractModelNames(modelBody) else emptyList()
                } else emptyList()

                QueryResult(success = true, usageInfo = UsageInfo(
                    totalPromptTokens = totalPrompt,
                    totalCompletionTokens = totalCompletion,
                    totalTokens = totalTokens,
                    totalCalls = totalCalls,
                    modelUsage = modelUsages,
                    availableModels = availableModels
                ))
            } catch (e: Exception) {
                // Fallback: try model list
                queryModelsOnly(provider, apiKey)
            }
        }

        // Usage API failed (likely 403 - no billing access), fallback to model list
        if (provider.modelsEndpoint != null) {
            return queryModelsOnly(provider, apiKey)
        }

        return QueryResult(success = true, usageInfo = UsageInfo())
    }

    // ===== Model list only (for providers without usage API) =====
    private fun queryModelsOnly(provider: ProviderConfig, apiKey: String): QueryResult {
        val endpoint = provider.modelsEndpoint ?: provider.balanceEndpoint
        val headerName = provider.authHeaderName
        val headerPrefix = provider.authHeaderPrefix
        val urlStr = if (endpoint.startsWith("http")) endpoint else "${provider.baseUrl}$endpoint"
        val (code, body) = httpGet(urlStr, apiKey, headerName, headerPrefix)

        return if (code in 200..299) {
            val modelNames = extractModelNames(body)
            QueryResult(success = true, usageInfo = UsageInfo(availableModels = modelNames))
        } else {
            QueryResult(success = true, usageInfo = UsageInfo()) // key invalid but don't error
        }
    }

    private fun httpGet(
        urlStr: String,
        apiKey: String,
        headerName: String = "Authorization",
        headerPrefix: String = "Bearer "
    ): Pair<Int, String> {
        return try {
            val url = URL(urlStr)
            val connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty(headerName, "$headerPrefix$apiKey")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 15000
                readTimeout = 15000
            }
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val responseText = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            connection.disconnect()
            Pair(responseCode, responseText)
        } catch (e: Exception) {
            Pair(-1, e.message ?: "Unknown error")
        }
    }

    // ===== DeepSeek =====
    private fun queryDeepSeek(provider: ProviderConfig, apiKey: String): QueryResult {
        val (code, body) = httpGet("${provider.baseUrl}${provider.balanceEndpoint}", apiKey)
        if (code == 200) {
            return try {
                val json = JSONObject(body)
                val isAvailable = json.optBoolean("is_available", false)
                val balanceInfos = json.optJSONArray("balance_infos")
                if (balanceInfos != null && balanceInfos.length() > 0) {
                    val info = balanceInfos.getJSONObject(0)
                    QueryResult(success = true, balanceResult = BalanceResult(
                        isAvailable = isAvailable,
                        totalBalance = info.optString("total_balance", "0"),
                        toppedUpBalance = info.optString("topped_up_balance", "0"),
                        grantedBalance = info.optString("granted_balance", "0"),
                        currency = info.optString("currency", "CNY"),
                        rawResponse = body
                    ))
                } else {
                    QueryResult(success = true, errorMessage = "No balance info")
                }
            } catch (e: Exception) {
                QueryResult(success = false, errorMessage = "Parse error: ${e.message}")
            }
        }
        return QueryResult(success = false, errorMessage = parseError(body, code))
    }

    // ===== OpenAI =====
    private fun queryOpenAI(provider: ProviderConfig, apiKey: String): QueryResult {
        val (code, body) = httpGet("${provider.baseUrl}${provider.balanceEndpoint}", apiKey)
        if (code == 200) {
            return try {
                val json = JSONObject(body)
                val models = json.optJSONArray("data")
                val modelCount = models?.length() ?: 0
                val modelNames = extractModelNames(body)
                QueryResult(success = true, balanceResult = BalanceResult(
                    isAvailable = true,
                    totalBalance = "$modelCount models",
                    toppedUpBalance = "Key valid",
                    grantedBalance = "",
                    currency = "",
                    rawResponse = body
                ), usageInfo = UsageInfo(availableModels = modelNames))
            } catch (e: Exception) {
                QueryResult(success = false, errorMessage = "Parse error: ${e.message}")
            }
        }
        return QueryResult(success = false, errorMessage = parseError(body, code))
    }

    // ===== 智谱AI (GLM) =====
    private fun queryZhipuAI(provider: ProviderConfig, apiKey: String): QueryResult {
        val (code, body) = httpGet("${provider.baseUrl}${provider.balanceEndpoint}", apiKey)
        if (code == 200) {
            return try {
                val json = JSONObject(body)
                val data = json.optJSONObject("data") ?: json
                val username = data.optString("user_name", data.optString("name", "Unknown"))
                val isAvailable = json.optBoolean("success", true) || data.length() > 0
                QueryResult(success = true, balanceResult = BalanceResult(
                    isAvailable = isAvailable,
                    totalBalance = "Available",
                    toppedUpBalance = username,
                    grantedBalance = "",
                    currency = "",
                    rawResponse = body
                ))
            } catch (e: Exception) {
                QueryResult(success = false, errorMessage = "Parse error: ${e.message}")
            }
        }
        return QueryResult(success = false, errorMessage = parseError(body, code))
    }

    // ===== Moonshot (Kimi) =====
    private fun queryMoonshot(provider: ProviderConfig, apiKey: String): QueryResult {
        val (code, body) = httpGet("${provider.baseUrl}${provider.balanceEndpoint}", apiKey)
        if (code == 200) {
            return try {
                val json = JSONObject(body)
                val data = json.optJSONObject("data") ?: json
                val balance = data.optString("available_balance",
                                data.optString("balance", "0"))
                val isAvailable = balance.toDoubleOrNull()?.let { it > 0 } ?: true
                QueryResult(success = true, balanceResult = BalanceResult(
                    isAvailable = isAvailable,
                    totalBalance = balance,
                    toppedUpBalance = "CNY",
                    grantedBalance = "",
                    currency = "",
                    rawResponse = body
                ))
            } catch (e: Exception) {
                QueryResult(success = false, errorMessage = "Parse error: ${e.message}")
            }
        }
        return QueryResult(success = false, errorMessage = parseError(body, code))
    }

    // ===== Anthropic (Claude) =====
    private fun queryAnthropic(provider: ProviderConfig, apiKey: String): QueryResult {
        val (code, body) = httpGet(
            "${provider.baseUrl}${provider.balanceEndpoint}",
            apiKey,
            headerName = "x-api-key",
            headerPrefix = ""
        )
        if (code == 200) {
            return try {
                val json = JSONObject(body)
                val models = json.optJSONArray("data")
                val modelCount = models?.length() ?: 0
                val modelNames = extractModelNames(body)
                QueryResult(success = true, balanceResult = BalanceResult(
                    isAvailable = true,
                    totalBalance = "$modelCount models",
                    toppedUpBalance = "Key valid",
                    grantedBalance = "",
                    currency = "",
                    rawResponse = body
                ), usageInfo = UsageInfo(availableModels = modelNames))
            } catch (e: Exception) {
                QueryResult(success = false, errorMessage = "Parse error: ${e.message}")
            }
        }
        return QueryResult(success = false, errorMessage = parseError(body, code))
    }

    // ===== Groq =====
    private fun queryGroq(provider: ProviderConfig, apiKey: String): QueryResult {
        val (code, body) = httpGet("${provider.baseUrl}${provider.balanceEndpoint}", apiKey)
        if (code == 200) {
            return try {
                val json = JSONObject(body)
                val models = json.optJSONArray("data")
                val modelCount = models?.length() ?: 0
                val modelNames = extractModelNames(body)
                QueryResult(success = true, balanceResult = BalanceResult(
                    isAvailable = true,
                    totalBalance = "$modelCount models",
                    toppedUpBalance = "Key valid",
                    grantedBalance = "",
                    currency = "",
                    rawResponse = body
                ), usageInfo = UsageInfo(availableModels = modelNames))
            } catch (e: Exception) {
                QueryResult(success = false, errorMessage = "Parse error: ${e.message}")
            }
        }
        return QueryResult(success = false, errorMessage = parseError(body, code))
    }

    // ===== MiniMax =====
    private fun queryMiniMax(provider: ProviderConfig, apiKey: String): QueryResult {
        val (code, body) = httpGet("${provider.baseUrl}${provider.balanceEndpoint}", apiKey)
        if (code == 200) {
            return try {
                val json = JSONObject(body)
                val baseResp = json.optJSONObject("base_resp") ?: json
                val statusCode = baseResp.optInt("status_code", 0)
                val isAvailable = statusCode == 0 || json.has("available_balance")
                val balance = json.optString("available_balance",
                                json.optString("balance", "Available"))
                QueryResult(success = true, balanceResult = BalanceResult(
                    isAvailable = isAvailable,
                    totalBalance = balance,
                    toppedUpBalance = "Available",
                    grantedBalance = "",
                    currency = "",
                    rawResponse = body
                ))
            } catch (e: Exception) {
                QueryResult(success = false, errorMessage = "Parse error: ${e.message}")
            }
        }
        return QueryResult(success = false, errorMessage = parseError(body, code))
    }

    // ===== 阿里百炼 (DashScope) =====
    private fun queryDashScope(provider: ProviderConfig, apiKey: String): QueryResult {
        val (code, body) = httpGet("${provider.baseUrl}${provider.balanceEndpoint}", apiKey)
        if (code == 200) {
            return try {
                val json = JSONObject(body)
                val models = json.optJSONArray("data")
                val modelCount = models?.length() ?: 0
                val modelNames = extractModelNames(body)
                QueryResult(success = true, balanceResult = BalanceResult(
                    isAvailable = true,
                    totalBalance = "$modelCount models",
                    toppedUpBalance = "Key valid",
                    grantedBalance = "",
                    currency = "",
                    rawResponse = body
                ), usageInfo = UsageInfo(availableModels = modelNames))
            } catch (e: Exception) {
                QueryResult(success = false, errorMessage = "Parse error: ${e.message}")
            }
        }
        return QueryResult(success = false, errorMessage = parseError(body, code))
    }

    private fun parseError(body: String, code: Int): String {
        return try {
            val json = JSONObject(body)
            json.optString("error",
                json.optJSONObject("error")?.optString("message",
                    "HTTP $code"))
        } catch (e: Exception) {
            "HTTP $code"
        }
    }
}
