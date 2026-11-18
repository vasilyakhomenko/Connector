package com.connector.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.connector.app.data.model.AppSettings
import com.connector.app.data.model.Provider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    current: AppSettings,
    onSave: (AppSettings) -> Unit,
    onBack: () -> Unit
) {
    var providerId by remember { mutableStateOf(current.provider) }
    var apiKey by remember { mutableStateOf(current.apiKey) }
    var model by remember { mutableStateOf(current.model) }
    var baseUrl by remember { mutableStateOf(current.baseUrl) }
    var systemPrompt by remember { mutableStateOf(current.systemPrompt) }
    var showKey by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    val provider = Provider.fromId(providerId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            onSave(
                                AppSettings(
                                    provider = providerId,
                                    apiKey = apiKey.trim(),
                                    model = model.trim(),
                                    baseUrl = baseUrl.trim(),
                                    systemPrompt = systemPrompt
                                )
                            )
                            onBack()
                        }
                    ) {
                        Text("Сохранить")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Провайдер", style = MaterialTheme.typography.labelLarge)

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = provider.displayName,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    Provider.entries.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.displayName) },
                            onClick = {
                                providerId = p.id
                                if (model.isBlank() || Provider.entries.any { it.defaultModel == model }) {
                                    model = p.defaultModel
                                }
                                if (baseUrl.isBlank() || Provider.entries.any { it.defaultBaseUrl == baseUrl }) {
                                    baseUrl = ""
                                }
                                expanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API-ключ") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Модель") },
                placeholder = { Text(provider.defaultModel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Адрес API (необязательно)") },
                placeholder = { Text(provider.defaultBaseUrl.ifBlank { "https://..." }) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("Системные инструкции") },
                placeholder = { Text("Например: отвечай кратко и на русском") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                shape = RoundedCornerShape(12.dp)
            )

            Text(
                "В нативном приложении CORS не нужен — все провайдеры работают напрямую.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
