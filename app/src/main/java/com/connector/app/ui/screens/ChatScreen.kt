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
import com.connector.app.data.model.Provider
import com.connector.app.viewmodel.ChatUiState
import com.connector.app.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onOpenSettings: () -> Unit
) {
    val state by vm.state.collectAsState()
    val drawerState = rememberDrawerState(
        if (state.drawerOpen) DrawerValue.Open else DrawerValue.Closed
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.drawerOpen) {
        if (state.drawerOpen) drawerState.open() else drawerState.close()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                chats = state.chats,
                currentId = state.currentChat?.id,
                onNewChat = {
                    vm.newChat()
                    vm.toggleDrawer(false)
                },
                onSelect = { vm.selectChat(it) },
                onDelete = { vm.deleteChat(it) },
                onClose = { vm.toggleDrawer(false) }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val title = state.currentChat?.title ?: "Connector"
                        Text(
                            text = title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { vm.toggleDrawer() }) {
                            Icon(Icons.Default.Menu, contentDescription = "Меню")
                        }
                    },
                    actions = {
                        val provider = Provider.fromId(state.settings.provider)
                        TextButton(onClick = onOpenSettings) {
                            Text(
                                text = state.settings.model.ifBlank { provider.displayName },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                Composer(
                    isGenerating = state.isGenerating,
                    statusText = state.statusText,
                    onSend = { vm.sendMessage(it) },
                    onStop = { vm.stopGenerating() }
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                val chat = state.currentChat
                if (chat == null || chat.messages.isEmpty()) {
                    EmptyState(
                        hasKey = state.settings.apiKey.isNotBlank(),
                        onOpenSettings = onOpenSettings
                    )
                } else {
                    MessageList(
                        messages = chat.messages,
                        isGenerating = state.isGenerating,
                        statusText = state.statusText
                    )
                }
            }
        }
    }
}
