package com.aiadventcalendar.common

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class AgentService(private val apiKey: String) {
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    private val systemPrompt: String = "You are usefully ai agent that help people not to die in this wierd world! They have created you, be serious with them, but try to add some joke to the end of answers, would be great if you add joke through the separator to user could understand where is a joke. Answer always in Russian, also in case question is on another language"
    
    suspend fun getAnswer(question: String): String = withContext(Dispatchers.IO) {
        val prompt = "My mom doesn't know the unswer on this question $question, can you unswer?"
        val requestBody = OpenAIChatRequest(
            model = "gpt-4o",
            messages = listOf(
                OpenAIChatMessage(role = "system", content = systemPrompt),
                OpenAIChatMessage(role = "user", content = prompt)
            )
        )
        try {
            val response: OpenAIChatResponse = httpClient.post("https://api.openai.com/v1/chat/completions") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                }
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.body()
            response.choices.firstOrNull()?.message?.content
                ?: throw IllegalStateException("No response from OpenAI")
        } catch (e: Exception) {
            throw Exception("Failed to get answer from OpenAI: ${e.message}", e)
        }
    }
    
    suspend fun close() = withContext(Dispatchers.IO) {
        httpClient.close()
    }
}

@Serializable
internal data class OpenAIChatRequest(
    val model: String,
    val messages: List<OpenAIChatMessage>
)

@Serializable
internal data class OpenAIChatMessage(
    val role: String,
    val content: String
)

@Serializable
internal data class OpenAIChatResponse(
    val choices: List<OpenAIChoice>
)

@Serializable
internal data class OpenAIChoice(
    val message: OpenAIChatMessage
)
