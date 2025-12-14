package com.aiadventcalendar.common

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Database helper for SQLite storage of agents, chats, messages, and prompts.
 */
class DatabaseHelper(private val dbPath: String = "agents.db") {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    init {
        createTables()
    }
    
    private fun getConnection(): Connection {
        return DriverManager.getConnection("jdbc:sqlite:$dbPath")
    }
    
    private fun createTables() {
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                // Agents table
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS agents (
                        agent_id TEXT PRIMARY KEY,
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent())
                
                // Chats table
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS chats (
                        chat_id TEXT PRIMARY KEY,
                        agent_id TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY (agent_id) REFERENCES agents(agent_id) ON DELETE CASCADE
                    )
                """.trimIndent())
                
                // Messages table
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS messages (
                        message_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        chat_id TEXT NOT NULL,
                        agent_id TEXT NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY (chat_id) REFERENCES chats(chat_id) ON DELETE CASCADE,
                        FOREIGN KEY (agent_id) REFERENCES agents(agent_id) ON DELETE CASCADE
                    )
                """.trimIndent())
                
                // Prompts table - stores serialized prompt data
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS prompts (
                        agent_id TEXT NOT NULL,
                        chat_id TEXT NOT NULL,
                        prompt_data TEXT NOT NULL,
                        PRIMARY KEY (agent_id, chat_id),
                        FOREIGN KEY (chat_id) REFERENCES chats(chat_id) ON DELETE CASCADE,
                        FOREIGN KEY (agent_id) REFERENCES agents(agent_id) ON DELETE CASCADE
                    )
                """.trimIndent())
                
                // Create indices for better performance
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_chats_agent_id ON chats(agent_id)")
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_messages_chat_id ON messages(chat_id)")
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_messages_agent_id ON messages(agent_id)")
            }
        }
    }
    
    // Agent operations
    fun createOrGetAgent(agentId: String, createdAt: Long): Long {
        getConnection().use { conn ->
            conn.prepareStatement("""
                INSERT OR IGNORE INTO agents (agent_id, created_at) VALUES (?, ?)
            """.trimIndent()).use { stmt ->
                stmt.setString(1, agentId)
                stmt.setLong(2, createdAt)
                stmt.executeUpdate()
            }
            
            conn.prepareStatement("SELECT created_at FROM agents WHERE agent_id = ?").use { stmt ->
                stmt.setString(1, agentId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        return rs.getLong("created_at")
                    }
                }
            }
        }
        return createdAt
    }
    
    fun agentExists(agentId: String): Boolean {
        getConnection().use { conn ->
            conn.prepareStatement("SELECT 1 FROM agents WHERE agent_id = ?").use { stmt ->
                stmt.setString(1, agentId)
                stmt.executeQuery().use { rs ->
                    return rs.next()
                }
            }
        }
    }
    
    // Chat operations
    fun createChat(chatId: String, agentId: String, createdAt: Long, updatedAt: Long) {
        getConnection().use { conn ->
            conn.prepareStatement("""
                INSERT INTO chats (chat_id, agent_id, created_at, updated_at) 
                VALUES (?, ?, ?, ?)
            """.trimIndent()).use { stmt ->
                stmt.setString(1, chatId)
                stmt.setString(2, agentId)
                stmt.setLong(3, createdAt)
                stmt.setLong(4, updatedAt)
                stmt.executeUpdate()
            }
        }
    }
    
    fun getChat(chatId: String): Chat? {
        getConnection().use { conn ->
            conn.prepareStatement("""
                SELECT chat_id, agent_id, created_at, updated_at 
                FROM chats WHERE chat_id = ?
            """.trimIndent()).use { stmt ->
                stmt.setString(1, chatId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        val messages = getMessagesForChat(chatId)
                        return Chat(
                            id = rs.getString("chat_id"),
                            agentId = rs.getString("agent_id"),
                            messages = messages.toMutableList(),
                            createdAt = rs.getLong("created_at"),
                            updatedAt = rs.getLong("updated_at")
                        )
                    }
                }
            }
        }
        return null
    }
    
    fun getChatsForAgent(agentId: String): List<Chat> {
        val chats = mutableListOf<Chat>()
        getConnection().use { conn ->
            conn.prepareStatement("""
                SELECT chat_id, agent_id, created_at, updated_at 
                FROM chats WHERE agent_id = ? 
                ORDER BY updated_at DESC
            """.trimIndent()).use { stmt ->
                stmt.setString(1, agentId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val chatId = rs.getString("chat_id")
                        val messages = getMessagesForChat(chatId)
                        chats.add(
                            Chat(
                                id = chatId,
                                agentId = rs.getString("agent_id"),
                                messages = messages.toMutableList(),
                                createdAt = rs.getLong("created_at"),
                                updatedAt = rs.getLong("updated_at")
                            )
                        )
                    }
                }
            }
        }
        return chats
    }
    
    fun updateChatTimestamp(chatId: String, updatedAt: Long) {
        getConnection().use { conn ->
            conn.prepareStatement("UPDATE chats SET updated_at = ? WHERE chat_id = ?").use { stmt ->
                stmt.setLong(1, updatedAt)
                stmt.setString(2, chatId)
                stmt.executeUpdate()
            }
        }
    }
    
    // Message operations
    fun addMessage(message: HistoryMessage, createdAt: Long) {
        getConnection().use { conn ->
            conn.prepareStatement("""
                INSERT INTO messages (chat_id, agent_id, role, content, created_at) 
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent()).use { stmt ->
                stmt.setString(1, message.chatId)
                stmt.setString(2, message.agentId)
                stmt.setString(3, message.role)
                stmt.setString(4, message.content)
                stmt.setLong(5, createdAt)
                stmt.executeUpdate()
            }
        }
        // Update chat timestamp
        updateChatTimestamp(message.chatId, createdAt)
    }
    
    private fun getMessagesForChat(chatId: String): List<HistoryMessage> {
        val messages = mutableListOf<HistoryMessage>()
        getConnection().use { conn ->
            conn.prepareStatement("""
                SELECT agent_id, chat_id, role, content 
                FROM messages WHERE chat_id = ? 
                ORDER BY created_at ASC
            """.trimIndent()).use { stmt ->
                stmt.setString(1, chatId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        messages.add(
                            HistoryMessage(
                                role = rs.getString("role"),
                                content = rs.getString("content"),
                                agentId = rs.getString("agent_id"),
                                chatId = rs.getString("chat_id")
                            )
                        )
                    }
                }
            }
        }
        return messages
    }
    
    // Prompt operations - store serialized prompt
    fun savePrompt(agentId: String, chatId: String, promptData: String) {
        getConnection().use { conn ->
            conn.prepareStatement("""
                INSERT OR REPLACE INTO prompts (agent_id, chat_id, prompt_data) 
                VALUES (?, ?, ?)
            """.trimIndent()).use { stmt ->
                stmt.setString(1, agentId)
                stmt.setString(2, chatId)
                stmt.setString(3, promptData)
                stmt.executeUpdate()
            }
        }
    }
    
    fun getPromptData(agentId: String, chatId: String): String? {
        getConnection().use { conn ->
            conn.prepareStatement("SELECT prompt_data FROM prompts WHERE agent_id = ? AND chat_id = ?").use { stmt ->
                stmt.setString(1, agentId)
                stmt.setString(2, chatId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        return rs.getString("prompt_data")
                    }
                }
            }
        }
        return null
    }
    
    fun deletePrompt(agentId: String, chatId: String) {
        getConnection().use { conn ->
            conn.prepareStatement("DELETE FROM prompts WHERE agent_id = ? AND chat_id = ?").use { stmt ->
                stmt.setString(1, agentId)
                stmt.setString(2, chatId)
                stmt.executeUpdate()
            }
        }
    }
}

