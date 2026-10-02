# CONTEXT_BRIEF.md — где мы сейчас

Одна страница «где мы сейчас». Обновляется в конце каждой сессии.
Новый ИИ читает вторым после `AI_RULES.md`.

**Дата обновления:** 2026-10-02 (день)

## Где мы

**Дома:** Android Studio. **На работе:** тоже Android Studio + git
в терминале.
**Фича в работе:** `feature/5.9-full-project` — серия 5.9, допиливание
проекта (все вкладки кроме сверки). Порядок: **Статистика →
Редактирование → БД → Настройки → Главная.**

**Текущий фокус:** **серия БД почти закрыта.** Закрыты 12+ пачек:
`db-style`, `db-info`, `db-backup-v2`, `db-backup-fix`,
`db-restore-v2`, `db-rollback`, `db-clean`, `db-backups-ops` (2
подзахода), `db-smooth-restart`, `db-soft-restart`, `db-import-picker`,
`db-rollback-public`. **Следующая — `db-backup-manager`** (ручное
удаление бэкапов). Затем `db-merge-v2`, `db-diagnostics`,
`db-compare`, `db-wells`, `db-logs`.

## Что сделано в серии 5.9

### Статистика — закрыта

**Пачки** (все закрыты):
`stats-screen`, `stats-reactive`, `stats-layout`, `stats-search`,
`stats-search-2`, `stats-order-status`, `report-html`,
`stats-charts`, `stats-fixes`, `stats-compare`, `stats-compare-2`.
`bulk-confirm-2`, `table-responsive`, `row-highlight` — в сверке.

### Пачка `report-xlsx` — закрыта

- ✅ `xlsx-core` — ручной генератор .xlsx.
- ✅ `xlsx-cells`, `xlsx-styles`, `xlsx-links`.
- ✅ `xlsx-multi` — N нарядов → N листов.
- ✅ `html-multi` — мультинарядный HTML.
- ✅ `xlsx-ui` — Excel в одиночном диалоге.
- ✅ `report-html-tests`, `multi-report-ui`.

### Редактирование — закрыта

- ✅ `edit-viewmodel`, `edit-screen-search`, `edit-add-sample`,
  `edit-status-blank`, `edit-save-guard`, `edit-multiselect`,
  `edit-mass-ops`.

**Возможности Редактирования:**

- Реактивный редактор с деревом, поиском, фильтрами.
- Умная вставка с авто-номером, авто-интервалом, сдвигом.
- Правка одной пробы. Для холостых интервал скрыт.
- Мультивыбор длинным тапом.
- Массовая правка статуса синхронизирует `weightControl`.
- Массовое удаление с опциональным пересчётом номеров.

### БД — почти закрыта

**Закрыто:**

- ✅ `db-style` — Snackbar вместо Toast, `>= 600.dp`, без `!!`.
- ✅ `db-info` — инфо-панель (путь, размеры, счётчики).
- ✅ `db-backup-v2` — экспорт `.gsmbackup` (БД + фото + manifest).
- ✅ `db-backup-fix` — сохранение в публичные Загрузки.
- ✅ `db-restore-v2` — импорт с авто-бэкапом и пересозданием стека.
- ✅ `db-rollback` — откат к авто-бэкапу.
- ✅ `db-backups-ops` — инфраструктура авто-бэкапов:
  - `operation` в манифесте (`restore`/`rollback`/`clean`/`export`).
  - Универсальная ротация по каждому префиксу.
  - Папки в Загрузках: `pre_restore/`, `pre_rollback/`,
    `pre_clean/`, `exports/`.
  - Ленивая миграция старых файлов из корня.
- ✅ `db-clean` — полная очистка БД с `pre_clean_*`.
- ✅ `db-smooth-restart` — перезапуск с возвратом на вкладку БД
  и `overridePendingTransition(0, 0)`.
- ✅ `db-soft-restart` — **бесшовный** пересбор поддерева через
  `restartTick` + `SimpleViewModelStoreOwner`. Activity **не
  пересоздаётся**, белого экрана нет.
- ✅ `db-import-picker` — импорт из списка `exports/` + SAF.
- ✅ `db-rollback-public` — откат объединяет приватные и публичные
  `pre_*`, ротация публичных, дедупликация по имени файла.

**Формат `.gsmbackup`** — zip-архив:
- `manifest.json` — версия формата, дата, схема, счётчики, operation.
- `geosamples.db` — сама БД.
- `sample_photos/` — папка с фото.

**Структура публичных Загрузок:**
Загрузки/GeoSampleManager/
pre_restore/ — авто-бэкапы перед импортом
pre_rollback/ — авто-бэкапы перед откатом
pre_clean/ — авто-бэкапы перед очисткой
exports/ — пользовательские экспорты

**Ротация** — 5 последних на операцию, приватно и публично.

**Осталось:**

- `db-backup-manager` — ручное удаление бэкапов, «Удалить старые»,
  «Удалить все».
- `db-merge-v2` — слияние двух БД + диалог конфликтов (3-4
  подзахода).
- `db-diagnostics` — сироты, битые ссылки, отсутствующие фото.
- `db-compare` — сравнение двух БД.
- `db-wells` — просмотр `order_wells`.
- `db-logs` — логи операций (отдельная серия, миграция 2→3).

### Пачки инфраструктуры

- ✅ `docs/5.9-docs-2`, `docs/5.9-docs-3`, `docs/5.9-docs-4` —
  доки Статистики.
- ✅ `fix/5.9-cleanup`, `5.9-cleanup-2` — чистка и warnings.
- ✅ `docs/5.9-edit-docs` — доки Редактирования.
- ✅ `docs/5.9-db-docs` — первые пачки БД.
- ✅ `docs/5.9-ai-rules-confirm` — §24 в `AI_RULES.md`.
- ✅ `docs/5.9-db-rollback-docs` — после `db-rollback`.
- ✅ `docs/5.9-db-series` — этот заход.

## Что делать дальше

**Серия БД — осталось 6 пачек.** Следующая:

- `db-backup-manager` — управление бэкапами: просмотр, ручное
  удаление отдельных, «Удалить старые» (ручная ротация), «Удалить
  все».

Затем: **`db-merge-v2` → `db-diagnostics` → `db-compare` →
`db-wells` → `db-logs`.**

После БД: **Настройки → Главная → PR фичи в `main`.**

## Известные грабли (важно!)

### Git

- **`AS Commit` ломает репо.** Все git-операции — **только терминал**.
- **Файл легко сохранить не в ту папку** (`.github/app/...`).
  Проверка: `git ls-files | findstr ИмяФайла`.
- **`git checkout`/`New Branch` — левый нижний угол AS.**
- **Новая ветка — `git push -u origin <ветка>`** при первом пуше.
- Можно один раз включить: `git config --global push.autoSetupRemote true`.

### Gradle

- **Test-worker'ы падают при многопоточной сборке.**
  Обход: `./gradlew testDebugUnitTest --no-daemon --max-workers=1`.

### XLSX (критичные — все из 30.09.2026, не забывать!)

- **`styles rel` в `workbook.xml.rels` — обязателен.**
- **`theme` в `<fgColor>` не использовать.**
- **Порядок элементов в `<font>` строго по ECMA-376.**
- **В `workbook.xml`** нужны `fileVersion`, `workbookPr`, `calcPr`.
- **В `sheet.xml`** нужны `sheetViews`, `sheetFormatPr`.
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
  `FilterChip`.
- **`activity.recreate()` не сбрасывает ViewModel.**
- **Для бесшовной пересборки** — `key(tick)` +
  `SimpleViewModelStoreOwner` + `CompositionLocalProvider`, а не
  `startActivity`. См. `MainActivity.ReadyContent`.

### Восстановление БД

- **Перед чтением файла `.db` — `PRAGMA wal_checkpoint(TRUNCATE)`.**
- **Перед заменой файла `.db` — закрыть соединение и удалить
  `-wal`/`-shm`.**
- **После замены — `AppDatabase.closeAndReset()` +
  `GeoSampleApp.resetRepository()`.**
- **Kotlin-нюанс:** в KDoc нельзя писать `/*` внутри `/**…*/` —
  вложенный блочный комментарий.

## Отложенные вопросы

- **И-24.** Vosk обрывает длинные номера. Отложено до серии `e4d`.
- **И-35.** Vosk путает «четвёртая» / «четырнадцатая». До `e4d`.
- **Общий сервис сдвига** (`SampleShiftPlanner`) — унификация
  INSERT/RENAME/DELETE/EDIT_INTERVAL. Обсуждён, не реализован.
- **Бороздовые пробы → ВК** — особая логика взвешивания. Отложено.
- **Правка интервала в редакторе** — сдвиг последующих.
- **Undo для массовых операций.**

## Не трогать

- Схема БД (version = 2).
- `AI_RULES.md` — актуален.

## Правила текущей сессии

- Сначала спроси: дома или на работе? (Оба — AS.)
- **Ветки — левый нижний угол AS** или git в терминале.
- Дома/на работе пачка → merge локально в фичу.
- **Git — только через терминал.**
- **Порядок закрытия захода:** код → тесты → подтверждение
  пользователя → merge → удаление ветки (§24).
- **После каждого захода** — обновлять `CONTEXT_BRIEF`, `PROGRESS`,
  `NEXT_STEPS`.
- **Правки в файлах — точечные.**