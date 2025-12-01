package com.aiadventcalendar

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val apiKey: String = System.getenv("OPENAI_API_KEY")
        ?: throw IllegalStateException("OPENAI_API_KEY environment variable is not set.")

    println("Way to exit: send empty line/exit/quit")
    println("Agent: How can i help you?")
    println()
    while (true) {
        print("You: ")
        val userInput: String? = readLine()
        if (userInput.isNullOrBlank() || userInput.lowercase() == "exit" || userInput.lowercase() == "quit") {
            println("Goodbye!")
            break
        }

        val agent = getAgent(apiKey)
        val response: String =
            agent.run("My mom doesn't know the unswer on this question ${userInput}, can you unswer?")
        println("Agent: $response")
        println()
        agent.close()
    }
}


private fun getAgent(apiKey: String): AIAgent<String, String> {
    return AIAgent(
        promptExecutor = simpleOpenAIExecutor(apiKey),
        llmModel = OpenAIModels.Chat.GPT4o,
        systemPrompt = "You are usefully ai agent that help people not to die in this wierd world! They have created you, be serious with them, but try to add some joke to the end of answers, would be great if you add joke through the separator to user could understand where is a joke. Answer always in Russian, also in case question is on another language"
    )
}

