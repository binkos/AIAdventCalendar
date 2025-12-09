package com.aiadventcalendar.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * @property comparisonData Optional comparison data for COMPARISON message type
 */
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val messageType: MessageType = MessageType.REGULAR,
    val comparisonData: AgentResponse.ComparisonAnswer? = null
)

/**
 * Types of messages that determine visual styling in the chat.
 */
enum class MessageType {
    REGULAR,       // Standard message
    QUESTION,      // Clarifying question from AI agent
    FINAL_ANSWER,  // Final comprehensive answer from AI
    SYSTEM,        // System/info messages
    COMPARISON     // LLM comparison results
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
    val comparisonBackground = Color(0xFF6A1B9A) // Purple
    val questionText = Color.White
    val answerText = Color.White
    val comparisonText = Color.White
}

/**
 * Labels displayed for different message types.
 */
private object MessageLabels {
    const val QUESTION = "📝 Question"
    const val ANSWER = "✨ Answer"
    const val INFO = "ℹ️ Info"
    const val COMPARISON = "⚖️ LLM Comparison"
}

/**
 * Temperature presets for LLM experimentation.
 * Demonstrates how temperature affects precision, creativity, and diversity.
 */
enum class TemperaturePreset(
    val value: Double,
    val label: String,
    val emoji: String,
    val description: String,
    val color: Color
) {
    PRECISE(
        value = 0.0,
        label = "Precise",
        emoji = "🎯",
        description = "Deterministic, factual, consistent responses",
        color = Color(0xFF1E88E5) // Blue
    ),
    BALANCED(
        value = 0.7,
        label = "Balanced",
        emoji = "⚖️",
        description = "Good balance between accuracy and creativity",
        color = Color(0xFF43A047) // Green
    ),
    CREATIVE(
        value = 1.2,
        label = "Creative",
        emoji = "🎨",
        description = "Diverse, imaginative, varied responses",
        color = Color(0xFFE53935) // Red/Orange
    );
    
    companion object {
        fun fromValue(value: Double): TemperaturePreset {
            return entries.minByOrNull { kotlin.math.abs(it.value - value) } ?: BALANCED
        }
    }
}

/**
 * Sample prompts designed to demonstrate temperature differences.
 * These prompts are carefully crafted to show the impact of temperature on output.
 */
object SamplePrompts {
    val prompts: List<SamplePrompt> = listOf(
        SamplePrompt(
            title = "🎭 Creative Writing",
            prompt = "Write a short poem about a robot learning to feel emotions for the first time",
            description = "Creative task - higher temps produce more varied, imaginative results"
        ),
        SamplePrompt(
            title = "📊 Data Analysis",
            prompt = "Explain what happens when you divide a number by zero",
            description = "Factual task - lower temps stay precise, higher temps add analogies"
        ),
        SamplePrompt(
            title = "💡 Brainstorm Ideas",
            prompt = "List 5 innovative uses for an empty cardboard box",
            description = "Ideation task - temperature greatly affects creativity and uniqueness"
        ),
        SamplePrompt(
            title = "📖 Story Start",
            prompt = "Continue this story: 'The last human on Earth heard a knock on the door...'",
            description = "Narrative task - observe how plot creativity changes with temperature"
        ),
        SamplePrompt(
            title = "🔬 Scientific Explanation",
            prompt = "Explain why the sky is blue to a 5-year-old",
            description = "Explanatory task - balance of accuracy and engaging language"
        ),
        SamplePrompt(
            title = "🎲 Random Word Association",
            prompt = "Create a chain of 10 words where each word is somehow connected to the previous one, starting with 'moon'",
            description = "Association task - high temps create more surprising connections"
        )
    )
}

data class SamplePrompt(
    val title: String,
    val prompt: String,
    val description: String
)

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
    
    // Temperature State
    var selectedTemperature by remember { mutableStateOf(TemperaturePreset.BALANCED) }
    var showSamplePrompts by remember { mutableStateOf(true) }
    
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
        showSamplePrompts = false
        handleSendMessage(
            text = text,
            isLoading = isLoading,
            coroutineScope = coroutineScope,
            backendClient = backendClient,
            conversationState = conversationState,
            temperature = selectedTemperature.value,
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
        showSamplePrompts = true
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
            
            TemperatureSelector(
                selectedPreset = selectedTemperature,
                onPresetSelected = { selectedTemperature = it },
                enabled = !isLoading
            )

            if (showSamplePrompts && messages.isEmpty()) {
                SamplePromptsSection(
                    onPromptSelected = { prompt ->
                        inputText = prompt
                    }
                )
            }

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
                isCollectingAnswers = conversationState.isCollectingAnswers,
                currentTemperature = selectedTemperature
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
            Column {
                Text(
                    text = "AI Advent Calendar",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "⚖️ Dual LLM Comparison Mode",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        
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
 * Temperature selector component with preset buttons and description.
 */
@Composable
private fun TemperatureSelector(
    selectedPreset: TemperaturePreset,
    onPresetSelected: (TemperaturePreset) -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🌡️ LLM Temperature",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Current: ${selectedPreset.value}",
                    style = MaterialTheme.typography.labelMedium,
                    color = selectedPreset.color
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TemperaturePreset.entries.forEach { preset ->
                    TemperaturePresetButton(
                        preset = preset,
                        isSelected = preset == selectedPreset,
                        enabled = enabled,
                        onClick = { onPresetSelected(preset) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Text(
                text = "${selectedPreset.emoji} ${selectedPreset.description}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Individual temperature preset button.
 */
@Composable
private fun TemperaturePresetButton(
    preset: TemperaturePreset,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) preset.color else Color.Transparent
    val textColor = if (isSelected) Color.White else preset.color
    val borderColor = if (isSelected) preset.color else preset.color.copy(alpha = 0.5f)
    
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = textColor,
            disabledContainerColor = backgroundColor.copy(alpha = 0.5f),
            disabledContentColor = textColor.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${preset.emoji} ${preset.label}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = "(${preset.value})",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Section displaying sample prompts to help users experiment with temperature.
 */
@Composable
private fun SamplePromptsSection(
    onPromptSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "🧪 Try These Prompts to See Temperature Effects",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Click a prompt below, then try it with different temperature settings to observe how outputs change:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            SamplePrompts.prompts.forEach { samplePrompt ->
                SamplePromptCard(
                    samplePrompt = samplePrompt,
                    onClick = { onPromptSelected(samplePrompt.prompt) }
                )
            }
        }
    }
}

/**
 * Individual sample prompt card.
 */
@Composable
private fun SamplePromptCard(
    samplePrompt: SamplePrompt,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = samplePrompt.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = samplePrompt.prompt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "💡 ${samplePrompt.description}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic
            )
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
                    if (message.messageType == MessageType.COMPARISON && message.comparisonData != null) {
                        ComparisonBubble(comparison = message.comparisonData)
                    } else {
                        ChatBubble(message = message)
                    }
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
    isCollectingAnswers: Boolean,
    currentTemperature: TemperaturePreset = TemperaturePreset.BALANCED
) {
    val labelText = if (isCollectingAnswers) {
        "Answer the question above..."
    } else {
        "Ask your question... (${currentTemperature.emoji} temp=${currentTemperature.value})"
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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
                
                Text(
                    text = "🔬 Experiment tip: Try the same prompt with different temperatures to see how responses vary!",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
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
 * Comparison bubble displaying dual LLM responses with metrics and analysis.
 */
@Composable
fun ComparisonBubble(comparison: AgentResponse.ComparisonAnswer) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MessageColors.comparisonBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            SelectionContainer {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Text(
                        text = MessageLabels.COMPARISON,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MessageColors.comparisonText
                    )
                    
                    // Side-by-side comparison
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // OpenAI Response
                        ComparisonResultCard(
                            result = comparison.openaiResponse,
                            modelName = "OpenAI GPT-3.5-turbo",
                            accentColor = Color(0xFF10A37F),
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Arcee AI Response
                        ComparisonResultCard(
                            result = comparison.arceeAiResponse,
                            modelName = "Arcee AI",
                            accentColor = Color(0xFFFF6B35),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    // Metrics Comparison Row
                    MetricsComparisonRow(
                        openaiResult = comparison.openaiResponse,
                        arceeAiResult = comparison.arceeAiResponse
                    )
                    
                    // Analysis Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "📊 Comparative Analysis",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = parseMarkdown(comparison.comparisonAnalysis, MaterialTheme.colorScheme.onSurfaceVariant),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual LLM response card in comparison view.
 */
@Composable
private fun ComparisonResultCard(
    result: LlmResponseResult,
    modelName: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Model name header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = modelName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                if (result.isSuccess) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFF4CAF50)
                    )
                } else {
                    Text(
                        text = "✗",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFE53935)
                    )
                }
            }
            
            if (!result.isSuccess) {
                Text(
                    text = "Error: ${result.errorMessage ?: "Unknown error"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE53935)
                )
            } else {
                // Metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricChip(
                        label = "⏱️ Time",
                        value = "${result.executionTimeMs}ms",
                        color = accentColor
                    )
                    MetricChip(
                        label = "📊 Tokens",
                        value = "${result.tokenUsage.totalTokens}",
                        color = accentColor
                    )
                }
                
                // Response preview
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = result.response.take(300) + if (result.response.length > 300) "..." else "",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Token breakdown
                Text(
                    text = "📝 ${result.tokenUsage.inputTokens} in + ${result.tokenUsage.outputTokens} out tokens",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Small metric chip component.
 */
@Composable
private fun MetricChip(
    label: String,
    value: String,
    color: Color
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.2f)
        ),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

/**
 * Metrics comparison row showing side-by-side metrics.
 */
@Composable
private fun MetricsComparisonRow(
    openaiResult: LlmResponseResult,
    arceeAiResult: LlmResponseResult
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (openaiResult.isSuccess && arceeAiResult.isSuccess) {
                ComparisonMetric(
                    label = "Fastest",
                    winner = if (openaiResult.executionTimeMs < arceeAiResult.executionTimeMs) "OpenAI" else "Arcee AI",
                    openaiValue = "${openaiResult.executionTimeMs}ms",
                    arceeAiValue = "${arceeAiResult.executionTimeMs}ms"
                )
                ComparisonMetric(
                    label = "Most Efficient",
                    winner = if (openaiResult.tokenUsage.totalTokens < arceeAiResult.tokenUsage.totalTokens) "OpenAI" else "Arcee AI",
                    openaiValue = "${openaiResult.tokenUsage.totalTokens}",
                    arceeAiValue = "${arceeAiResult.tokenUsage.totalTokens}"
                )
            }
        }
    }
}

/**
 * Comparison metric showing which model wins.
 */
@Composable
private fun ComparisonMetric(
    label: String,
    winner: String,
    openaiValue: String,
    arceeAiValue: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "🏆 $winner",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "OpenAI: $openaiValue",
                style = MaterialTheme.typography.labelSmall,
                color = if (winner == "OpenAI") Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Arcee AI: $arceeAiValue",
                style = MaterialTheme.typography.labelSmall,
                color = if (winner == "Arcee AI") Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    temperature: Double,
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

            // Send message to backend with temperature
            val response = backendClient.sendMessage(
                message = text,
                history = currentState.history.dropLast(1),
                temperature = temperature
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
                temperature = temperature,
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
    temperature: Double,
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
                temperature = temperature,
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
        
        is AgentResponse.ComparisonAnswer -> {
            handleComparisonAnswerResponse(
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
    temperature: Double,
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

    // Request the first question with temperature
    val questionResponse = backendClient.sendMessage(
        message = "",
        history = newState.history,
        temperature = temperature
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
        temperature = temperature,
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

/**
 * Handles the comparison answer response with dual LLM results.
 */
private fun handleComparisonAnswerResponse(
    response: AgentResponse.ComparisonAnswer,
    currentMessages: List<ChatMessage>,
    onMessagesUpdate: (List<ChatMessage>) -> Unit,
    onStateUpdate: (ConversationState) -> Unit
) {
    val comparisonMessage = ChatMessage(
        text = "LLM Comparison Results",
        isUser = false,
        messageType = MessageType.COMPARISON,
        comparisonData = response
    )
    onMessagesUpdate(currentMessages + comparisonMessage)
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
