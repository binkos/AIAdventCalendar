package com.aiadventcalendar.common

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.measureTimedValue

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

/**
 * Response type: comparison_answer - dual LLM comparison response
 */
@Serializable
@SerialName("comparison_answer")
data class ComparisonAnswerResponse(
    val type: String = "comparison_answer",
    val openaiResponse: LlmResponseResult,
    val arceeAiResponse: LlmResponseResult,
    val comparisonAnalysis: String
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

/**
 * Utility class for estimating token usage.
 */
class TokenEstimator {
    companion object {
        private const val AVERAGE_CHARS_PER_TOKEN = 4.0
        private const val AVERAGE_WORDS_PER_TOKEN = 0.75
    }
    
    /**
     * Estimates token count for a given text using character and word-based heuristics.
     */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        
        val charEstimate = text.length / AVERAGE_CHARS_PER_TOKEN
        val wordCount = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        val wordEstimate = wordCount / AVERAGE_WORDS_PER_TOKEN
        
        return ((charEstimate + wordEstimate) / 2).toInt().coerceAtLeast(1)
    }
    
    /**
     * Estimates full token usage including input and output tokens.
     */
    fun estimateTokenUsage(prompt: String, response: String): TokenUsage {
        val inputTokens = estimateTokens(prompt)
        val outputTokens = estimateTokens(response)
        val totalTokens = inputTokens + outputTokens
        
        return TokenUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens
        )
    }
}

class AgentService(private val apiKey: String) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        classDiscriminator = "type"
    }
    
    private val tokenEstimator = TokenEstimator()

    private fun createOpenAIAgent(temperature: Double): AIAgent<String, String> {
        return AIAgent(
            promptExecutor = simpleOpenRouterExecutor(apiKey),
            llmModel = LLModel(
                provider = LLMProvider.OpenRouter,
                id = "openai/gpt-3.5-turbo",
                capabilities = listOf(
                    LLMCapability.Temperature,
                    LLMCapability.Speculation,
                    LLMCapability.Tools,
                    LLMCapability.Completion,
                ),
                contextLength = 16_385,
            ),
            systemPrompt = getSpaceProfessorSystemPrompt(),
            temperature = temperature
        )
    }
    
    private fun createArceeAgent(temperature: Double): AIAgent<String, String> {
        return AIAgent(
            promptExecutor = simpleOpenRouterExecutor(apiKey),
            llmModel = LLModel(
                provider = LLMProvider.OpenRouter,
                id = "mistralai/devstral-2512:free",
                capabilities = listOf(),
                contextLength = 8_200,
            ),
            systemPrompt = getSpaceProfessorSystemPrompt(),
            temperature = temperature
        )
    }
    
    private fun createAnalysisAgent(temperature: Double): AIAgent<String, String> {
        return AIAgent(
            promptExecutor = simpleOpenRouterExecutor(apiKey),
            llmModel = LLModel(
                provider = LLMProvider.OpenRouter,
                id = "openai/gpt-3.5-turbo",
                capabilities = listOf(
                    LLMCapability.Temperature,
                    LLMCapability.Speculation,
                    LLMCapability.Tools,
                    LLMCapability.Completion,
                ),
                contextLength = 16_385,
            ),
            systemPrompt = getComparisonAnalysisSystemPrompt(),
            temperature = temperature
        )
    }

    /**
     * Generates a comparison analysis of two LLM responses.
     */
    private suspend fun generateComparisonAnalysis(
        userMessage: String,
        openaiResult: LlmResponseResult,
        arceeAiResult: LlmResponseResult,
        temperature: Double
    ): String = withContext(Dispatchers.IO) {
        val analysisPrompt = """
            Please analyze and compare the following two LLM responses to the same question.
            
            Original Question: "$userMessage"
            
            ## OpenAI GPT-3.5-turbo Response:
            Response: ${openaiResult.response}
            Execution Time: ${openaiResult.executionTimeMs}ms
            Token Usage: ${openaiResult.tokenUsage.inputTokens} input + ${openaiResult.tokenUsage.outputTokens} output = ${openaiResult.tokenUsage.totalTokens} total tokens
            Status: ${if (openaiResult.isSuccess) "Success" else "Failed: ${openaiResult.errorMessage}"}
            
            ## Arcee AI Response:
            Response: ${arceeAiResult.response}
            Execution Time: ${arceeAiResult.executionTimeMs}ms
            Token Usage: ${arceeAiResult.tokenUsage.inputTokens} input + ${arceeAiResult.tokenUsage.outputTokens} output = ${arceeAiResult.tokenUsage.totalTokens} total tokens
            Status: ${if (arceeAiResult.isSuccess) "Success" else "Failed: ${arceeAiResult.errorMessage}"}
            
            Please provide a comprehensive comparison analyzing:
            1. Token efficiency (which model used fewer tokens)
            2. Response speed (which model responded faster)
            3. Answer quality (accuracy, completeness, clarity, relevance)
            4. Overall assessment and recommendation
        """.trimIndent()
        
        val agent = createAnalysisAgent(temperature)
        try {
            val analysis = agent.run(analysisPrompt)
            agent.close()
            return@withContext analysis
        } catch (e: Exception) {
            agent.close()
            return@withContext "Analysis generation failed: ${e.message}"
        }
    }
    
    /**
     * Processes a user message and returns a comparison of two LLM responses.
     *
     * @param userMessage The user's message/question
     * @param historyMessages Previous conversation history (kept for API compatibility)
     * @param temperature LLM temperature (0.0 = precise, 0.7 = balanced, 1.2+ = creative)
     * @return JSON string with type "comparison_answer"
     */
    suspend fun processMessage(
        userMessage: String,
        historyMessages: List<HistoryMessage> = emptyList(),
        temperature: Double = 0.7
    ): String = withContext(Dispatchers.IO) {
        // Execute both LLMs in parallel
        val openaiDeferred = async {
            executeLlmRequest(
                userMessage = userMessage,
                modelName = "OpenAI GPT-3.5-turbo",
                agentFactory = { createOpenAIAgent(temperature) }
            )
        }
        
        val arceeAiDeferred = async {
            executeLlmRequest(
                userMessage = userMessage,
                modelName = "Arcee AI",
                agentFactory = { createArceeAgent(temperature) }
            )
        }
        
        // Wait for both to complete
        val openaiResult = openaiDeferred.await()
        val arceeAiResult = arceeAiDeferred.await()
        
        // Generate comparison analysis
        val comparisonAnalysis = generateComparisonAnalysis(
            userMessage = userMessage,
            openaiResult = openaiResult,
            arceeAiResult = arceeAiResult,
            temperature = temperature
        )
        
        // Create response
        val response = ComparisonAnswerResponse(
            openaiResponse = openaiResult,
            arceeAiResponse = arceeAiResult,
            comparisonAnalysis = comparisonAnalysis
        )
        
        return@withContext json.encodeToString(response)
    }
    
    /**
     * Executes a single LLM request and measures metrics.
     */
    private suspend fun executeLlmRequest(
        userMessage: String,
        modelName: String,
        agentFactory: () -> AIAgent<String, String>
    ): LlmResponseResult = withContext(Dispatchers.IO) {
        try {
            val agent = agentFactory()
            // Include system prompt in token estimation for accuracy
            val systemPrompt = getSpaceProfessorSystemPrompt()
            val fullPrompt = "$systemPrompt\n\nUser: $userMessage"
            
            val timedResult = measureTimedValue {
                agent.run(userMessage)
            }
            
            agent.close()
            
            val response = timedResult.value
            val executionTime = timedResult.duration.inWholeMilliseconds
            
            // Extract answer from JSON response if needed
            val answerText = try {
                val parsed = json.decodeFromString<AnswerResponse>(response)
                parsed.answer
            } catch (e: Exception) {
                response // Fallback to raw response
            }
            
            val tokenUsage = tokenEstimator.estimateTokenUsage(fullPrompt, answerText)
            
            LlmResponseResult(
                modelName = modelName,
                response = answerText,
                executionTimeMs = executionTime,
                tokenUsage = tokenUsage,
                isSuccess = true
            )
        } catch (e: Exception) {
            LlmResponseResult(
                modelName = modelName,
                response = "",
                executionTimeMs = 0,
                tokenUsage = TokenUsage(0, 0, 0),
                isSuccess = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
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
                
                "comparison_answer" -> {
                    val parsed = json.decodeFromString<ComparisonAnswerResponse>(responseJson)
                    AgentResponse.ComparisonAnswer(
                        type = parsed.type,
                        openaiResponse = parsed.openaiResponse,
                        arceeAiResponse = parsed.arceeAiResponse,
                        comparisonAnalysis = parsed.comparisonAnalysis
                    )
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
    
    private fun getComparisonAnalysisSystemPrompt(): String {
        return """
        You are an expert AI analyst specializing in evaluating and comparing Large Language Model (LLM) responses.
        
        ## YOUR ROLE
        - Analyze and compare responses from different LLMs objectively
        - Evaluate performance metrics: token efficiency, response time, and answer quality
        - Provide clear, structured comparisons with actionable insights
        - Maintain an objective, analytical tone
        
        ## YOUR TASK
        When given two LLM responses to the same question, along with their performance metrics, you must:
        1. Compare token usage efficiency (input + output tokens)
        2. Compare response time performance
        3. Evaluate answer quality: accuracy, completeness, clarity, and relevance
        4. Identify strengths and weaknesses of each response
        5. Provide a clear conclusion on which response performs better overall
        
        ## ANALYSIS STRUCTURE
        Structure your analysis as follows:
        
        ### Token Efficiency
        - Compare total token usage
        - Note which model is more efficient
        - Consider cost implications
        
        ### Response Speed
        - Compare execution times
        - Note which model responded faster
        - Consider practical implications
        
        ### Answer Quality
        - Accuracy: How correct and factual is each answer?
        - Completeness: Does each answer fully address the question?
        - Clarity: How well-structured and easy to understand is each answer?
        - Relevance: How well does each answer match the question's intent?
        
        ### Overall Assessment
        - Summarize key differences
        - Identify the better-performing model
        - Explain your reasoning
        
        ## RESPONSE FORMAT
        Provide your analysis as plain text (not JSON). Use clear sections with headers (##, ###) and bullet points for readability.
        
        ## CRITICAL RULES
        1. Be objective and unbiased in your analysis
        2. Support your conclusions with specific examples from the responses
        3. Consider all three dimensions: efficiency, speed, and quality
        4. Provide actionable insights, not just descriptions
        5. Use clear, professional language
        6. Keep the analysis concise but comprehensive
    """.trimIndent()
    }
}