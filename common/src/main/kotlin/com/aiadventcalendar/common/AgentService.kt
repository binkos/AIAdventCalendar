package com.aiadventcalendar.common

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.defaultStdioTransport
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.abs

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
 * Response type: required_questions - initial list of questions LLM needs answered
 */
@Serializable
@SerialName("required_questions")
data class RequiredQuestionsResponse(
    val type: String = "required_questions",
    val questions: List<QuestionItem>,
    val totalQuestions: Int,
    val currentQuestionIndex: Int = 0
)

/**
 * Response type: question - a single question to ask the user
 */
@Serializable
@SerialName("question")
data class QuestionResponse(
    val type: String = "question",
    val questionId: Int,
    val question: String,
    val category: String,
    val remainingQuestions: Int
)

/**
 * Response type: answer - final comprehensive answer
 */
@Serializable
@SerialName("answer")
data class AnswerResponse(
    val type: String = "answer",
    val answer: String
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

/**
 * Represents a chat conversation.
 */
@Serializable
data class Chat(
    val id: String,
    val agentId: String,
    val messages: MutableList<HistoryMessage> = mutableListOf(),
    val createdAt: Long,
    var updatedAt: Long
)

/**
 * Data class to store agent information and chats.
 */
data class AgentData(
    val agentId: String,
    val chats: MutableMap<String, Chat> = mutableMapOf(),
    val createdAt: Long
)

/**
 * Temperature presets for LLM experimentation.
 * Each preset demonstrates different characteristics.
 */
enum class TemperaturePreset(val value: Double, val label: String, val description: String) {
    PRECISE(0.0, "Precise (0.0)", "Most deterministic and factual responses"),
    BALANCED(0.7, "Balanced (0.7)", "Good balance between accuracy and creativity"),
    CREATIVE(1.2, "Creative (1.2)", "More diverse and imaginative responses");

    companion object {
        fun fromValue(value: Double): TemperaturePreset {
            return entries.minByOrNull { abs(it.value - value) } ?: BALANCED
        }
    }
}

class AgentService(apiKey: String, dbPath: String = "agents.db") {
    val client = OpenAILLMClient(apiKey)

    private val db = DatabaseHelper(dbPath)

    private val systemPromptText = "You are helpfully assistant"

    private val mcpJarPath: String = run {
        // Try multiple possible locations for the JAR file (in order of preference)
        val possiblePaths = listOfNotNull(
            // From environment variable if set (highest priority)
            System.getenv("MCP_JAR_PATH"),
            // From system property if set
            System.getProperty("mcp.jar.path"),
            // Standard build location when running from project root
            "mcp/build/libs/mcp-0.1.0-all.jar",
            // When running from common module directory
            "../mcp/build/libs/mcp-0.1.0-all.jar",
            // When running from project root - source location (fallback)
            "mcp/src/main/kotlin/com/aiadventcalendar/mcp/mcp-0.1.0-all.jar",
            // When running from common module - source location (fallback)
            "../mcp/src/main/kotlin/com/aiadventcalendar/mcp/mcp-0.1.0-all.jar"
        )

        // Find the first existing file
        val foundPath = possiblePaths.firstOrNull { path ->
            java.io.File(path).exists()
        }

        foundPath ?: throw IllegalStateException(
            "MCP JAR file not found. Searched in: ${possiblePaths.joinToString(", ")}. " +
                    "Please build the MCP module first with: ./gradlew :mcp:shadowJar " +
                    "or set MCP_JAR_PATH environment variable or mcp.jar.path system property."
        )
    }

    private val process = ProcessBuilder("java", "-jar", mcpJarPath).start()
    val transport = McpToolRegistryProvider.defaultStdioTransport(process)
    private val toolRegistry: ToolRegistry = runBlocking {
        McpToolRegistryProvider.fromTransport(
            transport = transport,
            name = "my-client",
            version = "1.0.0"
        )
    }

    var summarizerPrompt = prompt(
        id = "summarize-prompt"
    ) {
        system("You are history compression assistant, you receive user's messages with AI agent and return one message a summary of existed dialog, to use later in conversation instead of history")
    }

    val model = OpenAIModels.Chat.GPT4o

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        classDiscriminator = "type"
    }

    // Cache for prompts to avoid rebuilding from DB on every call
    private val promptCache: MutableMap<String, Prompt> = mutableMapOf()

    /**
     * Creates or retrieves an existing agent by ID.
     */
    fun createOrGetAgent(agentId: String): AgentData {
        val now = Clock.System.now().toEpochMilliseconds() / 1000
        val createdAt = db.createOrGetAgent(agentId, now)
        return AgentData(
            agentId = agentId,
            createdAt = createdAt
        )
    }

    fun getTools(): List<String> {
        return toolRegistry.tools.map { it.name }
    }

    /**
     * Builds a prompt from stored messages in the database.
     */
    private fun buildPromptFromChat(agentId: String, chatId: String): Prompt {
        val chat = db.getChat(chatId) ?: throw IllegalStateException("Chat not found: $chatId")

        return prompt(id = "chat-$chatId") {
            system(systemPromptText)

            // Add all stored messages to the prompt
            chat.messages.forEach { msg ->
                when (msg.role) {
                    "user" -> user(msg.content)
                    "assistant" -> assistant(msg.content)
                    else -> {} // Skip other roles
                }
            }
        }
    }

    /**
     * Returns all chats for an agent, sorted by updatedAt descending.
     */
    fun getChatHistory(agentId: String): List<Chat> {
        if (!db.agentExists(agentId)) {
            return emptyList()
        }
        return db.getChatsForAgent(agentId)
    }

    /**
     * Creates a new chat for the specified agent.
     */
    fun createChat(agentId: String): Chat {
        createOrGetAgent(agentId) // Ensure agent exists
        val chatId = UUID.randomUUID().toString()
        val now = Clock.System.now().toEpochMilliseconds() / 1000

        db.createChat(chatId, agentId, now, now)

        // Initialize prompt cache
        val newPrompt = prompt(id = "chat-$chatId") {
            system(systemPromptText)
        }
        promptCache["$agentId:$chatId"] = newPrompt

        return Chat(
            id = chatId,
            agentId = agentId,
            messages = mutableListOf(),
            createdAt = now,
            updatedAt = now
        )
    }

    /**
     * Retrieves a specific chat by agent ID and chat ID.
     */
    fun getChat(agentId: String, chatId: String): Chat? {
        return db.getChat(chatId)?.takeIf { it.agentId == agentId }
    }

    /**
     * Adds a message to a chat and updates the updatedAt timestamp.
     */
    private fun addMessageToChat(message: HistoryMessage) {
        val now = Clock.System.now().toEpochMilliseconds() / 1000
        db.addMessage(message, now)
        // Invalidate prompt cache for this chat
        promptCache.remove("${message.agentId}:${message.chatId}")
    }

    /**
     * Processes a user message and returns a direct answer.
     *
     * @param agentId The agent ID
     * @param chatId The chat ID
     * @param userMessage The user's message/question
     * @param temperature LLM temperature (0.0 = precise, 0.7 = balanced, 1.2+ = creative)
     * @return JSON string with type "answer"
     */
    suspend fun processMessage(
        agentId: String,
        chatId: String,
        userMessage: String,
        temperature: Double = 0.7
    ): String = withContext(Dispatchers.IO) {
        val chat = getChat(agentId, chatId)
            ?: throw IllegalStateException("Chat not found: agentId=$agentId, chatId=$chatId")

        // Get or build prompt from cache or database
        val cacheKey = "$agentId:${chat.id}"
        var historyPrompt = promptCache[cacheKey] ?: buildPromptFromChat(agentId, chat.id)

        // Store user message first
        val userMessageObj = HistoryMessage(
            role = "user",
            content = userMessage,
            agentId = agentId,
            chatId = chatId
        )
        addMessageToChat(userMessageObj)

        // Add user message to prompt
        historyPrompt = prompt(existing = historyPrompt) {
            user(userMessage)
        }
        promptCache[cacheKey] = historyPrompt

        val responses = client.execute(
            prompt = historyPrompt,
            model = model
        )

        val updatedPrompt = if ((historyPrompt.messages.count() + responses.size) % 11 == 0) {
            val summaryPrompt = getSummaryPromptFromHistoryPrompt(
                historyPrompt = historyPrompt.copy(messages = historyPrompt.messages + responses)
            )

            val response = client.execute(
                prompt = summaryPrompt,
                model = OpenAIModels.Reasoning.O3Mini
            )

            println(response.joinToString { it.content })

            prompt(id = historyPrompt.id) {
                val sysMessage = historyPrompt.messages.first()
                message(sysMessage)
                response.firstOrNull()?.let {
                    system(it.content)
                }
            }
        } else {
            prompt(historyPrompt) {
                messages(responses)
            }
        }

        promptCache[cacheKey] = updatedPrompt

        // Store assistant responses
        responses.forEach { response ->
            val assistantMessage = HistoryMessage(
                role = "assistant",
                content = response.content,
                agentId = agentId,
                chatId = chatId
            )
            addMessageToChat(assistantMessage)
        }

        return@withContext responses.joinToString { "${it.content} \n---Tokens info: ---\ninput tokens with history: ${it.metaInfo.inputTokensCount}, output tokens: ${it.metaInfo.outputTokensCount} \ntotal tokens used per session: ${it.metaInfo.totalTokensCount}" }
    }

    private fun getSummaryPromptFromHistoryPrompt(historyPrompt: Prompt): Prompt {
        return summarizerPrompt.copy(
            messages = buildList {
                add(summarizerPrompt.messages.first())
                try {
                    val prevSummary = historyPrompt.messages[1]
                    val prevSummaryExists = prevSummary.role == Message.Role.System
                    if (prevSummaryExists) {
                        Message.System(
                            content = "This is not the first iteration of summarizing, please use this one as summary of previous dialog history: ${historyPrompt.messages[1].content}",
                            metaInfo = RequestMetaInfo(
                                timestamp = prevSummary.metaInfo.timestamp,
                                metadata = prevSummary.metaInfo.metadata,
                            )
                        )
                    }
                } catch (_: Exception) {
                }
                add(
                    Message.User(
                        getSummaryTextPrompt(
                            historyMessage = historyPrompt.messages.filter { it.role != Message.Role.System }
                        ),
                        metaInfo = RequestMetaInfo(Clock.System.now())
                    )
                )
            }
        )
    }

    private fun getSummaryTextPrompt(
        historyMessage: List<Message>
    ): String {
        return buildString {
            appendLine("Please Summarize history messages of my chat with Agent to keep main info and points of discussed topic")
            historyMessage.forEach {
                if (it.role == Message.Role.Assistant) {
                    appendLine("Agent: ${it.content}")
                } else {
                    appendLine("User: ${it.content}")

                }
            }
        }
    }
}