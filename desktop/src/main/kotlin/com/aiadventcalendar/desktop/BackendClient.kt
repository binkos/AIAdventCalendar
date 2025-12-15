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
    val content: String,
    val agentId: String,
    val chatId: String
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
}

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
     * Gets or creates an agent and returns its info with chat history.
     */
    suspend fun getAgent(agentId: String): AgentInfoResponse = withContext(Dispatchers.IO) {
        try {
            val response: HttpResponse = httpClient.get("$baseUrl/agents/$agentId") {
                contentType(ContentType.Application.Json)
            }
            
            if (response.status == HttpStatusCode.OK) {
                response.body()
            } else {
                val errorResponse: ErrorResponse = response.body()
                throw Exception(errorResponse.error ?: "Unknown error from backend")
            }
        } catch (e: Exception) {
            throw Exception("Failed to get agent: ${e.message}", e)
        }
    }
    
    /**
     * Creates a new chat for the specified agent.
     */
    suspend fun createChat(agentId: String): CreateChatResponse = withContext(Dispatchers.IO) {
        try {
            val request = CreateChatRequest(agentId = agentId)
            val response: HttpResponse = httpClient.post("$baseUrl/agents/$agentId/chats") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (response.status == HttpStatusCode.OK) {
                response.body()
            } else {
                val errorResponse: ErrorResponse = response.body()
                throw Exception(errorResponse.error ?: "Unknown error from backend")
            }
        } catch (e: Exception) {
            throw Exception("Failed to create chat: ${e.message}", e)
        }
    }
    
    /**
     * Gets chat history for an agent.
     */
    suspend fun getChatHistory(agentId: String): ChatHistoryResponse = withContext(Dispatchers.IO) {
        try {
            val response: HttpResponse = httpClient.get("$baseUrl/agents/$agentId/chats") {
                contentType(ContentType.Application.Json)
            }
            
            if (response.status == HttpStatusCode.OK) {
                response.body()
            } else {
                val errorResponse: ErrorResponse = response.body()
                throw Exception(errorResponse.error ?: "Unknown error from backend")
            }
        } catch (e: Exception) {
            throw Exception("Failed to get chat history: ${e.message}", e)
        }
    }
    
    /**
     * Gets a specific chat by ID.
     */
    suspend fun getChat(agentId: String, chatId: String): ChatResponse = withContext(Dispatchers.IO) {
        try {
            val response: HttpResponse = httpClient.get("$baseUrl/agents/$agentId/chats/$chatId") {
                contentType(ContentType.Application.Json)
            }
            
            if (response.status == HttpStatusCode.OK) {
                response.body()
            } else {
                val errorResponse: ErrorResponse = response.body()
                throw Exception(errorResponse.error ?: "Unknown error from backend")
            }
        } catch (e: Exception) {
            throw Exception("Failed to get chat: ${e.message}", e)
        }
    }
    
    /**
     * Sends a message to a specific chat.
     * Returns the raw JSON response string.
     * 
     * @param agentId The agent ID
     * @param chatId The chat ID
     * @param message The user message to send
     * @param temperature LLM temperature (0.0 = precise, 0.7 = balanced, 1.2+ = creative)
     */
    suspend fun sendMessage(
        agentId: String,
        chatId: String,
        message: String,
        temperature: Double = 0.7
    ): String = withContext(Dispatchers.IO) {
        try {
            val request = SendMessageRequest(
                message = message,
                temperature = temperature
            )
            val response: HttpResponse = httpClient.post("$baseUrl/agents/$agentId/chats/$chatId/messages") {
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
data class AgentInfoResponse(
    val agentId: String,
    val chats: List<ChatSummary>
)

@Serializable
data class ChatSummary(
    val id: String,
    val agentId: String,
    val lastMessage: String,
    val updatedAt: Long
)

@Serializable
data class Chat(
    val id: String,
    val agentId: String,
    val messages: List<HistoryMessage>,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
private data class CreateChatRequest(
    val agentId: String
)

@Serializable
data class CreateChatResponse(
    val chatId: String,
    val createdAt: Long
)

@Serializable
data class ChatHistoryResponse(
    val chats: List<ChatSummary>
)

@Serializable
data class ChatResponse(
    val chat: Chat
)

@Serializable
private data class SendMessageRequest(
    val message: String,
    val temperature: Double = 0.7
)

@Serializable
private data class ConversationResponse(val response: String)

@Serializable
private data class ErrorResponse(val error: String?)

@Serializable
private data class HealthResponse(val status: String)

