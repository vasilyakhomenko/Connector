package com.connector.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.connector.app.ui.screens.ChatScreen
import com.connector.app.ui.screens.SettingsScreen
import com.connector.app.ui.theme.ConnectorTheme
import com.connector.app.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ConnectorTheme {
                Surface(Modifier.fillMaxSize()) {
                    val vm: ChatViewModel = viewModel()
                    var showSettings by remember { mutableStateOf(false) }
                    val state by vm.state.collectAsState()

                    if (showSettings) {
                        SettingsScreen(
                            current = state.settings,
                            onSave = { vm.saveSettings(it) },
                            onBack = { showSettings = false }
                        )
                    } else {
                        ChatScreen(
                            vm = vm,
                            onOpenSettings = { showSettings = true }
                        )
                    }
                }
            }
        }
    }
}
