# CONTEXT_BRIEF.md — где мы сейчас

Одна страница «где мы сейчас». Обновляется в конце каждой сессии.
Новый ИИ читает вторым после `AI_RULES.md`.

**Дата обновления:** 2026-10-02 (ночь)

## Где мы

**Дома:** Android Studio. **На работе:** тоже Android Studio + git
в терминале.
**Фича в работе:** `feature/5.9-full-project` — серия 5.9, допиливание
проекта (все вкладки кроме сверки). Порядок: **Статистика →
Редактирование → БД → Настройки → Главная.**

**Текущий фокус:** **серия БД — почти закрыта.** Закрыты 17 пачек:
`db-style`, `db-info`, `db-backup-v2`, `db-backup-fix`,
`db-restore-v2`, `db-rollback`, `db-backups-ops` (2 подзахода),
`db-clean`, `db-smooth-restart`, `db-soft-restart`, `db-import-picker`,
`db-rollback-public`, `db-backup-manager`, `db-merge-v2` (7 подзаходов),
`db-backups-fix`, `db-compare`. **Осталось: `db-diagnostics`,
`db-logs`.**

## Что сделано в серии 5.9

### Статистика — закрыта

`stats-screen`, `stats-reactive`, `stats-layout`, `stats-search`,
`stats-search-2`, `stats-order-status`, `report-html`,
`stats-charts`, `stats-fixes`, `stats-compare`, `stats-compare-2`.

### Пачка `report-xlsx` — закрыта

- ✅ `xlsx-core`, `xlsx-cells`, `xlsx-styles`, `xlsx-links`.
- ✅ `xlsx-multi`, `html-multi`, `xlsx-ui`.
- ✅ `report-html-tests`, `multi-report-ui`.

### Редактирование — закрыта

- ✅ `edit-viewmodel`, `edit-screen-search`, `edit-add-sample`,
  `edit-status-blank`, `edit-save-guard`, `edit-multiselect`,
  `edit-mass-ops`.

**Возможности:**

- Реактивный редактор с деревом, поиском, фильтрами.
- Умная вставка с авто-номером, авто-интервалом, сдвигом.
- Правка одной пробы. Мультивыбор. Массовые операции.

### БД — почти закрыта

**Закрыто (17 пачек):**

- ✅ `db-style`, `db-info`.
- ✅ `db-backup-v2`, `db-backup-fix`.
- ✅ `db-restore-v2`.
- ✅ `db-rollback` — откат к авто-бэкапу.
- ✅ `db-backups-ops` — operation в манифесте, три префикса,
  ротация, папки в Загрузках, миграция.
- ✅ `db-clean` — полная очистка с `pre_clean_*`.
- ✅ `db-smooth-restart`, `db-soft-restart` — бесшовный пересбор.
- ✅ `db-import-picker` — импорт из `exports/` + SAF.
- ✅ `db-rollback-public` — приватные + публичные `pre_*`.
- ✅ `db-backup-manager` — управление бэкапами.
- ✅ `db-merge-v2` (7 подзаходов) — слияние с умными конфликтами.
- ✅ `db-backups-fix` — «Удалить авто» не трогает экспорты.
- ✅ `db-compare` — сравнение двух БД (дерево, поиск).

**Формат `.gsmbackup`** — zip-архив:
- `manifest.json` — версия формата, дата, схема, счётчики, operation.
- `geosamples.db` — БД.
- `sample_photos/` — папка с фото.

**Структура публичных Загрузок:**
Загрузки/GeoSampleManager/
pre_restore/ — авто-бэкапы перед импортом
pre_rollback/ — авто-бэкапы перед откатом
pre_clean/ — авто-бэкапы перед очисткой
exports/ — пользовательские экспорты

**Ротация** — 5 последних на операцию, приватно и публично.

**Осталось:**

- `db-diagnostics` — сироты, битые ссылки, отсутствующие фото.
- `db-logs` — логи операций (миграция 2→3, отдельная серия).

**Отменено:** `db-wells` в БД — перенесено в Редактирование
(часть B+C).

### Пачки инфраструктуры

- ✅ `docs/5.9-docs-2`, `docs/5.9-docs-3`, `docs/5.9-docs-4`.
- ✅ `fix/5.9-cleanup`, `5.9-cleanup-2`.
- ✅ `docs/5.9-edit-docs`.
- ✅ `docs/5.9-db-docs`.
- ✅ `docs/5.9-ai-rules-confirm` — §24 в `AI_RULES.md`.
- ✅ `docs/5.9-db-rollback-docs`.
- ✅ `docs/5.9-db-series`.
- ✅ `docs/5.9-db-merge`.
- ✅ `docs/5.9-db-compare` — этот заход.

## Что делать дальше

**Варианты (по рекомендации ИИ):**

- `db-diagnostics` — продолжить серию БД.
- `db-logs` — с миграцией 2→3, закрыть серию БД.
- **B+C** — редизайн вкладки БД (TabRow + подсказки) + перенос
  создания участков/нарядов в Редактирование.

**После БД и B+C:**

- **Массовое создание проб** — отдельная фича.
- **Настройки → Главная → PR фичи в `main`.**

## Известные грабли (важно!)

### Git

- **`AS Commit` ломает репо.** Только терминал.
- **Файл легко сохранить не в ту папку** (`.github/app/...`).
  Проверка: `git ls-files | findstr ИмяФайла`.
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
- **Для бесшовной пересборки** — `key(tick)` +
  `SimpleViewModelStoreOwner` + `CompositionLocalProvider`.
  См. `MainActivity.ReadyContent`.

### Kotlin

- **В KDoc нельзя `/*` внутри `/**…*/`** — вложенный блочный
  комментарий.

### Восстановление БД

- **Перед чтением файла `.db` — `PRAGMA wal_checkpoint(TRUNCATE)`.**
- **Перед заменой — закрыть соединение и удалить `-wal`/`-shm`.**
- **После замены — `AppDatabase.closeAndReset()` +
  `GeoSampleApp.resetRepository()`.**

### JVM-тесты

- **`android.net.Uri.parse()` в JVM-тестах возвращает `null`**
  (`returnDefaultValues = true`). **Не тащить `Uri` в data-модели** —
  хранить строкой (`PublicBackup.uri`, `RollbackBackup.publicUri`).
- **MediaStore, ContentResolver** — только device-check.

### Слияние БД (`db-merge-v2`)

- **`SampleConflict` заполняется areaName / orderNumber / wellNumber**
  из моих данных — для группировки в UI.
- **Булевы `found` / `postponed` / `weightControl` защищены:**
  конфликт только если у меня `false`, в архиве `true`.
- **Идентичные пробы не конфликтуют.**
- **Все копии бэкапа удаляются вместе** при одиночном удалении.
- **`deleteAllBackups()` — только авто-бэкапы.**

### Сравнение БД (`db-compare`)

- **`CompareEngine.buildResult()` — 4 дерева:**
  `inArchive / matched / different / myOnly`.
- **Схема дерева:** Участок → Наряд → Проба / Скважина.
- **Ключи:** участки по `area_name`; наряды по
  `(area_name, order_number)`; пробы по
  `(area_name, order_number, sample_number)`; скважины по
  `(area_name, order_number, well_number)`.
- **Поиск — по активной категории** (не глобальный).
- **Авторазворот при ≤30 элементах.**

## Отложенные вопросы

- **И-24.** Vosk обрывает длинные номера. До `e4d`.
- **И-35.** Vosk путает «четвёртая» / «четырнадцатая». До `e4d`.
- **Общий сервис сдвига** (`SampleShiftPlanner`).
- **Бороздовые пробы → ВК** — особая логика.
- **Правка интервала в редакторе.**
- **Undo для массовых операций.**

## Не трогать

- Схема БД (version = 2).
- `AI_RULES.md` — актуален.

## Правила текущей сессии

- Сначала спроси: дома или на работе? (Оба — AS.)
- **Ветки — левый нижний угол AS** или git в терминале.
- Дома/на работе пачка → merge локально в фичу.
- **Git — только через терминал.**
- **Порядок закрытия захода:** код → тесты → device-check →
  подтверждение пользователя → merge → удаление ветки (§24).
- **После каждого захода** — обновлять `CONTEXT_BRIEF`, `PROGRESS`,
  `NEXT_STEPS`.
- **Правки в файлах — точечные.**