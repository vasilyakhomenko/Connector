package com.connector.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.connector.app.data.model.Chat
import com.connector.app.data.model.ChatMessage
import kotlinx.coroutines.launch

@Composable
internal fun DrawerContent(
    chats: List<Chat>,
    currentId: String?,
    onNewChat: () -> Unit,
    onSelect: (Chat) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.widthIn(max = 320.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Connector", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onNewChat, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Новый чат")
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(chats, key = { it.id }) { chat ->
                    val selected = chat.id == currentId
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                            .clickable { onSelect(chat) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(chat.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { onDelete(chat.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EmptyState(hasKey: Boolean, onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Чем могу помочь?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        if (!hasKey) {
            Text("Добавьте API-ключ: нажмите на название модели сверху", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onOpenSettings) { Text("Открыть настройки") }
        } else {
            Text("Напишите сообщение ниже", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
internal fun MessageList(messages: List<ChatMessage>, isGenerating: Boolean, statusText: String) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) scope.launch { listState.animateScrollToItem(messages.lastIndex) }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(messages, key = { it.id }) { msg -> MessageBubble(msg) }
        if (isGenerating && statusText.isNotBlank()) item { StatusIndicator(statusText) }
    }
}

@Composable
internal fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        if (isUser) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                Text(msg.content, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Text(msg.content.ifBlank { "…" }, color = if (msg.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp)
        }
    }
}

@Composable
internal fun StatusIndicator(text: String) {
    val transition = rememberInfiniteTransition(label = "thinking")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { i ->
                val alpha by transition.animateFloat(initialValue = 0.25f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(500, delayMillis = i * 160), repeatMode = RepeatMode.Reverse), label = "dot$i")
                Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary.copy(alpha = alpha)))
            }
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
internal fun Composer(isGenerating: Boolean, statusText: String, onSend: (String) -> Unit, onStop: () -> Unit) {
    var text by remember { mutableStateOf("") }
    Surface(tonalElevation = 2.dp, shadowElevation = 8.dp, shape = RoundedCornerShape(24.dp), modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth()) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("Задайте любой вопрос") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!isGenerating && text.isNotBlank()) { onSend(text); text = "" } }),
                maxLines = 6, shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, disabledBorderColor = Color.Transparent)
            )
            Spacer(Modifier.width(4.dp))
            FilledIconButton(onClick = { if (isGenerating) onStop() else if (text.isNotBlank()) { onSend(text); text = "" } }, shape = CircleShape, modifier = Modifier.size(44.dp)) {
                Icon(if (isGenerating) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send, contentDescription = if (isGenerating) "Стоп" else "Отправить")
            }
        }
    }
}
