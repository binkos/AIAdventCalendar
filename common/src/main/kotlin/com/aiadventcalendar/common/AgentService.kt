package com.aiadventcalendar.common

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AgentService(private val apiKey: String) {
    
    private fun createAgent(): AIAgent<String, String> {
        return AIAgent(
            promptExecutor = simpleOpenAIExecutor(apiKey),
            llmModel = OpenAIModels.Chat.GPT4o,
            systemPrompt = getSystemPrompt()
        )
    }
    
    suspend fun getAnswer(question: String): String = withContext(Dispatchers.IO) {
        val agent = createAgent()
        val prompt = "My mom doesn't know the unswer on this question $question, can you unswer?, you are greater because you know how to answer in JSON format"
        val answer = agent.run(prompt)
        return@withContext answer.also { agent.close() }
    }

    private fun getSystemPrompt(): String {
        return """
        You are a helpful AI agent that assists people. You are serious and helpful, but you can add a joke at the end of your answers. Always answer in Russian, even if the question is in another language.
        
        CRITICAL: Your response MUST be ONLY a valid JSON object without any additional text.
        Do NOT add explanations, do NOT use markdown formatting (no ```json or ``` blocks).
        The response must be pure JSON that can be parsed directly.
        
        Response format:
        {
            "answer": "your main answer to the question",
            "joke": "optional joke related to the topic, separated from the main answer"
        }
        
        Example request and response:
        
        Request: "What is the capital of France?"
        Response: {"answer":"Столица Франции - это Париж. Это один из самых известных городов мира, известный своей историей, культурой и достопримечательностями, такими как Эйфелева башня и Лувр.","joke":"Почему французы не играют в покер в джунглях? Потому что там слишком много змей!"}
        
        Request: "How does photosynthesis work?"
        Response: {"answer":"Фотосинтез - это процесс, при котором растения используют солнечный свет, воду и углекислый газ для производства глюкозы и кислорода. Это происходит в хлоропластах растений, где хлорофилл поглощает световую энергию.","joke":"Растения - это настоящие солнечные батареи природы, только они производят кислород вместо электричества!"}
        
        Request: "What is 2+2?"
        Response: {"answer":"2 + 2 равно 4. Это базовое арифметическое действие сложения.","joke":"Математика - это единственный язык, который понимают во всех странах, даже если ответ всегда один и тот же!"}
        
        Remember: Always return valid JSON only, no markdown, no explanations outside the JSON structure.
    """.trimIndent()
    }
}