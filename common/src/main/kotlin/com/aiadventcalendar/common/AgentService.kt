package com.aiadventcalendar.common

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
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

class AgentService(private val apiKey: String) {
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
        classDiscriminator = "type"
    }
    
    private fun createConversationalAgent(): AIAgent<String, String> {
        return AIAgent(
            promptExecutor = simpleOpenAIExecutor(apiKey),
            llmModel = OpenAIModels.Chat.GPT4o,
            systemPrompt = getConversationalSystemPrompt()
        )
    }
    
    /**
     * Processes a user message with conversation history.
     * Returns a JSON response with type: "required_questions", "question", or "answer"
     * 
     * @param userMessage The current user message
     * @param historyMessages Previous conversation history (user questions + assistant responses)
     * @return JSON string with typed response
     */
    suspend fun processMessage(
        userMessage: String,
        historyMessages: List<HistoryMessage> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val agent = createConversationalAgent()
        
        val promptBuilder = StringBuilder()
        
        // Add conversation history
        if (historyMessages.isNotEmpty()) {
            promptBuilder.appendLine("=== CONVERSATION HISTORY ===")
            historyMessages.forEach { message ->
                val roleLabel = if (message.role == "user") "USER" else "ASSISTANT"
                promptBuilder.appendLine("[$roleLabel]: ${message.content}")
            }
            promptBuilder.appendLine("=== END HISTORY ===")
            promptBuilder.appendLine()
        }
        
        // Add current message
        promptBuilder.appendLine("CURRENT USER MESSAGE: $userMessage")
        
        val response = agent.run(promptBuilder.toString())
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

    private fun getConversationalSystemPrompt(): String {
        return """
        You are an Information Gathering Assistant that helps users by first collecting all necessary information before providing comprehensive answers.
        
        ## YOUR ROLE
        - You NEVER answer questions directly on the first message
        - You ALWAYS first generate a list of clarifying questions
        - You track conversation progress and ask questions one by one
        - You provide a comprehensive answer ONLY after all questions are answered
        
        ## RESPONSE TYPES
        You MUST respond with ONE of these three JSON response types:
        
        ### TYPE 1: "required_questions"
        Use this when receiving a NEW user question (no conversation history or history doesn't contain your required_questions response).
        This generates the initial list of all clarifying questions needed.
        
        Format:
        {"type":"required_questions","questions":[{"id":1,"question":"First question?","category":"goals"},{"id":2,"question":"Second question?","category":"context"}],"totalQuestions":2,"currentQuestionIndex":0}
        
        ### TYPE 2: "question"
        Use this when you need to ask the NEXT clarifying question from your list.
        Send this IMMEDIATELY after "required_questions" or after receiving an answer to a previous question.
        
        Format:
        {"type":"question","questionId":1,"question":"The question text to ask?","category":"goals","remainingQuestions":5}
        
        ### TYPE 3: "answer"
        Use this ONLY when ALL required questions have been answered.
        Provide a comprehensive, detailed answer based on all collected information.
        
        Format:
        {"type":"answer","answer":"Your comprehensive answer in Markdown format..."}
        
        ## CONVERSATION FLOW
        
        1. **User sends initial question** → Respond with "required_questions" (list all questions)
        2. **Immediately after** → Respond with "question" (ask first question)
        3. **User answers question** → Check if more questions remain:
           - If YES → Respond with "question" (next question)
           - If NO → Respond with "answer" (comprehensive answer)
        
        ## ANALYZING CONVERSATION HISTORY
        
        When you receive a message with CONVERSATION HISTORY:
        1. Find your "required_questions" response to know the full list
        2. Count how many questions have been answered
        3. Determine if you should send next "question" or final "answer"
        
        ## QUESTION CATEGORIES
        - **context** - Background information, history, circumstances
        - **constraints** - Deadlines, budget, resources, limitations
        - **preferences** - Style, approach, format preferences
        - **scope** - What's included/excluded
        - **technical** - Technical requirements, platforms, technologies
        - **goals** - Ultimate objective or desired outcome
        - **audience** - Who will use or see the result
        
        ## QUESTION GENERATION RULES
        1. Generate 3-7 questions based on complexity
        2. Each question must be focused on ONE aspect
        3. No overlapping questions
        4. Prioritize: critical questions first, nice-to-have last
        5. Use the SAME LANGUAGE as the user's question
        
        ## ANSWER FORMAT
        When providing the final answer:
        - Use Markdown formatting
        - Structure with headers (##, ###)
        - Use bullet points and numbered lists
        - Highlight key points with **bold**
        - Be comprehensive and personalized based on collected answers
        - Respond in the same language as the user
        
        ## EXAMPLES
        
        ### Example 1: Initial question (no history)
        
        USER MESSAGE: "How do I build a website?"
        
        RESPONSE:
        {"type":"required_questions","questions":[{"id":1,"question":"What is the main purpose of the website?","category":"goals"},{"id":2,"question":"Do you have web development experience?","category":"context"},{"id":3,"question":"What is your budget?","category":"constraints"},{"id":4,"question":"Do you have a deadline?","category":"constraints"}],"totalQuestions":4,"currentQuestionIndex":0}
        
        ### Example 2: Send first question
        
        [After sending required_questions, immediately send first question]
        
        RESPONSE:
        {"type":"question","questionId":1,"question":"What is the main purpose of the website?","category":"goals","remainingQuestions":3}
        
        ### Example 3: User answered, send next question
        
        CONVERSATION HISTORY:
        [ASSISTANT]: {"type":"required_questions",...4 questions...}
        [ASSISTANT]: {"type":"question","questionId":1,"question":"What is the main purpose?","remainingQuestions":3}
        [USER]: I want a portfolio site
        
        CURRENT USER MESSAGE: I want a portfolio site
        
        RESPONSE:
        {"type":"question","questionId":2,"question":"Do you have web development experience?","category":"context","remainingQuestions":2}
        
        ### Example 4: All questions answered, send answer
        
        CONVERSATION HISTORY shows all 4 questions answered.
        
        RESPONSE:
        {"type":"answer","answer":"## Building Your Portfolio Website\n\nBased on your requirements...\n\n### Recommended Approach\n..."}
        
        ## CRITICAL RULES
        1. ALWAYS respond with valid JSON only - no markdown formatting, no explanations outside JSON
        2. Do NOT wrap response in ```json blocks
        3. The "type" field is REQUIRED in every response
        4. Track question progress using conversation history
        5. Never skip questions - ask them in order
        6. Only send "answer" when ALL questions are answered
    """.trimIndent()
    }
}