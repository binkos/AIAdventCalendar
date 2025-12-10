package com.aiadventcalendar.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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

data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

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
    var inputText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
) {
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Type your message...") },
                        modifier = Modifier
                            .weight(1f)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.key == Key.Enter && 
                                    keyEvent.type == KeyEventType.KeyDown && 
                                    !keyEvent.isShiftPressed
                                ) {
                                    if (inputText.isNotBlank() && !isLoading) {
                                    coroutineScope.launch {
                                        val text = inputText
                                        inputText = ""
                                        messages = messages + ChatMessage(text = text, isUser = true)
                                        isLoading = true
                                        
                                        try {
                                            val response = backendClient.sendMessage(
                                                message = text,
                                                history = emptyList(),
                                                temperature = 0.7
                                            )
                                            
                                            val parsed = backendClient.parseResponse(response)
                                            val answerText = when (parsed) {
                                                is AgentResponse.Answer -> parsed.answer
                                                else -> response
                                            }
                                            
                                            messages = messages + ChatMessage(text = answerText, isUser = false)
                                        } catch (e: Exception) {
                                            messages = messages + ChatMessage(text = "Error: ${e.message}", isUser = false)
                                        } finally {
                                            isLoading = false
                                        }
                                    }
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
                    onClick = {
                        if (inputText.isNotBlank() && !isLoading) {
                            coroutineScope.launch {
                                val text = inputText
                                inputText = ""
                                messages = messages + ChatMessage(text = text, isUser = true)
                                isLoading = true
                                
                                try {
                                    val response = backendClient.sendMessage(
                                        message = text,
                                        history = emptyList(),
                                        temperature = 0.7
                                    )
                                    
                                    val parsed = backendClient.parseResponse(response)
                                    val answerText = when (parsed) {
                                        is AgentResponse.Answer -> parsed.answer
                                        else -> response
                                    }
                                    
                                    messages = messages + ChatMessage(text = answerText, isUser = false)
                                } catch (e: Exception) {
                                    messages = messages + ChatMessage(text = "Error: ${e.message}", isUser = false)
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
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
