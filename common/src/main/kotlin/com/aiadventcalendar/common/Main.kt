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
        
        post("/chat") {
            try {
                val request = call.receive<ChatRequest>()
                val answer = agentService.getAnswer(request.prompt)
                call.respond(HttpStatusCode.OK, ChatResponse(answer = answer))
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

@Serializable
data class ChatRequest(val prompt: String)

@Serializable
data class ChatResponse(val answer: String)

@Serializable
data class ErrorResponse(val error: String?)

