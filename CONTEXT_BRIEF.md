# CONTEXT_BRIEF.md — где мы сейчас

Одна страница «где мы сейчас». Обновляется в конце каждой сессии.
Новый ИИ читает вторым после `AI_RULES.md`.

**Дата обновления:** 2026-10-02 (утро)

## Где мы

**Дома:** Android Studio. **На работе:** тоже Android Studio + git
в терминале.
**Фича в работе:** `feature/5.9-full-project` — серия 5.9, допиливание
проекта (все вкладки кроме сверки). Порядок: **Статистика →
Редактирование → БД → Настройки → Главная.**

**Текущий фокус:** **серия БД в процессе.** Закрыты 6 пачек:
`db-style`, `db-info`, `db-backup-v2`, `db-backup-fix`,
`db-restore-v2` (с 2 hotfix), `db-rollback`. **Следующая —
`db-clean`, `db-merge-v2`** (порядок уточняется).

## Что сделано в серии 5.9

### Статистика — закрыта

**Пачки** (все закрыты):
`stats-screen`, `stats-reactive`, `stats-layout`, `stats-search`,
`stats-search-2`, `stats-order-status`, `report-html`,
`stats-charts`, `stats-fixes`, `stats-compare`, `stats-compare-2`.
`bulk-confirm-2`, `table-responsive`, `row-highlight` — в сверке.

### Пачка `report-xlsx` — закрыта

- ✅ `xlsx-core` — ручной генератор .xlsx (zip + XML).
- ✅ `xlsx-cells` — заполнение ячеек из `ReportData`.
- ✅ `xlsx-styles` — цвета строк, жирный.
- ✅ `xlsx-links` — гиперссылки.
- ✅ `xlsx-multi` — N нарядов → N листов + общий лист «Приложения».
- ✅ `html-multi` — мультинарядный HTML.
- ✅ `xlsx-ui` — Excel в одиночном диалоге + все доработки.
- ✅ `report-html-tests` — тесты на одиночный HTML-генератор.
- ✅ `multi-report-ui` — экран выбора нарядов для мультиотчёта.

### Редактирование — закрыта

- ✅ `edit-viewmodel` — ViewModel вкладки, дерево, поиск/фильтры.
- ✅ `edit-screen-search` — адаптивный экран.
- ✅ `edit-add-sample` — умная вставка со сдвигом номеров и интервалов.
- ✅ `edit-status-blank` — интервал скрыт у холостых.
- ✅ `edit-save-guard` — проверка № по БД перед сохранением.
- ✅ `edit-multiselect` — режим выделения (BottomBar, чекбоксы).
- ✅ `edit-mass-ops` — массовая правка и удаление.

**Возможности Редактирования:**

- Реактивный редактор с деревом, поиском, фильтрами.
- Добавление пробы с авто-номером, авто-интервалом, валидацией,
  диалогом конфликтов, сдвигом номеров и интервалов.
- Правка одной пробы (интервал, вес, ВК, характеристика, тип,
  статус). Для холостых интервал скрыт.
- Мультивыбор длинным тапом.
- Массовая правка статуса синхронизирует флаг `weightControl`.
- Массовое удаление с опциональным пересчётом номеров.
- Проверка конфликта № по БД (не только по видимым строкам).

### БД — в работе

**Закрыто:**

- ✅ `db-style` — Snackbar вместо Toast, `>= 600.dp`, без `!!`.
- ✅ `db-info` — инфо-панель (путь, размеры БД/фото, счётчики).
- ✅ `db-backup-v2` — экспорт `.gsmbackup` (БД + фото + manifest).
- ✅ `db-backup-fix` — сохранение в **публичные Загрузки** через
  MediaStore (папка `GeoSampleManager`).
- ✅ `db-restore-v2` — импорт `.gsmbackup` с авто-бэкапом текущей БД
  и пересозданием стека приложения (без `killProcess`, через
  `startActivity(CLEAR_TASK)`).
- ✅ `db-rollback` — откат к авто-бэкапу `pre_restore_*`, ротация
  5 последних, общий `performReplacement` с импортом.

**Формат `.gsmbackup`** — zip-архив:
- `manifest.json` — версия формата, дата, версия схемы, счётчики.
- `geosamples.db` — сама БД.
- `sample_photos/` — папка с фото.

**Авто-бэкап перед импортом и откатом** сохраняется в двух местах:
- публично: `Загрузки/GeoSampleManager/pre_restore_*.gsmbackup`
  или `pre_rollback_*.gsmbackup`;
- приватно: `filesDir/db_backups/pre_restore_*.gsmbackup`
  или `pre_rollback_*.gsmbackup`.

`pre_restore_*` ротируются: держим 5 последних.

**Осталось:**

- `db-clean` — полная очистка БД с бэкапом.
- `db-merge-v2` — слияние двух БД + диалог конфликтов (большая, 3-4 подзахода).
- `db-diagnostics` — сироты, битые ссылки, отсутствующие фото.
- `db-compare` — сравнение двух БД.
- `db-wells` — просмотр `order_wells`.
- `db-logs` — логи операций. **Отдельная серия** (миграция 2→3).

### Пачки инфраструктуры

- ✅ `docs/5.9-docs-2` — доки после `xlsx-multi` и `html-multi`.
- ✅ `fix/5.9-cleanup` — убраны дубликаты в корне.
- ✅ `5.9-cleanup-2` — warnings компилятора в `XlsxWriter`.
- ✅ `docs/5.9-docs-3` — доки после `xlsx-ui`.
- ✅ `docs/5.9-edit-docs` — доки после Редактирования.
- ✅ `docs/5.9-db-docs` — доки после пачек БД.
- ✅ `docs/5.9-db-rollback-docs` — доки после `db-rollback`.

## Что делать дальше

**Серия БД — осталось 5 пачек.** Следующая на выбор:
- `db-clean` — простая, полезная.
- `db-merge-v2` — большая, с конфликтами.
- `db-diagnostics` — поиск проблем.

Затем: **Настройки → Главная → PR фичи в `main`.**

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
  Обход: **AS-runner** или
  `./gradlew testDebugUnitTest --no-daemon --max-workers=1`.

### XLSX (критичные — все из 30.09.2026, не забывать!)

- **`styles rel` в `workbook.xml.rels` — обязателен.**
- **`theme` в `<fgColor>` не использовать.**
- **Порядок элементов в `<font>` строго по ECMA-376:**
  `b, i, ..., u, sz, color, name`.
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
  `FilterChip` (при использовании `FilterChipDefaults`).
- **`activity.recreate()` не сбрасывает ViewModel.** Compose
  `viewModel()` переживает пересоздание Activity. Для полного сброса
  использовать `startActivity(MainActivity, NEW_TASK|CLEAR_TASK)` +
  `finish()`.

### Восстановление БД

- **Перед чтением файла `.db` — `PRAGMA wal_checkpoint(TRUNCATE)`.**
  Иначе копия неконсистентная.
- **Перед заменой файла `.db` — закрыть соединение и удалить
  `-wal`/`-shm`.** Иначе SQLite подхватит старые.
- **После замены — `AppDatabase.closeAndReset()` +
  `GeoSampleApp.resetRepository()`.** Без этого старый `repo` держит
  закрытое соединение.

## Отложенные вопросы

- **И-24.** Vosk обрывает длинные номера. Отложено до серии `e4d`.
- **И-35.** Vosk путает «четвёртая» / «четырнадцатая». До `e4d`.
- **Общий сервис сдвига** (`SampleShiftPlanner`) — унификация
  INSERT/RENAME/DELETE/EDIT_INTERVAL. Обсуждён, не реализован.
- **Бороздовые пробы → ВК** — особая логика взвешивания. Отложено.
- **Правка интервала в редакторе** — сдвиг следующих проб,
  предупреждение о разрывах/пересечениях. Обсуждено, не реализовано.
- **Undo для массовых операций** — `applyMassEdit` и `deleteSelected`
  не создают undo-записей.

## Не трогать

- Схема БД (version = 2).
- `AI_RULES.md` — актуален.

## Правила текущей сессии

- Сначала спроси: дома или на работе? (Оба — AS.)
- **Ветки — левый нижний угол AS.**
- Дома/на работе пачка → merge локально в фичу.
- **Git — только через терминал.**
- **Порядок закрытия захода:** код → тесты → подтверждение
  пользователя → merge → удаление ветки.
- **После каждого захода** — обновлять `CONTEXT_BRIEF`, `PROGRESS`,
  `NEXT_STEPS`.
- **Правки в файлах — точечные.**