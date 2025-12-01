package com.aiadventcalendar.common

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AgentService(private val apiKey: String) {
    private val agent: AIAgent<String, String> = createAgent()
    
    private fun createAgent(): AIAgent<String, String> {
        return AIAgent(
            promptExecutor = simpleOpenAIExecutor(apiKey),
            llmModel = OpenAIModels.Chat.GPT4o,
            systemPrompt = "You are usefully ai agent that help people not to die in this wierd world! They have created you, be serious with them, but try to add some joke to the end of answers, would be great if you add joke through the separator to user could understand where is a joke. Answer always in Russian, also in case question is on another language"
        )
    }
    
    suspend fun getAnswer(question: String): String = withContext(Dispatchers.IO) {
        val prompt = "My mom doesn't know the unswer on this question $question, can you unswer?"
        agent.run(prompt)
    }
    
    suspend fun close() = withContext(Dispatchers.IO) {
        agent.close()
    }
}

