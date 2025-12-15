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
import io.ktor.server.routing.route
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
        
        route("/agents/{agentId}") {
            get {
                try {
                    val agentId = call.parameters["agentId"] ?: throw IllegalArgumentException("agentId is required")
                    val agent = agentService.createOrGetAgent(agentId)
                    val chats = agentService.getChatHistory(agent.agentId)
                    val chatSummaries = chats.map { chat ->
                        val lastMessage = chat.messages.lastOrNull()?.content?.take(100) ?: "No messages yet"
                        ChatSummary(
                            id = chat.id,
                            agentId = chat.agentId,
                            lastMessage = lastMessage,
                            updatedAt = chat.updatedAt
                        )
                    }
                    call.respond(HttpStatusCode.OK, AgentInfoResponse(
                        agentId = agentId,
                        chats = chatSummaries
                    ))
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(error = e.message ?: "Unknown error")
                    )
                }
            }
            
            post {
                try {
                    val agentId = call.parameters["agentId"] ?: throw IllegalArgumentException("agentId is required")
                    val agent = agentService.createOrGetAgent(agentId)
                    val chats = agentService.getChatHistory(agent.agentId)
                    val chatSummaries = chats.map { chat ->
                        val lastMessage = chat.messages.lastOrNull()?.content?.take(100) ?: "No messages yet"
                        ChatSummary(
                            id = chat.id,
                            agentId = chat.agentId,
                            lastMessage = lastMessage,
                            updatedAt = chat.updatedAt
                        )
                    }
                    call.respond(HttpStatusCode.OK, AgentInfoResponse(
                        agentId = agentId,
                        chats = chatSummaries
                    ))
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(error = e.message ?: "Unknown error")
                    )
                }
            }
            
            route("/chats") {
                post {
                    try {
                        val agentId = call.parameters["agentId"] ?: throw IllegalArgumentException("agentId is required")
                        call.receive<CreateChatRequest>() // Validate request structure
                        val chat = agentService.createChat(agentId)
                        call.respond(HttpStatusCode.OK, CreateChatResponse(
                            chatId = chat.id,
                            createdAt = chat.createdAt
                        ))
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse(error = e.message ?: "Unknown error")
                        )
                    }
                }
                
                get {
                    try {
                        val agentId = call.parameters["agentId"] ?: throw IllegalArgumentException("agentId is required")
                        val chats = agentService.getChatHistory(agentId)
                        val chatSummaries = chats.map { chat ->
                            val lastMessage = chat.messages.lastOrNull()?.content?.take(100) ?: "No messages yet"
                            ChatSummary(
                                id = chat.id,
                                agentId = chat.agentId,
                                lastMessage = lastMessage,
                                updatedAt = chat.updatedAt
                            )
                        }
                        call.respond(HttpStatusCode.OK, ChatHistoryResponse(chats = chatSummaries))
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse(error = e.message ?: "Unknown error")
                        )
                    }
                }
                
                route("/{chatId}") {
                    get {
                        try {
                            val agentId = call.parameters["agentId"] ?: throw IllegalArgumentException("agentId is required")
                            val chatId = call.parameters["chatId"] ?: throw IllegalArgumentException("chatId is required")

                            println(agentService.getTools())

                            val chat = agentService.getChat(agentId, chatId)
                                ?: throw IllegalStateException("Chat not found")
                            call.respond(HttpStatusCode.OK, ChatResponse(chat = chat))
                        } catch (e: Exception) {
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                ErrorResponse(error = e.message ?: "Unknown error")
                            )
                        }
                    }
                    
                    route("/messages") {
                        post {
                            try {
                                val agentId = call.parameters["agentId"] ?: throw IllegalArgumentException("agentId is required")
                                val chatId = call.parameters["chatId"] ?: throw IllegalArgumentException("chatId is required")
                                val request = call.receive<SendMessageRequest>()
                                val temperature = request.temperature.coerceIn(0.0, 2.0)
                                val response = agentService.processMessage(
                                    agentId = agentId,
                                    chatId = chatId,
                                    userMessage = request.message,
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
            }
        }
    }
}

@Serializable
data class HealthResponse(val status: String)

/**
 * Response containing agent info and chat history.
 */
@Serializable
data class AgentInfoResponse(
    val agentId: String,
    val chats: List<ChatSummary>
)

/**
 * Summary of a chat for listing purposes.
 */
@Serializable
data class ChatSummary(
    val id: String,
    val agentId: String,
    val lastMessage: String,
    val updatedAt: Long
)

/**
 * Request to create a new chat.
 */
@Serializable
data class CreateChatRequest(
    val agentId: String
)

/**
 * Response after creating a new chat.
 */
@Serializable
data class CreateChatResponse(
    val chatId: String,
    val createdAt: Long
)

/**
 * Response containing chat history list.
 */
@Serializable
data class ChatHistoryResponse(
    val chats: List<ChatSummary>
)

/**
 * Response containing full chat data.
 */
@Serializable
data class ChatResponse(
    val chat: Chat
)

/**
 * Request to send a message in a chat.
 */
@Serializable
data class SendMessageRequest(
    val message: String,
    val temperature: Double = 0.7
)

/**
 * Response from the conversational endpoint.
 * Contains raw JSON response with type field.
 */
@Serializable
data class ConversationResponse(val response: String)

@Serializable
data class ErrorResponse(val error: String?)

