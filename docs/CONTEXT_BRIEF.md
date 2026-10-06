# CONTEXT_BRIEF.md — где мы сейчас

Одна страница «где мы сейчас». Обновляется в конце каждой сессии.
Новый ИИ читает вторым после `AI_RULES.md`.

**Дата обновления:** 2026-10-06

## Где мы

**Дома:** Android Studio. **На работе:** тоже Android Studio + git
в терминале.

**Активной фичи нет.** Последняя крупная серия 5.10 (теневая
статистика) закрыта и влита в `main` через PR #124.

**Архив (не удалять):**
- `feature/5.10-shadow-stats` — теневая статистика. Реализовано,
  влито в `main`.
- `feature/5.9-full-project` — допиливание вкладок. Реализовано,
  влито в `main`.
- `feature/5.8.11-e4-voice-v2` — рефакторинг ГП. Архив.

**Ближайшая работа — долги после 5.10.** См. `NEXT_STEPS.md`.

**Спецификации:**
- `docs/SHADOW_STATS.md` — теневая статистика (реализовано).
- `docs/VOICE.md` — голосовой помощник.

## Известные грабли (важно!)

### Git
- **`AS Commit` ломает репо.** Только терминал (`Alt+F12`).
- **Файл легко сохранить не в ту папку.**
- **`git checkout`/`New Branch` — левый нижний угол AS.**
- **Новая ветка — `git push -u origin <ветка>`** при первом пуше.

### Gradle
- **Test-worker'ы падают при многопоточной сборке.**
  Обход: `./gradlew testDebugUnitTest --no-daemon --max-workers=1`.

### XLSX (критичные)
- **`styles rel` в `workbook.xml.rels` — обязателен.**
- **`theme` в `<fgColor>` не использовать.**
- **Порядок в `<font>` строго по ECMA-376.**
- **В `workbook.xml`** — `fileVersion`, `workbookPr`, `calcPr`.
- **В `sheet.xml`** — `sheetViews`, `sheetFormatPr`.
- **`bgColor` = `fgColor`** в solid fill.
- **`indexed` + `rgb` одновременно** в `<fgColor>`.
- **Запись через `ByteArrayOutputStream`** (не `zip.finish()`).
- **Порядок в sheet.xml:** `dimension` → `sheetViews` →
  `sheetFormatPr` → `cols` → `sheetData` → `mergeCells` →
  `hyperlinks` → `drawing`.
- **Порядок в styleSheet:** `numFmts` → `fonts` → `fills` →
  `borders` → `cellStyleXfs` → `cellXfs` → `cellStyles` → `dxfs` →
  `tableStyles`.

### Compose
- **`maxWidth` из `BoxWithConstraints` недоступен внутри
  `Row`/`Column`-скоупа.** Считать значение до входа в скоуп.
- **`@OptIn(ExperimentalMaterial3Api::class)`** нужен для
  `ExposedDropdownMenuBox`, `ExposedDropdownMenu`, `menuAnchor`,
  `FilterChip`, `TopAppBar`, `TabRow`.
- **`activity.recreate()` не сбрасывает ViewModel.**
- **`Scaffold` вложенный — нельзя.** Внутри `Scaffold` Material3
  `AnimatedContent` ломает Compose Runtime при вложенности:
  `ArrayIndexOutOfBoundsException` в `SlotTableKt.key`. Внутренний
  экран — `Box` + `Column`, `SnackbarHost` вручную.
- **`return@Column` в Composable-лямбде — нельзя.** Падает с
  `IndexOutOfBoundsException: Index -1 out of bounds for length 0`
  в `androidx.compose.runtime.Stack.pop`. Composer не может
  закрыть группу. Заменять на `when` / `if-else` с двумя
  полными ветками. `return` из **самой** Composable-функции
  (до первого `@Composable`-вызова) — безопасен.
- **KDoc и `/*`.** Внутри `/**…*/` нельзя `/*` — ломает парсер.
  Ссылки на glob-паттерны писать без звёздочки: «папка assets/help»,
  не «assets/help/*.md».

### Kotlin
- **В KDoc нельзя `/*` внутри `/**…*/`.**

### Dp / Float
- **`Dp * Float` работает, `Float * Dp` — нет.** Менять порядок.
- **`scrollState.scrollTo` — в px, `hourWidthDp` — в dp.**
  Конвертация через `LocalDensity`.

### Восстановление БД
- **Перед чтением файла `.db` — `PRAGMA wal_checkpoint(TRUNCATE)`.**
- **Перед заменой — закрыть соединение и удалить `-wal`/`-shm`.**
- **После замены — `AppDatabase.closeAndReset()` +
  `GeoSampleApp.resetRepository()`.**

### JVM-тесты
- **`android.net.Uri.parse()` в JVM-тестах возвращает `null`.**
- **MediaStore, ContentResolver** — только device-check.

### Слияние БД
- Булевы `found` / `postponed` / `weightControl` защищены.
- Идентичные пробы не конфликтуют.

### Сравнение БД
- `CompareEngine.buildResult()` — 4 дерева.
- Ключи: `area_name`, `(area_name, order_number)`,
  `(area_name, order_number, sample_number)`.

### Журнал
- **`logs.db` удалена в 5.10.** Журнал переехал в `stats.db.events`.
- **UI журнала из Настроек убран.** Просмотр — в панели админа.
- **`LogWriter.flush`** → `stats.db.events` (batch).
- **Файловый архив `.logs/` тоже вырезан.**
- **`LogsDbCleanup.cleanupIfNeeded`** удаляет `logs.db` при апдейте.

### Теневая статистика (5.10) — реализовано
- **`stats.db` — отдельная БД.** Файл: `filesDir/stats/active.db`.
- **5 таблиц:** `sessions`, `tab_visits`, `order_work`, `events`,
  `daily_summary`.
- **`SessionTracker.onAppStart`** открывает новую сессию; висящая
  закрывается как `crash`.
- **`OrderWorkTracker`** держит один активный наряд в памяти.
  **Модель фаз «последнее событие»:** поиск → SEARCH,
  отметка → VERIFY. Каждый интервал идёт в ту фазу, которой
  начался. Пауза ≥ 60 сек в фазу не идёт; ≥ 30 мин — закрывает
  наряд.
- **`TabVisitTracker`** закрывает предыдущий визит при смене
  вкладки; `Mutex` защищает от гонок.
- **`AutoWarnRules`:** 5+ неудачных поисков подряд, 3+ одинаковых
  ошибки подряд; `reset()` при старте сессии.
- **Ротация `stats.db`:** `StatsRotator.checkAndRotate()` через
  `runBlocking` в `onCreate`, **до** `SessionTracker.onAppStart`.
  Месяц по `MAX(sessions.started_at)` (или mtime). Коллизия в
  `archive/` → warn, не ротируем.
- **Панель администратора:** вход — долгий тап на версии в
  «О приложении» → пароль `0000` (`AdminPanelAuth.DEFAULT_PASSWORD`).
- **Табы:** День / Наряды / Ошибки.
- **`EventsList.kt`** — плоский список событий **на экране дня**,
  с раскрытием `detailsJson`. `EventRowExpandable` — общий
  компонент для дня / визита / ошибок.
- **`ProblemsBlock`** пока с нулями (5.10-stat-admin-ui-5b
  отложено).

### Настройки (5.9-settings)
- **`AppearanceSettings`** — `scale` + `theme`. Хранится в
  `filesDir/appearance_settings.json`.
- **Масштаб** — `textFactor` (fontScale) и `densityFactor` (density)
  разделены.
- **Тема** — `AppTheme.SYSTEM/LIGHT/DARK`.
- **Справка** — `assets/help/*.md`, парсится `HelpContentLoader`.
- **`SettingsCategory`** — 7 пунктов (`SYSTEM` удалён в 5.10).

### Главная (5.9-main)
- **Сводка:** счётчики + прогресс + кнопка отчёта.
- **Продолжить работу:** последний наряд из `SessionStateRepository`.
- **Незавершённые:** сортировка `% убыв → время убыв → номер`.
- **Требует внимания:** только сироты SQL.
- **Отчёт:** `pendingReportRequest` → `StatsScreen`.

### Выход (5.9-exit)
- **`ExitBackupWriter`** — `filesDir/exit_backup/last_exit.gsmbackup`.
- **Перезаписывается** при каждом выходе.
- **Не участвует в ротации** и не удаляется из UI.
- **Back на Главной** — диалог выхода.

## Не трогать

- Схема основной БД (version = 2).
- `AI_RULES.md` — актуален (кроме §15 — отдельный долг).
- Спецификации `docs/SHADOW_STATS.md`, `docs/VOICE.md`.

## Правила текущей сессии

- Сначала спроси: дома или на работе? (Оба — AS.)
- **Git — только через Terminal.**
- **В начале захода — блок команд создания ветки.**
- **После «норм» — блок команд коммита/merge/delete/verify.**
- **«Норм» = device-check пройден. «Тесты зелёные» — только юнит.**
- **Пока пачка не смержена — новых не открываем.**
- **После каждого захода** — обновлять `CONTEXT_BRIEF`, `PROGRESS`,
  `NEXT_STEPS`.
- **Правки в файлах — точечные.**