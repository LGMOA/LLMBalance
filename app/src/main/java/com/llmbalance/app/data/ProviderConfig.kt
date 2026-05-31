package com.llmbalance.app.data

data class ProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val balanceEndpoint: String,
    val description: String,
    val iconColor: Int = 0xFF1A73E8.toInt(),
    val authHeaderName: String = "Authorization",
    val authHeaderPrefix: String = "Bearer "
)

object Providers {
    val DEEPSEEK = ProviderConfig(
        id = "deepseek",
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.com",
        balanceEndpoint = "/user/balance",
        description = "DeepSeek 余额查询",
        iconColor = 0xFF4D6BFE.toInt()
    )

    val OPENAI = ProviderConfig(
        id = "openai",
        name = "OpenAI",
        baseUrl = "https://api.openai.com",
        balanceEndpoint = "/v1/models",  // Validates key + shows available models
        description = "OpenAI Key 验证 + 模型列表",
        iconColor = 0xFF10A37F.toInt()
    )

    val ZHIPU = ProviderConfig(
        id = "zhipu",
        name = "智谱AI (GLM)",
        baseUrl = "https://open.bigmodel.cn",
        balanceEndpoint = "/api/paas/v4/account/info",
        description = "智谱 GLM 账户信息",
        iconColor = 0xFF3859FF.toInt()
    )

    val MOONSHOT = ProviderConfig(
        id = "moonshot",
        name = "Moonshot (Kimi)",
        baseUrl = "https://api.moonshot.cn",
        balanceEndpoint = "/v1/users/me/balance",
        description = "Kimi 余额查询",
        iconColor = 0xFF6B4EFF.toInt()
    )

    val ANTHROPIC = ProviderConfig(
        id = "anthropic",
        name = "Anthropic (Claude)",
        baseUrl = "https://api.anthropic.com",
        balanceEndpoint = "/v1/models",  // Validates key
        description = "Claude Key 验证",
        iconColor = 0xFFD97757.toInt(),
        authHeaderName = "x-api-key",
        authHeaderPrefix = ""
    )

    val GROQ = ProviderConfig(
        id = "groq",
        name = "Groq",
        baseUrl = "https://api.groq.com",
        balanceEndpoint = "/openai/v1/models",  // Validates key
        description = "Groq Key 验证",
        iconColor = 0xFFF55036.toInt()
    )

    val MINIMAX = ProviderConfig(
        id = "minimax",
        name = "MiniMax",
        baseUrl = "https://api.minimax.chat",
        balanceEndpoint = "/v1/account/info",
        description = "MiniMax 账户信息",
        iconColor = 0xFF0066FF.toInt()
    )

    val DASHSCOPE = ProviderConfig(
        id = "dashscope",
        name = "阿里百炼 (DashScope)",
        baseUrl = "https://dashscope.aliyuncs.com",
        balanceEndpoint = "/compatible-mode/v1/models",
        description = "阿里百炼 Key 验证",
        iconColor = 0xFFFF6A00.toInt()
    )

    val ALL = listOf(DEEPSEEK, OPENAI, ZHIPU, MOONSHOT, ANTHROPIC, GROQ, MINIMAX, DASHSCOPE)

    fun getById(id: String): ProviderConfig? = ALL.find { it.id == id }
}
