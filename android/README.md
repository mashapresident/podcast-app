# Podcast App — Android client

Клієнтська частина додатка для подкастів (Kotlin, Jetpack Compose, Media3, Retrofit, Room).

## Запуск
1. Запустіть сервер (репозиторій `podcast-backend`): `docker compose up --build`.
2. Відкрийте проєкт в Android Studio (Koala або новіша, JDK 17).
3. Якщо Android Studio просить Gradle Wrapper — погодьтесь на його створення.
4. Запустіть на емуляторі: адреса сервера `http://10.0.2.2:8080/api/v1/` задана в `app/build.gradle.kts` (`API_BASE_URL`).
   Для реального пристрою вкажіть IP комп'ютера в тій самій Wi-Fi мережі.

## Наповнення каталогу
Зареєструйтесь (в застосунку або через API) і імпортуйте подкаст:
```bash
curl -X POST http://localhost:8080/api/v1/podcasts/import \
  -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" \
  -d '{"feedUrl":"https://<адреса RSS-стрічки подкасту>"}'
```

## Тести
`./gradlew test` — unit-тести (валідація, парсинг DTO, форматування часу).

## Архітектура
UI (Compose) → ViewModel (StateFlow) → Repository (мережа + Room-кеш) → Retrofit/Room/DataStore.
Плеєр — `PlaybackService` (Media3) + `PlayerController`.
