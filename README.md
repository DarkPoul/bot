# Shift Scheduler Telegram Bot

Бот для керування графіками продавців, підмінами та підтвердженнями виходів. Побудовано на Java 17, Maven, Telegram Long Polling та Google Sheets як джерело даних.

## Можливості (MVP)
- Онбординг через /start із реєстрацією користувача (статус PENDING).
- Підтвердження/відхилення заявки адміністратором (ADMIN_TELEGRAM_ID).
- Створення/оновлення особистого графіка у довільному текстовому форматі.
- Перегляд особистого графіка.
- Аудит подій у окремий лист та повідомлення у групу AUDIT_GROUP_ID.

## Архітектура
- **bot layer**: `ShiftSchedulerBot`, `UpdateRouter`, UI‑білдери.
- **service layer**: `AuthService`, `ScheduleService`, `RequestService`, `ReminderService`, `AuditService`.
- **repository layer**: робота з Google Sheets через `SheetsClient` та репозиторії для кожної сутності.
- **state**: in‑memory FSM (`ConversationStateStore`) з TTL.
- **utils**: календар, перевірка перетинів змін, робота з датами у TZ Europe/Kyiv.

## Налаштування Google Sheets
1. Створіть Google Spreadsheet з листами та колонками:
   - `users`: `userId, username, fullName, locationId, phone, role, status, createdAt, createdBy`
   - `locations`: `locationId, name, address, active`
   - `location_assignments`: `locationId, userId, isPrimary, activeFrom, activeTo`
   - `tm_locations`: `tmUserId, locationId`
   - `shifts`: `shiftId, date, startTime, endTime, locationId, userId, status, source, linkedRequestId, updatedAt`
   - `requests`: `requestId, type, initiatorUserId, fromUserId, toUserId, date, startTime, endTime, locationId, status, comment, createdAt, updatedAt`
   - `audit_log`: `eventId, timestamp, actorUserId, action, entityType, entityId, details`
   - `personal_schedules`: `scheduleId, userId, scheduleText, periodStart, periodEnd, updatedAt`
2. Створіть Service Account у Google Cloud, видайте йому доступ "Editor" на Spreadsheet.
3. Завантажте JSON ключ сервісного акаунта як `secrets/sa.json` (не комітьте в git).

## Змінні оточення
- `BOT_TOKEN` – токен бота від BotFather.
- `BOT_USERNAME` – username бота без @.
- `SPREADSHEET_ID` – ID таблиці Google Sheets.
- `GOOGLE_APPLICATION_CREDENTIALS` – шлях до service account json (в контейнері `/secrets/sa.json`).
- `AUDIT_GROUP_ID` – ID Telegram групи для аудитів.
- `ADMIN_TELEGRAM_ID` – Telegram ID адміністратора, який підтверджує заявки.
- `TZ` – таймзона, за замовчуванням `Europe/Kyiv`.

## Запуск локально
```bash
mvn clean package
BOT_TOKEN=xxx BOT_USERNAME=mybot SPREADSHEET_ID=... GOOGLE_APPLICATION_CREDENTIALS=secrets/sa.json AUDIT_GROUP_ID=-100123 ADMIN_TELEGRAM_ID=123456 java -jar target/shift-scheduler-bot-1.0.0-shaded.jar
```

## Запуск у Docker
```bash
docker-compose build
BOT_TOKEN=xxx BOT_USERNAME=mybot SPREADSHEET_ID=... AUDIT_GROUP_ID=-100123 ADMIN_TELEGRAM_ID=123456 docker-compose up -d
```
Service account ключ очікується у `./secrets/sa.json`.

## Команди та UI
- `/start` – онбординг та головне меню.
- **🗓 Створити/Оновити мій графік** – введення графіка одним повідомленням.
- **👀 Переглянути мій графік** – показ збереженого графіка.

## Приклад сценарію (MVP)
1. Новий користувач запускає `/start`, вводить ПІБ та обирає локацію.
2. Бот відповідає “Заявка відправлена, очікуйте підтвердження”.
3. Адмін отримує заявку з кнопками ✅/❌ і підтверджує.
4. Користувач отримує “Вас підтверджено, доступ відкрито” і може зберегти/переглянути графік.

## Тести
- Перевірка перетину змін (`OverlapCheckerTest`).
- Генерація календаря (`CalendarKeyboardBuilderTest`).
- Формування календарного статусу (`ScheduleServiceTest`).

## Розширення
- Додати перевірку конфліктів на рівні локацій.
- Додати повні flow для SWAP/COVER з multi‑step діалогом.
- Інтегрувати планувальник для щоденних нагадувань та підтверджень виходу.
