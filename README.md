# Connector

Нативный Android-клиент для ИИ-моделей.  
Kotlin · Jetpack Compose · Material 3

Работает с **Anthropic, OpenAI, OpenRouter, DeepSeek, Groq, xAI** и любым OpenAI-совместимым API.  
CORS не нужен — запросы идут напрямую.

---

## Возможности

- Стриминг ответов (SSE)
- История чатов (локально, DataStore)
- Системный промпт
- Инструменты: поиск DuckDuckGo + чтение страниц
- Тёмная / светлая тема
- Material 3 интерфейс

---

## Быстрый старт

### Вариант A — Android Studio (рекомендуется)

1. Склонируй репозиторий:
   ```bash
   git clone https://github.com/vasilyakhomenko/Connector.git
   cd Connector
   ```
2. Открой папку в **Android Studio**
3. Дождись синхронизации Gradle
4. Нажми ▶ Run

### Вариант B — командная строка

```bash
chmod +x gradlew
./gradlew assembleDebug
```

APK появится здесь:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## GitHub Actions

При каждом push в `main` автоматически собирается debug-APK.  
Скачать: **Actions → последняя успешная сборка → Artifacts → Connector-debug**.

---

## Требования

- Android 8.0+ (API 26)
- Android Studio Ladybug (2024.2+) или новее
- JDK 17

---

## Лицензия

MIT
