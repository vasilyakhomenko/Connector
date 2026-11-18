# Connector

Android-приложение с **оригинальным интерфейсом** Connector.

Kotlin-оболочка + WebView → UI **один в один** с исходным клиентом (проекты, чаты, настройки, tool-calling).

## Сборка

### Android Studio
1. Открой репозиторий
2. Положите `chat.html` в `app/src/main/assets/` (файл из zip / artifacts)
3. ▶ Run

### GitHub Actions
**Actions → Build APK → Download artifact**

## Важно

- Интерфейс **идентичен** первому видео: проекты, сайдбар, composer, настройки
- Anthropic работает напрямую
- Для остальных моделей укажите CORS Worker в настройках

## Структура

| Файл | Назначение |
|------|------------|
| `MainActivity.kt` | WebView, `file:///android_asset/chat.html` |
| `app/src/main/assets/chat.html` | Оригинальный UI |

## Лицензия

MIT
