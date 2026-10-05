# CONTEXT_BRIEF.md — где мы сейчас

Одна страница «где мы сейчас». Обновляется в конце каждой сессии.
Новый ИИ читает вторым после `AI_RULES.md`.

**Дата обновления:** 2026-10-05

## Где мы

**Дома:** Android Studio. **На работе:** тоже Android Studio + git
в терминале.
**Фича в работе:** `feature/5.9-full-project` — серия 5.9 на выходе.

**Все вкладки закрыты:**
- ✅ Статистика.
- ✅ Редактирование.
- ✅ БД (19 пачек).
- ✅ Настройки (внешний вид, тема, справка, звук, голос, Bluetooth).
- ✅ Главная (сводка, продолжить, незавершённые, проблемы, отчёт).
- ✅ Журнал аудита (logs, 9 подзаходов).

**Отдельные фиксы:**
- sort-normalize, tts-audio-mode, tts-audio-mode-fix-normal,
  settings-sound-3, settings-scale, settings-scale-2, settings-theme,
  settings-help-1/2a/2b, main-a/main-b/main-fix, exit.

**Осталось в 5.9:**
- `docs/5.9-final` — доки + правила (текущий заход).
- **PR фичи `5.9-full-project` в `main`.**
- `5.9-mass-add` — в долгом ящике.

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
- **`return@Column` в Compose — нестабильно.** Заменять на `when` /
  `if-else`. Количество вызовов между рекомпозициями должно быть
  стабильным.
- **KDoc и `/*`.** Внутри `/**…*/` нельзя `/*` — ломает парсер.
  Ссылки на glob-паттерны писать без звёздочки: «папка assets/help»,
  не «assets/help/*.md».

### Kotlin
- **В KDoc нельзя `/*` внутри `/**…*/`.**

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
- **`logs.db` — отдельная БД.**
- **`LogWriter` использует `trySend`.**
- **Батч: 50 записей или раз в 500 мс.**
- **Файловый архив — `Downloads/GeoSampleManager/.logs/YYYY-MM-DD.log`.**

### Настройки (5.9-settings)
- **`AppearanceSettings`** — `scale` + `theme`. Хранится в
  `filesDir/appearance_settings.json`.
- **Масштаб** — `textFactor` (fontScale) и `densityFactor` (density)
  разделены. `fontScale` множится сильно, `density` слабо.
- **Тема** — `AppTheme.SYSTEM/LIGHT/DARK`. Применяется в
  `MainActivity.onCreate` через `app.appearance`.
- **Справка** — `assets/help/*.md`, парсится `HelpContentLoader`.
  Рендер — `HelpBlocksView`. KDoc без звёздочки.
- **`SettingsCategory`** — 8 пунктов, включая `HELP`.

### Главная (5.9-main)
- **Сводка:** счётчики + прогресс + кнопка отчёта.
- **Продолжить работу:** последний наряд из `SessionStateRepository`.
- **Незавершённые:** сортировка `% убыв → время убыв → номер`.
- **Требует внимания:** только сироты SQL (лёгкая проверка).
- **Отчёт:** `pendingReportRequest` → `StatsScreen`.

### Выход (5.9-exit)
- **`ExitBackupWriter`** — `filesDir/exit_backup/last_exit.gsmbackup`.
- **Перезаписывается** при каждом выходе.
- **Не участвует в ротации** и не удаляется из UI.
- **Back на Главной** — диалог выхода.
- **Операция в манифесте — `"exit"`.**

## Не трогать

- Схема основной БД (version = 2).
- `AI_RULES.md` — актуален.

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