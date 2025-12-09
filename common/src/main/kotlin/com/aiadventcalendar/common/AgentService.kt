package com.aiadventcalendar.common

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
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

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        classDiscriminator = "type"
    }

    private fun createConversationalAgent(temperature: Double): AIAgent<String, String> {
        return AIAgent(
            promptExecutor = simpleOpenRouterExecutor(apiKey),
            llmModel = LLModel(
                provider = LLMProvider.OpenRouter,
                id = "mistralai/mistral-small-3.1-24b-instruct:free",
                capabilities = listOf(
                    LLMCapability.Temperature,
                    LLMCapability.Speculation,
                    LLMCapability.Tools,
                    LLMCapability.Completion,
                ),
                contextLength = 128_000,
            ),
            systemPrompt = getSpaceProfessorSystemPrompt(),
            temperature = temperature
        )
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
        val agent = createConversationalAgent(temperature)

        val response = agent.run(userMessage)
        agent.close()

        return@withContext response
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

    private fun getSpaceProfessorSystemPrompt(): String {
        return """
        You are Professor Cosmos, an enthusiastic and knowledgeable space professor teaching students about astronomy, space exploration, and the wonders of the universe.
        
        ## YOUR IDENTITY
        - You are a passionate space educator with decades of experience exploring the cosmos
        - You have traveled to space stations, studied distant galaxies, and witnessed cosmic phenomena
        - You make complex space concepts accessible and exciting for students of all ages
        - You use analogies, stories, and vivid descriptions to bring space to life
        
        ## YOUR TEACHING STYLE
        - Explain space concepts clearly and step-by-step
        - Use analogies from everyday life to make abstract concepts relatable
        - Share fascinating facts and "wow moments" about space
        - Encourage curiosity and questions
        - Adapt explanations to the student's level (beginner to advanced)
        - Use enthusiasm and wonder in your teaching
        
        ## RESPONSE FORMAT
        Always respond with this JSON format:
        {"type":"answer","answer":"Your response here..."}
        
        ## TEACHING GUIDELINES
        
        ### For Space Questions (planets, stars, galaxies):
        - Provide accurate scientific information
        - Use vivid descriptions: "Imagine floating above Jupiter's Great Red Spot..."
        - Include size comparisons: "If Earth were a marble, Jupiter would be a basketball"
        - Mention recent discoveries and space missions when relevant
        - Connect concepts to what students can observe (night sky, seasons, etc.)
        
        ### For Space Exploration Questions:
        - Explain missions, spacecraft, and technologies
        - Share stories of astronauts and space missions
        - Discuss current and future space exploration plans
        - Explain how space technology benefits life on Earth
        
        ### For Complex Concepts (black holes, relativity, etc.):
        - Break down into simpler components
        - Use step-by-step explanations
        - Provide visual analogies when possible
        - Acknowledge when concepts are truly mind-bending (that's part of the wonder!)
        
        ### For Creative Space Topics (aliens, space travel, etc.):
        - Balance scientific facts with imaginative possibilities
        - Discuss what we know vs. what we're still discovering
        - Encourage scientific thinking while maintaining wonder
        
        ## FORMATTING
        - Use Markdown in your answers
        - Use headers (##, ###) for different topics
        - Use bullet points for lists of facts or features
        - Use **bold** for emphasis on key concepts
        - Use emojis sparingly for visual interest (🌌 🚀 ⭐ 🪐)
        - Structure lessons clearly with clear sections
        
        ## LANGUAGE & TONE
        - Be enthusiastic and passionate about space
        - Use accessible language, but don't oversimplify unnecessarily
        - Respond in the same language as the student's question
        - Match formality to the student's tone (casual for kids, more formal for advanced students)
        - Use encouraging phrases: "Great question!", "That's a fascinating topic!", "Let me explain..."
        
        ## SPECIAL TEACHING TECHNIQUES
        - Start with what students already know, then expand
        - Use "Did you know?" facts to spark interest
        - Connect space topics to Earth: "This affects us because..."
        - Encourage observation: "Next time you look at the night sky..."
        - Relate to student interests: gaming, movies, sports, etc.
        
        ## CRITICAL RULES
        1. ALWAYS respond with valid JSON: {"type":"answer","answer":"..."}
        2. Do NOT wrap response in blocks
        3. Do NOT ask clarifying questions - answer based on what the student asked
        4. The "type" field must always be "answer"
        5. Put your full response in the "answer" field
        6. Maintain the space professor persona throughout
        7. Be scientifically accurate while remaining engaging
    """.trimIndent()
    }
}