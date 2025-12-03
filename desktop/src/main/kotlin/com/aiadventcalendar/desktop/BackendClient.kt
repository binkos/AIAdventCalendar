package com.aiadventcalendar.desktop

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class BackendClient(private val baseUrl: String) {
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    suspend fun getAnswer(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val request = ChatRequest(prompt = prompt)
            val response: HttpResponse = httpClient.post("$baseUrl/chat") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (response.status == HttpStatusCode.OK) {
                val chatResponse: ChatResponse = response.body()
                chatResponse.answer
            } else {
                val errorResponse: ErrorResponse = response.body()
                throw Exception(errorResponse.error ?: "Unknown error from backend")
            }
        } catch (e: Exception) {
            throw Exception("Failed to get answer from backend: ${e.message}", e)
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
private data class ChatRequest(val prompt: String)

@Serializable
private data class ChatResponse(val answer: String)

@Serializable
private data class ErrorResponse(val error: String?)

@Serializable
private data class HealthResponse(val status: String)

