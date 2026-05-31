package com.llmbalance.app.data

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

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
                QueryResult(success = true, balanceResult = BalanceResult(
                    isAvailable = true,
                    totalBalance = "$modelCount models",
                    toppedUpBalance = "Key valid",
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
                QueryResult(success = true, balanceResult = BalanceResult(
                    isAvailable = true,
                    totalBalance = "$modelCount models",
                    toppedUpBalance = "Key valid",
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

    // ===== Groq =====
    private fun queryGroq(provider: ProviderConfig, apiKey: String): QueryResult {
        val (code, body) = httpGet("${provider.baseUrl}${provider.balanceEndpoint}", apiKey)
        if (code == 200) {
            return try {
                val json = JSONObject(body)
                val models = json.optJSONArray("data")
                val modelCount = models?.length() ?: 0
                QueryResult(success = true, balanceResult = BalanceResult(
                    isAvailable = true,
                    totalBalance = "$modelCount models",
                    toppedUpBalance = "Key valid",
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
                QueryResult(success = true, balanceResult = BalanceResult(
                    isAvailable = true,
                    totalBalance = "$modelCount models",
                    toppedUpBalance = "Key valid",
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
