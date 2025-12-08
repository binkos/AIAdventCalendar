package com.aiadventcalendar.common

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

fun main() {
    val apiKey: String = System.getenv("OPENAI_API_KEY")
        ?: throw IllegalStateException("OPENAI_API_KEY environment variable is not set.")
    
    val port: Int = System.getenv("SERVER_PORT")?.toIntOrNull() ?: 8080
    
    embeddedServer(Netty, port = port) {
        configureApplication(apiKey)
    }.start(wait = true)
}

fun Application.configureApplication(apiKey: String) {
    install(ContentNegotiation) {
        json()
    }
    
    val agentService = AgentService(apiKey)
    
    routing {
        get("/health") {
            call.respond(HttpStatusCode.OK, HealthResponse(status = "ok"))
        }
        
        /**
         * Conversational endpoint that processes messages with history support.
         * Returns typed JSON responses: required_questions, question, or answer.
         * 
         * Accepts temperature parameter (0.0-1.5) to control LLM creativity:
         * - 0.0: Precise, deterministic outputs
         * - 0.7: Balanced (default)
         * - 1.2+: Creative, diverse outputs
         */
        post("/conversation") {
            try {
                val request = call.receive<ConversationRequest>()
                val historyMessages = request.history.map { 
                    HistoryMessage(role = it.role, content = it.content) 
                }
                val temperature = request.temperature.coerceIn(0.0, 2.0)
                val response = agentService.processMessage(
                    userMessage = request.message,
                    historyMessages = historyMessages,
                    temperature = temperature
                )
                call.respond(HttpStatusCode.OK, ConversationResponse(response = response))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(error = e.message ?: "Unknown error")
                )
            }
        }
    }
}

@Serializable
data class HealthResponse(val status: String)

/**
 * Request for the conversational endpoint.
 */
@Serializable
data class ConversationRequest(
    val message: String,
    val history: List<ConversationHistoryItem> = emptyList(),
    val temperature: Double = 0.7
)

/**
 * History item for conversation tracking.
 */
@Serializable
data class ConversationHistoryItem(
    val role: String,  // "user" or "assistant"
    val content: String
)

/**
 * Response from the conversational endpoint.
 * Contains raw JSON response with type field.
 */
@Serializable
data class ConversationResponse(val response: String)

@Serializable
data class ErrorResponse(val error: String?)

