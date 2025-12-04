package com.aiadventcalendar.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// ============================================================================
// SECTION 1: DATA MODELS
// ============================================================================

/**
 * Represents a single chat message displayed in the UI.
 *
 * @property text The message content (supports markdown formatting)
 * @property isUser True if the message is from the user, false if from AI
 * @property messageType The type of message for visual styling
 */
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val messageType: MessageType = MessageType.REGULAR
)

/**
 * Types of messages that determine visual styling in the chat.
 */
enum class MessageType {
    REGULAR,       // Standard message
    QUESTION,      // Clarifying question from AI agent
    FINAL_ANSWER,  // Final comprehensive answer from AI
    SYSTEM         // System/info messages
}

/**
 * Manages the state of an ongoing conversation with the AI agent.
 *
 * @property requiredQuestions List of questions the AI needs to ask
 * @property currentQuestionIndex Index of the current question being asked
 * @property isCollectingAnswers True when AI is gathering information
 * @property history Full conversation history for context
 */
data class ConversationState(
    val requiredQuestions: List<QuestionItem> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val isCollectingAnswers: Boolean = false,
    val history: List<HistoryMessage> = emptyList()
)

// ============================================================================
// SECTION 2: THEME CONSTANTS
// ============================================================================

/**
 * Color constants for different message types.
 */
private object MessageColors {
    val questionBackground = Color(0xFF2E7D32)  // Green
    val answerBackground = Color(0xFF1565C0)    // Blue
    val questionText = Color.White
    val answerText = Color.White
}

/**
 * Labels displayed for different message types.
 */
private object MessageLabels {
    const val QUESTION = "📝 Question"
    const val ANSWER = "✨ Answer"
    const val INFO = "ℹ️ Info"
}

// ============================================================================
// SECTION 3: APPLICATION ENTRY POINT
// ============================================================================

/**
 * Main entry point for the desktop application.
 * Initializes the backend client and creates the main window.
 */
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

// ============================================================================
// SECTION 4: MAIN APPLICATION COMPOSABLE
// ============================================================================

/**
 * Main chat application composable that manages the entire chat UI.
 *
 * @param backendClient Client for communicating with the backend API
 */
@Composable
fun ChatApp(backendClient: BackendClient) {
    // UI State
    var inputText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var conversationState by remember { mutableStateOf(ConversationState()) }
    
    // Coroutine scope for async operations
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Auto-scroll to the latest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Callback functions for UI interactions
    val onSendMessage: (String) -> Unit = { text ->
        handleSendMessage(
            text = text,
            isLoading = isLoading,
            coroutineScope = coroutineScope,
            backendClient = backendClient,
            conversationState = conversationState,
            onInputClear = { inputText = "" },
            onMessagesUpdate = { messages = it },
            onLoadingChange = { isLoading = it },
            onStateUpdate = { conversationState = it },
            currentMessages = messages
        )
    }

    val onResetConversation: () -> Unit = {
        messages = emptyList()
        conversationState = ConversationState()
        inputText = ""
    }

    // Main UI Layout
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChatHeader(
                hasMessages = messages.isNotEmpty(),
                onNewChat = onResetConversation
            )

            ChatMessageList(
                messages = messages,
                isLoading = isLoading,
                listState = listState,
                modifier = Modifier.weight(1f)
            )

            ChatInputArea(
                inputText = inputText,
                onInputChange = { inputText = it },
                onSend = { onSendMessage(inputText) },
                isLoading = isLoading,
                isCollectingAnswers = conversationState.isCollectingAnswers
            )
        }
    }
}

// ============================================================================
// SECTION 5: UI COMPONENTS
// ============================================================================

/**
 * Header component with app title and new chat button.
 */
@Composable
private fun ChatHeader(
    hasMessages: Boolean,
    onNewChat: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI Advent Calendar",
            style = MaterialTheme.typography.headlineMedium
        )
        
        if (hasMessages) {
            Button(
                onClick = onNewChat,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("New Chat")
            }
        }
    }
}

/**
 * Scrollable list of chat messages.
 */
@Composable
private fun ChatMessageList(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
            LazyColumn(
                state = listState,
        modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
        // Empty state placeholder
                if (messages.isEmpty() && !isLoading) {
                    item {
                EmptyStateMessage()
            }
        }

        // Chat messages
                items(messages) { message ->
                    ChatBubble(message = message)
                }

        // Loading indicator
                if (isLoading) {
                    item {
                LoadingBubble()
            }
        }
    }
}

/**
 * Placeholder message when chat is empty.
 */
@Composable
private fun EmptyStateMessage() {
    Text(
        text = "Ask me anything! I'll gather some information first to give you the best answer.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp)
    )
}

/**
 * Loading indicator bubble shown while waiting for AI response.
 */
@Composable
private fun LoadingBubble() {
                        ChatBubble(
                            message = ChatMessage(
                                text = "Thinking...",
                                isUser = false
        )
    )
}

/**
 * Input area with text field and send button.
 * Press Enter to send, Shift+Enter for new line.
 */
@Composable
private fun ChatInputArea(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    isCollectingAnswers: Boolean
) {
    val labelText = if (isCollectingAnswers) {
        "Answer the question above..."
    } else {
        "Ask your question..."
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
            onValueChange = onInputChange,
            label = { Text(labelText) },
            modifier = Modifier
                .weight(1f)
                .onPreviewKeyEvent { keyEvent ->
                    // Send on Enter (without Shift), Shift+Enter for new line
                    // onPreviewKeyEvent intercepts BEFORE the text field processes it
                    if (keyEvent.key == Key.Enter && 
                        keyEvent.type == KeyEventType.KeyDown && 
                        !keyEvent.isShiftPressed
                    ) {
                        if (inputText.isNotBlank() && !isLoading) {
                            onSend()
                            true // Event consumed - prevents new line
                        } else {
                            true // Still consume to prevent empty new line
                        }
                    } else {
                        false // Let other key events pass through
                    }
                },
            enabled = !isLoading,
            singleLine = false,
            maxLines = 3
        )

        SendButton(
            onClick = onSend,
            isLoading = isLoading,
            isEnabled = !isLoading && inputText.isNotBlank()
        )
    }
}

/**
 * Send button with loading state.
 */
@Composable
private fun SendButton(
    onClick: () -> Unit,
    isLoading: Boolean,
    isEnabled: Boolean
) {
    Button(
        onClick = onClick,
        enabled = isEnabled
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

/**
 * Single chat message bubble with markdown support.
 */
@Composable
fun ChatBubble(message: ChatMessage) {
    val (containerColor, textColor) = getMessageColors(message)

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
                Column(modifier = Modifier.padding(12.dp)) {
                    MessageTypeLabel(message = message, textColor = textColor)
                    
                    Text(
                        text = parseMarkdown(message.text, textColor),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

/**
 * Label showing the type of message (Question, Answer, Info).
 */
@Composable
private fun MessageTypeLabel(message: ChatMessage, textColor: Color) {
    if (message.isUser || message.messageType == MessageType.REGULAR) return

    val typeLabel = when (message.messageType) {
        MessageType.QUESTION -> MessageLabels.QUESTION
        MessageType.FINAL_ANSWER -> MessageLabels.ANSWER
        MessageType.SYSTEM -> MessageLabels.INFO
        else -> return
    }

            Text(
        text = typeLabel,
        style = MaterialTheme.typography.labelSmall,
        color = textColor.copy(alpha = 0.8f),
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

/**
 * Determines the background and text colors for a message based on its type.
 */
@Composable
private fun getMessageColors(message: ChatMessage): Pair<Color, Color> {
    val containerColor = when {
        message.isUser -> MaterialTheme.colorScheme.primary
        message.messageType == MessageType.QUESTION -> MessageColors.questionBackground
        message.messageType == MessageType.FINAL_ANSWER -> MessageColors.answerBackground
        message.messageType == MessageType.SYSTEM -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.surface
    }

    val textColor = when {
        message.isUser -> MaterialTheme.colorScheme.onPrimary
        message.messageType == MessageType.QUESTION -> MessageColors.questionText
        message.messageType == MessageType.FINAL_ANSWER -> MessageColors.answerText
        message.messageType == MessageType.SYSTEM -> MaterialTheme.colorScheme.onTertiary
        else -> MaterialTheme.colorScheme.onSurface
    }

    return containerColor to textColor
}

// ============================================================================
// SECTION 6: MESSAGE HANDLING LOGIC
// ============================================================================

/**
 * Handles sending a message to the backend and processing the response.
 */
private fun handleSendMessage(
    text: String,
    isLoading: Boolean,
    coroutineScope: CoroutineScope,
    backendClient: BackendClient,
    conversationState: ConversationState,
    onInputClear: () -> Unit,
    onMessagesUpdate: (List<ChatMessage>) -> Unit,
    onLoadingChange: (Boolean) -> Unit,
    onStateUpdate: (ConversationState) -> Unit,
    currentMessages: List<ChatMessage>
) {
    if (text.isBlank() || isLoading) return

    coroutineScope.launch {
        onInputClear()
        onMessagesUpdate(currentMessages + ChatMessage(text = text, isUser = true))
        onLoadingChange(true)

        try {
            // Update history with user message
            val updatedHistory = conversationState.history + HistoryMessage(role = "user", content = text)
            var currentState = conversationState.copy(history = updatedHistory)
            onStateUpdate(currentState)

            // Send message to backend
            val response = backendClient.sendMessage(
                message = text,
                history = currentState.history.dropLast(1)
            )

            // Update history with assistant response
            currentState = currentState.copy(
                history = currentState.history + HistoryMessage(role = "assistant", content = response)
            )
            onStateUpdate(currentState)

            // Process the response
            processAgentResponse(
                responseJson = response,
                userMessage = text,
                backendClient = backendClient,
                currentState = currentState,
                currentMessages = currentMessages + ChatMessage(text = text, isUser = true),
                onMessagesUpdate = onMessagesUpdate,
                onStateUpdate = onStateUpdate
            )

        } catch (e: Exception) {
            onMessagesUpdate(
                currentMessages + ChatMessage(text = text, isUser = true) +
                ChatMessage(text = "Error: ${e.message}", isUser = false)
            )
        } finally {
            onLoadingChange(false)
        }
    }
}

/**
 * Processes the AI agent's response and updates the UI accordingly.
 */
private suspend fun processAgentResponse(
    responseJson: String,
    userMessage: String,
    backendClient: BackendClient,
    currentState: ConversationState,
    currentMessages: List<ChatMessage>,
    onMessagesUpdate: (List<ChatMessage>) -> Unit,
    onStateUpdate: (ConversationState) -> Unit
) {
    when (val parsedResponse = backendClient.parseResponse(responseJson)) {
        is AgentResponse.RequiredQuestions -> {
            handleRequiredQuestionsResponse(
                response = parsedResponse,
                userMessage = userMessage,
                responseJson = responseJson,
                backendClient = backendClient,
                currentState = currentState,
                currentMessages = currentMessages,
                onMessagesUpdate = onMessagesUpdate,
                onStateUpdate = onStateUpdate
            )
        }

        is AgentResponse.Question -> {
            handleQuestionResponse(
                response = parsedResponse,
                currentState = currentState,
                currentMessages = currentMessages,
                onMessagesUpdate = onMessagesUpdate,
                onStateUpdate = onStateUpdate
            )
        }

        is AgentResponse.Answer -> {
            handleAnswerResponse(
                response = parsedResponse,
                currentMessages = currentMessages,
                onMessagesUpdate = onMessagesUpdate,
                onStateUpdate = onStateUpdate
            )
        }

        null -> {
            // Fallback for unparseable response
            onMessagesUpdate(currentMessages + ChatMessage(text = responseJson, isUser = false))
        }
    }
}

/**
 * Handles the initial response containing required questions.
 */
private suspend fun handleRequiredQuestionsResponse(
    response: AgentResponse.RequiredQuestions,
    userMessage: String,
    responseJson: String,
    backendClient: BackendClient,
    currentState: ConversationState,
    currentMessages: List<ChatMessage>,
    onMessagesUpdate: (List<ChatMessage>) -> Unit,
    onStateUpdate: (ConversationState) -> Unit
) {
    // Update state with required questions
    var newState = currentState.copy(
        requiredQuestions = response.questions,
        currentQuestionIndex = 0,
        isCollectingAnswers = true,
        history = currentState.history + listOf(
            HistoryMessage(role = "user", content = userMessage),
            HistoryMessage(role = "assistant", content = responseJson)
        )
    )
    onStateUpdate(newState)

    // Show system message about questions count
    val systemMessage = ChatMessage(
        text = "I need to ask you ${response.totalQuestions} questions to provide a comprehensive answer.",
        isUser = false,
        messageType = MessageType.SYSTEM
    )
    onMessagesUpdate(currentMessages + systemMessage)

    // Request the first question
    val questionResponse = backendClient.sendMessage(
        message = "",
        history = newState.history
    )
    newState = newState.copy(
        history = newState.history + HistoryMessage(role = "assistant", content = questionResponse)
    )
    onStateUpdate(newState)

    // Process the first question
    processAgentResponse(
        responseJson = questionResponse,
        userMessage = "",
        backendClient = backendClient,
        currentState = newState,
        currentMessages = currentMessages + systemMessage,
        onMessagesUpdate = onMessagesUpdate,
        onStateUpdate = onStateUpdate
    )
}

/**
 * Handles a single clarifying question from the AI.
 */
private fun handleQuestionResponse(
    response: AgentResponse.Question,
    currentState: ConversationState,
    currentMessages: List<ChatMessage>,
    onMessagesUpdate: (List<ChatMessage>) -> Unit,
    onStateUpdate: (ConversationState) -> Unit
) {
    val questionMessage = ChatMessage(
        text = "${response.question}\n\n(${response.remainingQuestions} questions remaining)",
        isUser = false,
        messageType = MessageType.QUESTION
    )
    onMessagesUpdate(currentMessages + questionMessage)
    onStateUpdate(currentState.copy(currentQuestionIndex = response.questionId))
}

/**
 * Handles the final comprehensive answer from the AI.
 */
private fun handleAnswerResponse(
    response: AgentResponse.Answer,
    currentMessages: List<ChatMessage>,
    onMessagesUpdate: (List<ChatMessage>) -> Unit,
    onStateUpdate: (ConversationState) -> Unit
) {
    val answerMessage = ChatMessage(
        text = response.answer,
        isUser = false,
        messageType = MessageType.FINAL_ANSWER
    )
    onMessagesUpdate(currentMessages + answerMessage)
    onStateUpdate(ConversationState()) // Reset for new conversation
}

// ============================================================================
// SECTION 7: MARKDOWN PARSER
// ============================================================================

/**
 * Parses markdown text and returns an AnnotatedString with proper styling.
 *
 * Supported syntax:
 * - **bold** or __bold__
 * - *italic* or _italic_
 * - ~~underline~~
 * - ***bold italic*** or ___bold italic___
 *
 * @param text The raw text containing markdown
 * @param baseColor The default text color
 * @return Styled AnnotatedString
 */
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

/** Checks if text at given index starts with the specified delimiter. */
private fun matchesDelimiter(text: String, index: Int, delimiter: String): Boolean {
    return index + delimiter.length <= text.length &&
            text.substring(index, index + delimiter.length) == delimiter
}

/** Creates a bold style with the given color. */
private fun boldStyle(color: Color) = SpanStyle(fontWeight = FontWeight.Bold, color = color)

/** Creates an italic style with the given color. */
private fun italicStyle(color: Color) = SpanStyle(fontStyle = FontStyle.Italic, color = color)

/** Creates a bold + italic style with the given color. */
private fun boldItalicStyle(color: Color) = SpanStyle(
    fontWeight = FontWeight.Bold,
    fontStyle = FontStyle.Italic,
    color = color
)

/** Creates an underline style with the given color. */
private fun underlineStyle(color: Color) = SpanStyle(
    textDecoration = TextDecoration.Underline,
    color = color
)

/**
 * Appends styled text between delimiters.
 * Returns the new index after processing.
 */
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

/**
 * Handles single * or _ delimiters for italic text.
 * Returns the new index after processing.
 */
private fun AnnotatedString.Builder.handleSingleDelimiter(
    text: String,
    index: Int,
    baseColor: Color
): Int {
    val delimiter = text[index]

    // Check if it's actually a double delimiter (** or __)
    if (index + 1 < text.length && text[index + 1] == delimiter) {
        withStyle(SpanStyle(color = baseColor)) { append(text[index]) }
        return index + 1
    }

    // Find closing delimiter
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
