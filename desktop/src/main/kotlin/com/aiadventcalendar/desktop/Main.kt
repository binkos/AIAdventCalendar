package com.aiadventcalendar.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

enum class AppScreen {
    AGENT_INPUT,
    CHAT_VIEW
}

fun main() = application {
    val backendUrl: String = System.getenv("BACKEND_URL") ?: "http://localhost:8080"
    val backendClient = remember { BackendClient(backendUrl) }

    Window(
        onCloseRequest = {
            backendClient.close()
            exitApplication()
        },
        title = "AI Advent Calendar"
    ) {
        ChatApp(backendClient = backendClient)
    }
}

@Composable
fun ChatApp(backendClient: BackendClient) {
    var currentScreen by remember { mutableStateOf(AppScreen.AGENT_INPUT) }
    var agentId by remember { mutableStateOf("") }
    var inputAgentId by remember { mutableStateOf("") }
    var chats by remember { mutableStateOf<List<ChatSummary>>(emptyList()) }
    var currentChatId by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf<Map<String, List<ChatMessage>>>(emptyMap()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Load messages when chat is selected
    LaunchedEffect(currentChatId) {
        currentChatId?.let { chatId ->
            if (!messages.containsKey(chatId)) {
                coroutineScope.launch {
                    try {
                        val chatResponse = backendClient.getChat(agentId, chatId)
                        messages = messages + (chatId to chatResponse.chat.messages.map { msg ->
                            ChatMessage(
                                text = msg.content,
                                isUser = msg.role == "user"
                            )
                        })
                    } catch (e: Exception) {
                        // Error loading chat
                    }
                }
            }
        }
    }

    LaunchedEffect(currentChatId, messages) {
        currentChatId?.let { chatId ->
            val chatMessages = messages[chatId] ?: emptyList()
            if (chatMessages.isNotEmpty()) {
                listState.animateScrollToItem(chatMessages.size - 1)
            }
        }
    }

    MaterialTheme {
        when (currentScreen) {
            AppScreen.AGENT_INPUT -> {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                    AgentInputScreen(
                        agentId = inputAgentId,
                        onAgentIdChange = { inputAgentId = it },
                        onAgentIdSubmit = {
                            if (inputAgentId.isNotBlank()) {
                                coroutineScope.launch {
                                    try {
                                        isLoading = true
                                        val agentInfo = backendClient.getAgent(inputAgentId)
                                        agentId = inputAgentId
                                        chats = agentInfo.chats
                                        currentScreen = AppScreen.CHAT_VIEW
                                        isLoading = false
                                    } catch (e: Exception) {
                                        isLoading = false
                                        // Show error - for now just keep on input screen
                                    }
                                }
                            }
                        },
                        isLoading = isLoading
                    )
                }
            }
            
            AppScreen.CHAT_VIEW -> {
                SplitLayoutScreen(
                    agentId = agentId,
                    chats = chats,
                    currentChatId = currentChatId,
                    messages = messages[currentChatId] ?: emptyList(),
                inputText = inputText,
                onInputChange = { inputText = it },
                    onNewChat = {
                        coroutineScope.launch {
                            try {
                                isLoading = true
                                val newChat = backendClient.createChat(agentId)
                                chats = backendClient.getChatHistory(agentId).chats
                                currentChatId = newChat.chatId
                                messages = messages + (newChat.chatId to emptyList())
                                isLoading = false
                            } catch (e: Exception) {
                                isLoading = false
                            }
                        }
                    },
                    onChatSelected = { chatId ->
                        currentChatId = chatId
                    },
                    onSend = {
                        currentChatId?.let { chatId ->
                            if (inputText.isNotBlank() && !isLoading) {
                                coroutineScope.launch {
                                    val text = inputText
                                    inputText = ""
                                    val currentMessages = messages[chatId] ?: emptyList()
                                    messages = messages + (chatId to (currentMessages + ChatMessage(text = text, isUser = true)))
                                    isLoading = true
                                    
                                    try {
                                        val response = backendClient.sendMessage(
                                            agentId = agentId,
                                            chatId = chatId,
                                            message = text,
                                            temperature = 0.7
                                        )
                                        
                                        val parsed = backendClient.parseResponse(response)
                                        val answerText = when (parsed) {
                                            is AgentResponse.Answer -> parsed.answer
                                            else -> response
                                        }
                                        
                                        val updatedMessages = messages[chatId] ?: emptyList()
                                        messages = messages + (chatId to (updatedMessages + ChatMessage(text = answerText, isUser = false)))
                                        
                                        // Refresh chat history
                                        chats = backendClient.getChatHistory(agentId).chats
                                        
                                        // Reload full chat to sync with server
                                        val chatResponse = backendClient.getChat(agentId, chatId)
                                        messages = messages + (chatId to chatResponse.chat.messages.map { msg ->
                                            ChatMessage(
                                                text = msg.content,
                                                isUser = msg.role == "user"
                                            )
                                        })
                                    } catch (e: Exception) {
                                        val errorMessages = messages[chatId] ?: emptyList()
                                        messages = messages + (chatId to (errorMessages + ChatMessage(text = "Error: ${e.message}", isUser = false)))
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        }
                    },
                    onBack = {
                        currentScreen = AppScreen.AGENT_INPUT
                        agentId = ""
                        chats = emptyList()
                        currentChatId = null
                        messages = emptyMap()
                    },
                isLoading = isLoading,
                    listState = listState
                )
            }
        }
    }
}

@Composable
fun AgentInputScreen(
    agentId: String,
    onAgentIdChange: (String) -> Unit,
    onAgentIdSubmit: () -> Unit,
    isLoading: Boolean
    ) {
        Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "AI Advent Calendar",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
                )
        
                Text(
            text = "Enter Agent ID",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            OutlinedTextField(
                value = agentId,
                onValueChange = onAgentIdChange,
                label = { Text("Agent ID") },
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.key == Key.Enter && 
                            keyEvent.type == KeyEventType.KeyDown && 
                            !keyEvent.isShiftPressed
                        ) {
                            if (agentId.isNotBlank() && !isLoading) {
                                onAgentIdSubmit()
                                true
                            } else {
                                true
                            }
                        } else {
                            false
                        }
                    },
                enabled = !isLoading,
                singleLine = true
            )
            
            Button(
                onClick = onAgentIdSubmit,
                enabled = !isLoading && agentId.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Continue")
                }
            }
        }
    }
}

@Composable
fun SplitLayoutScreen(
    agentId: String,
    chats: List<ChatSummary>,
    currentChatId: String?,
    messages: List<ChatMessage>,
    inputText: String,
    onInputChange: (String) -> Unit,
    onNewChat: () -> Unit,
    onChatSelected: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
    isLoading: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    Row(
        modifier = Modifier.fillMaxSize()
    ) {
        // Left Sidebar - Chat List
        Column(
            modifier = Modifier
                .widthIn(min = 250.dp, max = 350.dp)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Sidebar Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Agent: $agentId",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onBack,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text("←")
                }
            }
            
            Button(
                onClick = onNewChat,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("+ New Chat")
            }
            
            // Chat List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (chats.isEmpty()) {
                    item {
            Text(
                            text = "No chats yet.\nClick 'New Chat' to start.",
                            modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(chats) { chat ->
                        ChatListItem(
                            chat = chat,
                            isSelected = chat.id == currentChatId,
                            onClick = { onChatSelected(chat.id) }
                        )
                    }
                }
            }
        }
        
        // Right Side - Current Chat View
        if (currentChatId != null) {
            ChatViewScreen(
                agentId = agentId,
                chatId = currentChatId,
                messages = messages,
                inputText = inputText,
                onInputChange = onInputChange,
                onSend = onSend,
                isLoading = isLoading,
                listState = listState,
                modifier = Modifier.weight(1f)
            )
        } else {
            // Empty state when no chat selected
        Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                    text = "Select a chat from the sidebar or create a new one",
                    style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ChatListItem(chat: ChatSummary, isSelected: Boolean, onClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(chat.updatedAt * 1000))
    
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = dateStr,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) contentColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = chat.lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                maxLines = 2
            )
        }
    }
}

@Composable
fun ChatViewScreen(
    agentId: String,
    chatId: String,
    messages: List<ChatMessage>,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Chat Header
        Text(
            text = "Chat",
            style = MaterialTheme.typography.titleLarge
        )
        
        // Messages Area
            LazyColumn(
                state = listState,
            modifier = Modifier
                .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { message ->
                    ChatBubble(message = message)
                }

                if (isLoading) {
                    item {
                    ChatBubble(message = ChatMessage(text = "Thinking...", isUser = false))
                }
            }
        }

        // Input Area
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                label = { Text("Type your message...") },
                        modifier = Modifier
                            .weight(1f)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.key == Key.Enter && 
                                    keyEvent.type == KeyEventType.KeyDown && 
                                    !keyEvent.isShiftPressed
                                ) {
                                    if (inputText.isNotBlank() && !isLoading) {
                                        onSend()
                                true
                                    } else {
                                true
                                    }
                                } else {
                            false
                                }
                            },
                        enabled = !isLoading,
                        singleLine = false,
                        maxLines = 3
                    )

            Button(
                        onClick = onSend,
                enabled = !isLoading && inputText.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Send")
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val containerColor = if (message.isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    
    val textColor = if (message.isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 500.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            SelectionContainer {
                    Text(
                        text = parseMarkdown(message.text, textColor),
                    modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
            }
        }
    }
}

fun parseMarkdown(text: String, baseColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var index = 0
        val length = text.length

        while (index < length) {
            index = when {
                matchesDelimiter(text, index, "***") || matchesDelimiter(text, index, "___") ->
                    appendStyledText(text, index, 3, boldItalicStyle(baseColor))

                matchesDelimiter(text, index, "**") || matchesDelimiter(text, index, "__") ->
                    appendStyledText(text, index, 2, boldStyle(baseColor))

                matchesDelimiter(text, index, "~~") ->
                    appendStyledText(text, index, 2, underlineStyle(baseColor))

                text[index] == '*' || text[index] == '_' ->
                    handleSingleDelimiter(text, index, baseColor)

                else -> {
                    withStyle(SpanStyle(color = baseColor)) { append(text[index]) }
                    index + 1
                }
            }
        }
    }
}

private fun matchesDelimiter(text: String, index: Int, delimiter: String): Boolean {
    return index + delimiter.length <= text.length &&
            text.substring(index, index + delimiter.length) == delimiter
}

private fun boldStyle(color: Color) = SpanStyle(fontWeight = FontWeight.Bold, color = color)
private fun italicStyle(color: Color) = SpanStyle(fontStyle = FontStyle.Italic, color = color)
private fun boldItalicStyle(color: Color) = SpanStyle(
    fontWeight = FontWeight.Bold,
    fontStyle = FontStyle.Italic,
    color = color
)
private fun underlineStyle(color: Color) = SpanStyle(
    textDecoration = TextDecoration.Underline,
    color = color
)

private fun AnnotatedString.Builder.appendStyledText(
    text: String,
    startIndex: Int,
    delimiterLength: Int,
    style: SpanStyle
): Int {
    val delimiter = text.substring(startIndex, startIndex + delimiterLength)
    val endIndex = text.indexOf(delimiter, startIndex + delimiterLength)

    return if (endIndex != -1) {
        withStyle(style) {
            append(text.substring(startIndex + delimiterLength, endIndex))
        }
        endIndex + delimiterLength
    } else {
        withStyle(SpanStyle(color = style.color)) { append(text[startIndex]) }
        startIndex + 1
    }
}

private fun AnnotatedString.Builder.handleSingleDelimiter(
    text: String,
    index: Int,
    baseColor: Color
): Int {
    val delimiter = text[index]

    if (index + 1 < text.length && text[index + 1] == delimiter) {
        withStyle(SpanStyle(color = baseColor)) { append(text[index]) }
        return index + 1
    }

    val endIndex = text.indexOf(delimiter, index + 1)
    return if (endIndex != -1 && endIndex > index + 1) {
        withStyle(italicStyle(baseColor)) {
            append(text.substring(index + 1, endIndex))
        }
        endIndex + 1
    } else {
        withStyle(SpanStyle(color = baseColor)) { append(text[index]) }
        index + 1
    }
}
