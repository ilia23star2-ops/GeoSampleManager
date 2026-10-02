# PROGRESS.md — история заходов

## 5.9 серия — допиливание вкладок

**Дата:** 2026-09-29 … 2026-10-02
**Ветка:** `feature/5.9-full-project`
**Контекст:** после релизной серии 5.8.11 — планомерное закрытие
всех вкладок, кроме сверки. Закрыты: Статистика, Редактирование.
В работе — БД.

### Вкладка «БД» — почти закрыта

**Дата:** 2026-10-01 … 2026-10-02
**Пачки:** `db-style`, `db-info`, `db-backup-v2`, `db-backup-fix`,
`db-restore-v2` (с 2 hotfix), `db-rollback`, `db-backups-ops`
(2 подзахода), `db-clean`, `db-smooth-restart`, `db-soft-restart`,
`db-import-picker` (с фиксом), `db-rollback-public` (с фиксом),
`db-backup-manager` (с фиксом), `db-merge-v2` (7 подзаходов),
`db-backups-fix`.

**Что было:** вкладка БД — просмотр участков/нарядов/проб,
добавление участков и нарядов, удаление, простой бэкап файла `.db`
без фото. `Toast` вместо Snackbar. Копирование `.db` без
`wal_checkpoint` — потенциально неконсистентная копия.

**Что стало:** полноценный менеджер БД — экспорт/импорт, инфо-панель,
авто-бэкапы с ротацией, откат, очистка, управление бэкапами,
бесшовный перезапуск стека, слияние БД с умными конфликтами.

#### `db-style` — стилевые правки

- `Toast` → `SnackbarHost`.
- `maxWidth > 600.dp` → `>= 600.dp`.
- Убран `!!` при работе с `selectedOrder`.

#### `db-info` — инфо-панель

- Кнопка «Инфо» → `DbInfoDialog`.
- `DatabaseRepository.getDbInfo()` — счётчики всех таблиц + размеры
  файла БД и папки фото.
- `SampleNoteDao.countAll()`, `SampleImageDao.countAll()`,
  `OrderWellDao.countAll()` — новые запросы.

#### `db-backup-v2` — экспорт `.gsmbackup`

- `GsmBackupWriter` — zip-архив с `manifest.json`, `geosamples.db`,
  `sample_photos/`.
- `BackupCounts` — счётчики для манифеста.
- `manifest.json`: `format_version`, `created_at`, `app_version`,
  `db_schema_version`, `counts`.
- `DatabaseRepository.checkpointWal()` — `PRAGMA
  wal_checkpoint(TRUNCATE)`.
- `DB_SCHEMA_VERSION = 2` — константа.
- `DbBackupDialog` — имя файла + чекбокс «Поделиться после сохранения».

#### `db-backup-fix` — сохранение в публичные Загрузки

- Сохранение в **публичные Загрузки** через `MediaStore` (API 29+).
- Путь: `Загрузки/GeoSampleManager/`.
- На API < 29 — fallback через `CreateDocument`.

#### `db-restore-v2` — импорт `.gsmbackup`

- `GsmBackupReader` — чтение архива, парсинг `manifest.json`,
  извлечение `.db` + фото.
- `DbRestoreDialog` — превью: имя файла, дата, схема, счётчики.
- `DbViewModel.restoreFromUri` — авто-бэкап → `closeAndReset` →
  удаление `.db`/`-wal`/`-shm` → распаковка → `resetRepository`.
- `RestoreState` — Idle / InProgress / Done / Error.

**Hotfix 1:** `activity.recreate()` не сбрасывает ViewModel.

**Hotfix 2:** `killProcess` + `AlarmManager` не работают на новых
Android. Финальное решение — `startActivity(MainActivity,
NEW_TASK|CLEAR_TASK)` + `finish()`.

#### `db-rollback` — откат к авто-бэкапу

- Кнопка «Откатиться к авто-бэкапу» → позже переименована в «Откат».
- `RollbackBackups` — парсер имени, фильтр, сортировка, ротация.
- `GsmBackupReader` — перегрузки для `File`.
- `DatabaseRepository.getRollbackBackupsDir()` — `filesDir/db_backups/`.
- `DbRollbackDialog` + `DbRollbackConfirmDialog`.
- Авто-бэкап `pre_rollback_*` перед откатом.
- Ротация `pre_restore_*` после успешной замены (5 последних).
- `RollbackBackupsTest` — 12 тестов.

#### `db-backups-ops` — универсальная инфраструктура

**Подзаход /1 — operation + универсальная ротация.**

- `GsmBackupWriter` — параметр `operation` в `write()` и манифест.
- `GsmBackupReader.BackupManifest` — поле `operation`.
- `RollbackBackups` — поддержка трёх префиксов (`pre_restore_`,
  `pre_rollback_`, `pre_clean_`); `RollbackBackup.operation`;
  `rotateByPrefix`.
- Тесты: `BackupManifestTest` — 6.

**Подзаход /2 — папки в Загрузках + миграция.**

- Структура: `GeoSampleManager/{pre_restore, pre_rollback, pre_clean,
  exports}/`.
- `PublicBackupsMigrator` — одноразовая ленивая миграция.
- `PublicBackupsMigratorTest` — 10 тестов.

#### `db-clean` — полная очистка БД

- Кнопка «Очистить БД» (красный контур).
- Двойное подтверждение через `CleanConfirmState` (галочка).
- Авто-бэкап `pre_clean_*` перед очисткой.
- `DatabaseRepository.clearAllData()` — `db.clearAllTables()` +
  удаление всех фото.
- `CleanConfirmStateTest` — 6.

#### `db-smooth-restart` — бесшовный перезапуск (промежуточный)

- `RestartRouter` — сохранение route в SharedPreferences.
- `NavGraph` — `startDestination` из сохранённого route.
- `overridePendingTransition(0, 0)` — без анимации.
- Кнопка «Откатиться к авто-бэкапу» → «Откат».

**Не закрыло проблему:** белый экран остался.

#### `db-soft-restart` — пересбор ViewModel без пересоздания Activity

**Решение проблемы белого экрана.**

- `GeoSampleApp`: `restartRequest: StateFlow<RestartRequest?>`,
  `requestRestart(route)`, `scheduleRestartMessage`, `consumeRestartMessage`.
- `SimpleViewModelStoreOwner` — владелец ViewModelStore для поддерева.
- `MainActivity.ReadyContent(app)`: `key(tick)` +
  `CompositionLocalProvider(LocalViewModelStoreOwner)` +
  `DisposableEffect` → `owner.viewModelStore.clear()`.
- `NavGraph.AppScaffold(initialRoute)`.
- `DbScreen` — вместо `startActivity` → `app.requestRestart(Screen.DB.route)`.
- Удалён `RestartRouter` + `RestartRouterTest`.
- Снекбар «Готово. …» — после пересборки через
  `app.consumeRestartMessage()`.

#### `db-import-picker` — импорт из списка exports

- Кнопка «Импорт» → диалог со списком `.gsmbackup` из
  `Загрузки/GeoSampleManager/exports/`.
- Авто-бэкапы `pre_*` в импорте **не показываются** — ими
  занимается «Откат».
- `PublicBackup`, `PublicBackupsLister.listForImport`.
- `DbImportPickerDialog` — имя файла (моноширинный), дата, размер.
- Кнопка «Выбрать файл вручную…» (SAF).
- `PublicBackupsListerTest` — 12.

**Фикс:** `PublicBackup.uri` — строка, не `Uri` (иначе JVM-тесты
падают на `Uri.parse` = null).

#### `db-rollback-public` — приватные + публичные авто-бэкапы

- Откат объединяет `filesDir/db_backups/` и
  `Загрузки/GeoSampleManager/pre_*/`.
- `RollbackBackup.source` — `PRIVATE` / `PUBLIC`.
- `RollbackBackups.merge()` — дедупликация по имени файла,
  приоритет приватного.
- `PublicBackupsLister.rotateAutoBackups()` — ротация публичных
  через MediaStore, 5 на операцию.
- `DbViewModel.rollbackFromPublic(uri)`.
- Метка источника в строке: «Внутренний» / «Загрузки».

**Фикс:** `PublicBackup.uri` → String.

#### `db-backup-manager` — управление бэкапами

- Кнопка «Бэкапы» на вкладке БД.
- `BackupManagerDialog` — сводка (количество, размер, счётчики по
  источнику/операции), список всех бэкапов (приватные + публичные).
- Удаление по одному (крестик → подтверждение).
- Кнопка «Удалить старые» — ручная ротация (5 на операцию).
- Кнопка «Удалить авто» (было «Удалить всё») — все авто-бэкапы,
  экспорты не трогает (см. `db-backups-fix`).
- `BackupManagerStats` — чистая логика сводки + форматирование размера.
- `BackupManagerStatsTest` — 11 тестов.

**Фикс:** удаление одной пробы стирает **все копии** (приватную и
публичную); компактная раскладка кнопок на вкладке БД — 4 + 3
в двух рядах.

#### `db-merge-v2` — слияние БД

**Самая крупная пачка, 7 подзаходов.**

**Подзаход /1 — движок (участки + наряды).**

- `MergeEngine` — `openArchive`, `planAreas`, `planOrders`,
  `applyAreaPlan`, `applyOrderPlan`, `closeAndClean`.
- `MergeModels` — `AreaPlan`, `OrderPlan`, `TempDatabaseHandle`.
- `AppDatabase.buildTemp()` — открыть БД по произвольному пути.
- `MergeEngineTest` — старт.

**Подзаход /2 — пробы, скважины, заметки.**

- `planSamples`, `planWells`, `planNotes`, `apply*` для них.
- `ConflictResolution` (KEEP_MINE / TAKE_THEIRS).

**Подзаход /3 — фото.**

- Распаковка `sample_photos/` из архива.
- `planPhotos`, `applyPhotoPlan` — копирование файлов под UUID-именем.
- `extractArchivePhotoName` — чистая функция.

**Подзаход /4 — UI wizard.**

- Кнопка «Слияние» на вкладке БД.
- `MergeWizard` — превью → конфликты → прогресс → итог.
- `MergeRunner` — оркестратор apply-фаз.
- `MergePreview`, `MergeStats`.

**Подзаход /5 — умные конфликты по полям.**

- `SampleField` — перечень полей пробы.
- `FieldDiff` — расхождение по одному полю с raw + display.
- `FieldResolution` — карта «поле → чей вариант».
- `FieldOwner` — MINE / THEIRS.
- `displayFor` — человекочитаемые подписи (русские).
- Три категории в `planSamples`: `identical` / `toAdd` / `conflicts`.
- «Заполнить пустые» — умная стратегия.

**Подзаход /6 — детализация + защита отметок.**

- `DetailsBlock` в превью показывает все категории с подписями
  «нет новых» и т.п.
- Группы в конфликтах кликабельны — фильтруют список.
- `found` / `postponed` / `weightControl`: **моё true защищено**.
  Конфликт — только если у меня false, в архиве true.
- `MergeConflictsScreen` — полноэкранный экран (не тесный диалог).
- Широкая раскладка: слева панель массовых (30%), справа список (70%).
- Узкая: две вкладки «Массовые» / «Список».

**Подзаход /7 — дерево и группы.**

- `SampleConflict` получил `areaName` / `orderNumber` / `wellNumber`.
- `buildConflictTree` — дерево Наряд → Скважина → Проба.
- `MassStrategy` — ALL_MINE / ALL_THEIRS / FILL_EMPTY.
- Массовые действия на каждой группе (наряд / скважина).
- Авторазворот: ≤30 конфликтов — сразу развёрнуто; >30 — свёрнуто.
- Иконка «Развернуть всё / Свернуть всё» в шапке дерева.

**`MergeEngineTest`** — существенно расширен (порядка 80 тестов).

#### `db-backups-fix` — «Удалить авто» не трогает экспорты

- `PublicBackupsLister.isAutoBackupSubDir()` — предикат
  «это авто-бэкап».
- `DbViewModel.deleteAllBackups()` — фильтр по предикату.
- `BackupManagerDialog` — кнопка «Удалить авто», подтверждение
  про «экспорты останутся».
- `PublicBackupsListerTest` — +7 тестов.

### Вкладка «Редактирование» — закрыта

**Дата:** 2026-10-01
**Пачки:** `edit-viewmodel`, `edit-screen-search`, `edit-add-sample`,
`edit-status-blank`, `edit-save-guard`, `edit-multiselect`,
`edit-mass-ops`.

**Что стало:** реактивный редактор с деревом, поиском, фильтрами,
добавлением, правкой, мультивыбором, массовыми операциями.

**Device-check ✅** (01.10.2026).

### Пачка `report-xlsx` — закрыта

**Формат отчёта:** 1 лист = 1 наряд. Шапка A1:I1..A4:I4. Таблица
проб с цветами как в сверке. Заметки — колонка + лист «Приложения».
Фото — счётчик + картинки. Гиперссылки. Легенда в колонке J.

**Подзаходы:**

- ✅ `xlsx-core` — ручной генератор .xlsx.
- ✅ `xlsx-cells`, `xlsx-styles`, `xlsx-links`.
- ✅ `xlsx-multi` — N нарядов → N листов + «Приложения».
- ✅ `html-multi` — мультинарядный HTML.
- ✅ `xlsx-ui` — подключение Excel.
- ✅ `report-html-tests`, `multi-report-ui`.

### Инфраструктурные пачки

- ✅ `docs/5.9-docs-2`, `docs/5.9-docs-3`, `docs/5.9-docs-4`.
- ✅ `fix/5.9-cleanup`, `5.9-cleanup-2`.
- ✅ `docs/5.9-edit-docs` — доки Редактирования.
- ✅ `docs/5.9-db-docs` — первые пачки БД.
- ✅ `docs/5.9-ai-rules-confirm` — §24 в `AI_RULES.md`.
- ✅ `docs/5.9-db-rollback-docs` — после `db-rollback`.
- ✅ `docs/5.9-db-series` — серия БД (средняя часть).
- ✅ `docs/5.9-db-merge` — этот заход.

### Пачки Статистики (закрыты ранее)

`stats-screen`, `stats-reactive`, `stats-layout`, `stats-search`,
`stats-search-2`, `stats-order-status`, `report-html`,
`stats-charts`, `stats-fixes`, `stats-compare`, `stats-compare-2`.

---

## 5.8.11-sort-fix серия — SORT и голосовая навигация

**Дата:** 2026-09-26 (вечер)
**Ветка:** `fix/5.8.11-sort-fix` → merge в `feature/5.8.11-e4-voice-v2`

- `5.8.11-sort-fix` (bab0a01) — SORT flat + авто-split.
- `5.8.11-sort-fix-3` (8769657) — фикс SORT, Message.spoken, тесты.
- `5.8.11-sort-ui` (405d6f1) — SORT пишет в UI.
- `5.8.11-sort-fix-4` (ad26841) — задвоение токенов + Message.display.
- `5.8.11-sort-fix-5` (eb4d1f5) — унификация Next + каноника в Result.

### Что осталось после серии

- **И-24** — Vosk обрывает по короткой паузе. Отложено до e4d.
- **И-35** — Vosk путает «четвёртая» / «четырнадцатая». До e4d.
- **Docs + PR фичи `e4` в `main`** — серия готова, не влита.

---

## 5.8.11-e4 серия — рефакторинг ГП

**Контекст:** аудит голосового пути, сведение UI и ГП в один путь,
закрепление скважины, очередь мультизапроса, честная обработка
ошибок, маркеры намерения, подтверждение массовых, мимикрия везде.

### Закрытые пачки серии

- `5.8.11-e4-fix-voice-1` (device ✅) — 4 фикса голоса.
- `5.8.11-e4-ui-1` — кнопка «Наверх» в SearchScreen.
- `5.8.11-e4-weight-queue` — очередь веса.
- `5.8.11-e4-prefix-1` (device ✅) — префиксы по буквам.
- `5.8.11-e4-speak-1` (device ✅) — разбиение длинных номеров.
- `5.8.11-e4-markers-2` (device ✅) — отложение одной фразой.
- `5.8.11-e4-markers` (device ✅) — маркеры намерения.
- `5.8.11-e4-pin-7` (device ✅) — честная ошибка вместо fallback.
- `5.8.11-e4-pin-6` (откачен в pin-7) — показ подмены.
- `5.8.11-e4-pin-5` — расширение fallback.
- `5.8.11-e4-pin-4` — числительные в pin + fallback 14→4.
- `5.8.11-e4-pin-3` — вес «X сотни», «следующая X».
- `5.8.11-e4-pin-2` — очередь мультизапроса.
- `5.8.11-e4-pin-1` — закрепление скважины.
- `5.8.11-e4e-bundle` — мимикрия + единый путь.
- `5.8.11-e4-tests` — покрытие парсера.

### Архитектурные решения серии

Реализовано: pin скважины, очередь мультизапроса, мимикрия TTS,
честная ошибка вместо fallback, маркеры намерения, подтверждение
массовых, очередь веса, префиксы по буквам.
Не реализовано: динамические словари Vosk (`e4-dicts`).

---

## 5.8.11 серия — унификация поиска и ответа (SEARCH_MODEL)

Спецификация — `SEARCH_MODEL.md`.

- `5.8.11-d2` — Response + Presenter.
- `5.8.11-d1` — SearchResult + SearchService.
- `5.8.11-c2` — групповые кандидаты + озвучка.
- `5.8.11-c1` — DigitGroup + DigitGrouper.
- `5.8.11-b` — единый ввод.
- `5.8.11-a` — состояния ГП.

## 5.8.10 серия — настройки UI, онбординг, импорт, голос

- `5.8.10-g1/g2` (✅ device) — панель ГП.
- `5.8.10-f` (✅ device) — приоритет имени листа.
- `5.8.10-e` — коллизия по orderTitle.
- `5.8.10-d` — проверка импорта по имени участка.
- `5.8.10-c` — озвучка списка проб.
- `5.8.10-b` — И-9: онбординг.
- `5.8.10-a` — И-10: showCharacteristic.

## 5.8.6 серия — Vosk-полировка

`5f`, `5g`, `5c`, `5a`, `4`, `3`, `2`, `2a`.

## 5.8.9 серия — голосовой ввод

`f-2b`, `i-1/2/3`, `d-3c2b1/3c2b2`, `f-1a-fix-1`, `d-2a`, `g-1/3`.

## Ранее (выборочно)

- `5.8.9h-2` — `UnifiedSearch` в UI и ГП.
- `5.8.9-infra-2d` — CI вручную.
- Базовый голосовой ввод, Vosk-модель, Excel-импорт, фото, заметки.