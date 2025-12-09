package com.aiadventcalendar.desktop

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Represents a message in the conversation history.
 */
@Serializable
data class HistoryMessage(
    val role: String,  // "user" or "assistant"
    val content: String
)

/**
 * Question item in the required questions list.
 */
@Serializable
data class QuestionItem(
    val id: Int,
    val question: String,
    val category: String
)

/**
 * Sealed class representing all possible agent responses.
 */
@Serializable
sealed class AgentResponse {
    
    @Serializable
    @SerialName("required_questions")
    data class RequiredQuestions(
        val type: String = "required_questions",
        val questions: List<QuestionItem>,
        val totalQuestions: Int,
        val currentQuestionIndex: Int = 0
    ) : AgentResponse()
    
    @Serializable
    @SerialName("question")
    data class Question(
        val type: String = "question",
        val questionId: Int,
        val question: String,
        val category: String,
        val remainingQuestions: Int
    ) : AgentResponse()
    
    @Serializable
    @SerialName("answer")
    data class Answer(
        val type: String = "answer",
        val answer: String
    ) : AgentResponse()
    
    @Serializable
    @SerialName("comparison_answer")
    data class ComparisonAnswer(
        val type: String = "comparison_answer",
        val openaiResponse: LlmResponseResult,
        val arceeAiResponse: LlmResponseResult,
        val comparisonAnalysis: String
    ) : AgentResponse()
}

/**
 * Token usage statistics for an LLM response.
 */
@Serializable
data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int
)

/**
 * Result from a single LLM execution with metrics.
 */
@Serializable
data class LlmResponseResult(
    val modelName: String,
    val response: String,
    val executionTimeMs: Long,
    val tokenUsage: TokenUsage,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

class BackendClient(private val baseUrl: String) {
    
    companion object {
        private const val TIMEOUT_MS = 120_000L // 2 minutes
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        classDiscriminator = "type"
    }
    
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = TIMEOUT_MS
            connectTimeoutMillis = TIMEOUT_MS
            socketTimeoutMillis = TIMEOUT_MS
        }
    }
    
    /**
     * Sends a message to the conversational agent with history.
     * Returns the raw JSON response string.
     * 
     * @param message The user message to send
     * @param history Previous conversation history
     * @param temperature LLM temperature (0.0 = precise, 0.7 = balanced, 1.2+ = creative)
     */
    suspend fun sendMessage(
        message: String,
        history: List<HistoryMessage> = emptyList(),
        temperature: Double = 0.7
    ): String = withContext(Dispatchers.IO) {
        try {
            val request = ConversationRequest(
                message = message,
                history = history.map { ConversationHistoryItem(role = it.role, content = it.content) },
                temperature = temperature
            )
            val response: HttpResponse = httpClient.post("$baseUrl/conversation") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (response.status == HttpStatusCode.OK) {
                val conversationResponse: ConversationResponse = response.body()
                conversationResponse.response
            } else {
                val errorResponse: ErrorResponse = response.body()
                throw Exception(errorResponse.error ?: "Unknown error from backend")
            }
        } catch (e: Exception) {
            throw Exception("Failed to send message: ${e.message}", e)
        }
    }
    
    /**
     * Parses the agent response JSON into a typed AgentResponse object.
     */
    fun parseResponse(responseJson: String): AgentResponse? {
        return try {
            val typeRegex = """"type"\s*:\s*"(\w+)"""".toRegex()
            val typeMatch = typeRegex.find(responseJson)
            val type = typeMatch?.groupValues?.get(1)
            
            when (type) {
                "required_questions" -> json.decodeFromString<AgentResponse.RequiredQuestions>(responseJson)
                "question" -> json.decodeFromString<AgentResponse.Question>(responseJson)
                "answer" -> json.decodeFromString<AgentResponse.Answer>(responseJson)
                "comparison_answer" -> json.decodeFromString<AgentResponse.ComparisonAnswer>(responseJson)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response: HealthResponse = httpClient.get("$baseUrl/health") {
                contentType(ContentType.Application.Json)
            }.body()
            response.status == "ok"
        } catch (e: Exception) {
            false
        }
    }
    
    fun close() {
        httpClient.close()
    }
}

@Serializable
private data class ConversationRequest(
    val message: String,
    val history: List<ConversationHistoryItem> = emptyList(),
    val temperature: Double = 0.7
)

@Serializable
private data class ConversationHistoryItem(
    val role: String,
    val content: String
)

@Serializable
private data class ConversationResponse(val response: String)

@Serializable
private data class ErrorResponse(val error: String?)

@Serializable
private data class HealthResponse(val status: String)

