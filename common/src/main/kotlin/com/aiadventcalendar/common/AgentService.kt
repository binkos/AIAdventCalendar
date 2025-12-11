package com.aiadventcalendar.common

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
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
 * Temperature presets for LLM experimentation.
 * Each preset demonstrates different characteristics.
 */
enum class TemperaturePreset(val value: Double, val label: String, val description: String) {
    PRECISE(0.0, "Precise (0.0)", "Most deterministic and factual responses"),
    BALANCED(0.7, "Balanced (0.7)", "Good balance between accuracy and creativity"),
    CREATIVE(1.2, "Creative (1.2)", "More diverse and imaginative responses");

    companion object {
        fun fromValue(value: Double): TemperaturePreset {
            return entries.minByOrNull { kotlin.math.abs(it.value - value) } ?: BALANCED
        }
    }
}

class AgentService(private val apiKey: String) {
    val client = OpenAILLMClient(apiKey)
    var historyPrompt = prompt(
        id = "running-prompt",
    ) {
        system("You are helpfully assistant")
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

    /**
     * Processes a user message and returns a direct answer.
     *
     * @param userMessage The user's message/question
     * @param historyMessages Previous conversation history (kept for API compatibility)
     * @param temperature LLM temperature (0.0 = precise, 0.7 = balanced, 1.2+ = creative)
     * @return JSON string with type "answer"
     */
    suspend fun processMessage(
        userMessage: String,
        historyMessages: List<HistoryMessage> = emptyList(),
        temperature: Double = 0.7
    ): String = withContext(Dispatchers.IO) {
        historyPrompt = prompt(existing = historyPrompt) {
            user(userMessage)
        }

        val responses = client.execute(
            prompt = historyPrompt,
            model = model
        )

        historyPrompt = if ((historyPrompt.messages.count() + responses.size) % 11 == 0) {
            summarizerPrompt = summarizerPrompt.copy(
                messages = buildList {
                    add(summarizerPrompt.messages.first())
                    addAll(historyPrompt.messages.subList(1, historyPrompt.messages.lastIndex))
                    addAll(responses)
                    add(
                        Message.User(
                            "Summarize history messages",
                            metaInfo = RequestMetaInfo(Clock.System.now())
                        )
                    )
                }
            )

            val response = client.execute(
                prompt = summarizerPrompt,
                model = OpenAIModels.Reasoning.O3Mini
            )

            println(response.joinToString { it.content })

            prompt(id = historyPrompt.id) {
                val sysMessage = historyPrompt.messages.first()
                message(sysMessage)
                messages(response)
            }
        } else {
            prompt(historyPrompt) {
                messages(responses)
            }
        }

        return@withContext responses.joinToString { "${it.content} \n---Tokens info: ---\ninput tokens with history: ${it.metaInfo.inputTokensCount}, output tokens: ${it.metaInfo.outputTokensCount} \ntotal tokens used per session: ${it.metaInfo.totalTokensCount}" }
    }

    /**
     * Parses the agent response JSON into a typed AgentResponse object.
     */
    fun parseResponse(responseJson: String): AgentResponse? {
        return try {
            // First try to determine the type
            val typeRegex = """"type"\s*:\s*"(\w+)"""".toRegex()
            val typeMatch = typeRegex.find(responseJson)
            val type = typeMatch?.groupValues?.get(1)

            when (type) {
                "required_questions" -> {
                    val parsed = json.decodeFromString<RequiredQuestionsResponse>(responseJson)
                    AgentResponse.RequiredQuestions(
                        questions = parsed.questions,
                        totalQuestions = parsed.totalQuestions,
                        currentQuestionIndex = parsed.currentQuestionIndex
                    )
                }

                "question" -> {
                    val parsed = json.decodeFromString<QuestionResponse>(responseJson)
                    AgentResponse.Question(
                        questionId = parsed.questionId,
                        question = parsed.question,
                        category = parsed.category,
                        remainingQuestions = parsed.remainingQuestions
                    )
                }

                "answer" -> {
                    val parsed = json.decodeFromString<AnswerResponse>(responseJson)
                    AgentResponse.Answer(answer = parsed.answer)
                }

                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getConversationalSystemPrompt(): String {
        return """
        You are a helpful, creative, and knowledgeable AI assistant.
        
        ## YOUR ROLE
        - Answer questions directly and immediately
        - Be helpful, informative, and engaging
        - Adapt your style based on the question type
        
        ## RESPONSE FORMAT
        Always respond with this JSON format:
        {"type":"answer","answer":"Your response here..."}
        
        ## GUIDELINES
        
        ### For Creative Tasks (poems, stories, ideas):
        - Be imaginative and expressive
        - Use vivid language and interesting metaphors
        - Don't be afraid to be unique and surprising
        
        ### For Factual Questions:
        - Be accurate and informative
        - Explain concepts clearly
        - Use examples when helpful
        
        ### For Brainstorming:
        - Generate diverse and varied ideas
        - Think outside the box
        - Include both practical and creative suggestions
        
        ## FORMATTING
        - Use Markdown in your answers
        - Use headers (##, ###) for structure when appropriate
        - Use bullet points and numbered lists for clarity
        - Use **bold** for emphasis
        - Keep responses focused and well-organized
        
        ## LANGUAGE
        - Respond in the same language as the user's question
        - Match the tone of the question (formal/casual)
        
        ## CRITICAL RULES
        1. ALWAYS respond with valid JSON: {"type":"answer","answer":"..."}
        2. Do NOT wrap response in ```json blocks
        3. Do NOT ask clarifying questions - just answer directly
        4. The "type" field must always be "answer"
        5. Put your full response in the "answer" field
    """.trimIndent()
    }
}