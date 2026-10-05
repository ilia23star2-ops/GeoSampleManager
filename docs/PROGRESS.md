# PROGRESS.md — история заходов

## 5.9 серия — допиливание вкладок

**Дата:** 2026-09-29 … 2026-10-05
**Ветка:** `feature/5.9-full-project`
**Контекст:** после релизной серии 5.8.11 — планомерное закрытие
всех вкладок, кроме сверки. Закрыты: Статистика, Редактирование,
БД, серия `logs`.

### Вкладка «БД» — закрыта

**Дата:** 2026-10-01 … 2026-10-05
**Пачки:** `db-style`, `db-info`, `db-backup-v2`, `db-backup-fix`,
`db-restore-v2` (с 2 hotfix), `db-rollback`, `db-backups-ops`
(2 подзахода), `db-clean`, `db-smooth-restart`, `db-soft-restart`,
`db-import-picker` (с фиксом), `db-rollback-public` (с фиксом),
`db-backup-manager` (с фиксом), `db-merge-v2` (7 подзаходов),
`db-backups-fix`, `db-compare` (с фиксами), `db-diagnostics`
(2 подзахода), `db-restructure-edit` (2 подзахода).

**Что было:** вкладка БД — просмотр участков/нарядов/проб,
добавление участков и нарядов, удаление, простой бэкап файла `.db`
без фото. `Toast` вместо Snackbar.

**Что стало:** полноценный менеджер БД — экспорт/импорт,
инфо-панель, авто-бэкапы с ротацией, откат, очистка, управление
бэкапами, бесшовный перезапуск стека, слияние с умными
конфликтами, сравнение двух БД, диагностика. Управление участками
и нарядами (создание, удаление) — перенесено в Редактирование.

#### `db-style` — стилевые правки

- `Toast` → `SnackbarHost`.
- `maxWidth > 600.dp` → `>= 600.dp`.
- Убран `!!` при работе с `selectedOrder`.

#### `db-info` — инфо-панель

- Кнопка «Инфо» → `DbInfoDialog`.
- `DatabaseRepository.getDbInfo()` — счётчики + размеры.
- Новые запросы `countAll()` в трёх DAO.

**Устарело:** в `db-restructure-edit-2` `DbInfoDialog` удалён,
инфо встроено в `DbScreen` как постоянная панель.

#### `db-backup-v2` — экспорт `.gsmbackup`

- `GsmBackupWriter` — zip с `manifest.json`, `geosamples.db`,
  `sample_photos/`.
- `checkpointWal()` — `PRAGMA wal_checkpoint(TRUNCATE)`.
- `DbBackupDialog` — имя файла + «Поделиться после сохранения».

#### `db-backup-fix` — сохранение в публичные Загрузки

- Через `MediaStore` (API 29+).
- На API < 29 — fallback `CreateDocument`.

#### `db-restore-v2` — импорт `.gsmbackup`

- `GsmBackupReader` — чтение архива, парсинг манифеста.
- `DbRestoreDialog` — превью.
- Авто-бэкап → `closeAndReset` → распаковка → `resetRepository`.
- Hotfix 1: `recreate()` не сбрасывает ViewModel.
- Hotfix 2: `startActivity(MainActivity, NEW_TASK|CLEAR_TASK)`.

#### `db-rollback` — откат к авто-бэкапу

- `RollbackBackups` — парсер, фильтр, сортировка, ротация.
- `DbRollbackDialog` + `DbRollbackConfirmDialog`.
- Авто-бэкап `pre_rollback_*` перед откатом.
- `RollbackBackupsTest` — 12 тестов.

#### `db-backups-ops` — универсальная инфраструктура

**Подзаход /1 — operation + универсальная ротация.**

- `GsmBackupWriter` — параметр `operation`.
- `RollbackBackups` — три префикса.
- Тесты: `BackupManifestTest` — 6.

**Подзаход /2 — папки в Загрузках + миграция.**

- `GeoSampleManager/{pre_restore, pre_rollback, pre_clean, exports}/`.
- `PublicBackupsMigrator` — ленивая миграция.
- `PublicBackupsMigratorTest` — 10.

#### `db-clean` — полная очистка БД

- Двойное подтверждение через `CleanConfirmState`.
- Авто-бэкап `pre_clean_*`.
- `DatabaseRepository.clearAllData()`.
- `CleanConfirmStateTest` — 6.

#### `db-smooth-restart` — бесшовный перезапуск (промежуточный)

- `RestartRouter` — сохранение route.
- `NavGraph` — `startDestination` из сохранённого route.

**Не закрыло:** белый экран остался.

#### `db-soft-restart` — пересбор ViewModel без пересоздания Activity

- `GeoSampleApp`: `restartRequest`, `requestRestart`,
  `scheduleRestartMessage`, `consumeRestartMessage`.
- `SimpleViewModelStoreOwner`.
- `MainActivity.ReadyContent` — `key(tick)` + провайдер + Dispose.
- Удалён `RestartRouter` + `RestartRouterTest`.
- Снекбар «Готово. …» после пересборки.

#### `db-import-picker` — импорт из exports

- Список `.gsmbackup` из `exports/`.
- Авто-бэкапы `pre_*` не показываются.
- SAF-выбор вручную.
- `PublicBackupsListerTest` — 12.

**Фикс:** `PublicBackup.uri` — строка, не `Uri`.

#### `db-rollback-public` — приватные + публичные

- `RollbackBackup.source` — `PRIVATE` / `PUBLIC`.
- `RollbackBackups.merge()` — дедупликация.
- `PublicBackupsLister.rotateAutoBackups()`.

#### `db-backup-manager` — управление бэкапами

- `BackupManagerDialog` — сводка + список.
- Удаление по одному, «Удалить старые», «Удалить авто».
- `BackupManagerStats` — сводка.
- `BackupManagerStatsTest` — 11.

**Фикс:** удаление одной пробы стирает все копии.
**Фикс:** компактная раскладка кнопок — 4 + 3.

#### `db-merge-v2` — слияние БД (7 подзаходов)

- /1 — движок: участки, наряды.
- /2 — пробы, скважины, заметки.
- /3 — фото (копирование файлов).
- /4 — UI wizard.
- /5 — умные конфликты по полям.
- /6 — детализация + защита отметок.
- /7 — дерево Наряд → Скважина → Проба, группы, авторазворот.

`MergeEngineTest` — ~80 тестов.

#### `db-backups-fix` — «Удалить авто» не трогает экспорты

- `PublicBackupsLister.isAutoBackupSubDir()`.
- `DbViewModel.deleteAllBackups()` — фильтр.
- `PublicBackupsListerTest` — +7.

#### `db-compare` — сравнение двух БД

**Три подзахода.**

**Подзаход /1 — базовый диалог.**

- Кнопка «Сравнить» на вкладке БД.
- `CompareEngine.countMyOnly()` — счётчики.
- `DbCompareDialog` — плоский диалог.

**Фикс:** визуал — карточки-фильтры.

**Подзаход /2 — движок иерархии.**

- `CompareTree` — Участок → Наряд → Проба/Скважина.
- `CompareEngine.buildResult()` — 4 дерева.
- `ConflictInfo`.
- Тесты `CompareEngineTest` — 11.

**Подзаход /3 — полноэкранный UI.**

- `DbCompareScreen` — как `MergeConflictsScreen` (30/70).
- 4 цветные карточки-фильтра.
- Дерево в правой части.
- Поиск по активной категории.
- Авторазворот ≤30 / иконка «Развернуть всё / Свернуть всё».
- Узкий экран — две вкладки «Сводка» / «Подробности».
- Удалён старый `DbCompareDialog`.

#### `db-diagnostics` — поиск и исправление проблем

**Два подзахода.**

**Подзаход /1 — движок + DAO + тесты.**

- `data/diagnostics/DbIssue.kt` — sealed-класс проблем:
  `OrphanOrder`, `OrphanSample`, `BrokenPhotoLink`,
  `PhotoFlagMismatch`.
- `data/diagnostics/DbDiagnosticsEngine.kt` — чистая логика
  поиска проблем.
- `OrderDao.findOrphanOrders()`, `SampleDao.findOrphanSamples()`,
  `SampleDao.getAllSamplesList()`, `SampleImageDao.getAllImages()`.
- `DbDiagnosticsEngineTest` — 14 тестов.

**Подзаход /2 — UI + применение + авто-бэкап.**

- `data/diagnostics/DiagnosticsModels.kt` — `DbDiagnosticsState`
  (Idle / Loading / Ready / Applying / Done / Error).
- `DatabaseRepository.runDiagnostics()` и
  `applyDiagnosticsFixes()` — применение исправлений в транзакции.
- `ui/screens/DbDiagnosticsDialog.kt` — единый список с чек-боксами,
  группировка по типу.
- `DbViewModel` — состояние, выбор, применение.
- `DbScreen` — кнопка «Диагностика».
- Авто-бэкап `pre_diagnostics_*` перед исправлением.
- `RollbackBackups.OPERATIONS` — добавлена `diagnostics`.

**Проверки:**
1. Наряды без участка.
2. Пробы без наряда.
3. Битые ссылки на фото (объединены пункты 3 и 5 из ТЗ).
4. Флаг `has_photo = false`, но файлы на диске есть.

#### `db-restructure-edit` — перенос участков/нарядов в Редактирование

**Два подзахода.**

**Подзаход /1 — перенос создания и удаления.**

- `EditViewModel` — добавлены `addArea`, `deleteArea`,
  `addOrder`, `deleteOrder` (перенесены из `DbViewModel`).
- `EditScreen` — кнопка «+ Участок» над деревом; иконки
  «+ Наряд» и «Удалить» у каждого участка; «Удалить» у каждого
  наряда.
- Диалоги `TextInputDialog` / `ConfirmDialog` перенесены
  из `DbScreen` в `EditScreen`.
- `DbScreen` — список участков/нарядов оставлен только для
  просмотра (без кнопок создания/удаления).

**Подзаход /2 — инфо-панель вместо списка.**

- `DbScreen` — список участков/нарядов/проб убран полностью.
  Вместо него — постоянная инфо-панель `DbInfoPanel`.
- `DbViewModel` — убраны стейты `areas`, `selectedArea`,
  `orders`, `selectedOrder`, `samples`; методы `selectArea`,
  `selectOrder`.
- `DbInfoDialog.kt` удалён. `formatBytes` переехал в `DbScreen.kt`.
- Кнопка «Инфо» убрана — её роль выполняет панель.
- Панель обновляется автоматически при заходе на вкладку +
  кнопка «Обновить».

### Серия `logs` — журнал аудита

**Дата:** 2026-10-05
**Пачки:** `logs-1` … `logs-8b` (9 подзаходов).

**Что было:** журнала не было. При сбое непонятно, что делал
оператор и почему приложение упало.

**Что стало:** полный журнал аудита с двумя хранилищами:

- `logs.db` — отдельная БД (не в основной схеме) для быстрого
  чтения и фильтров.
- `Downloads/GeoSampleManager/.logs/YYYY-MM-DD.log` — скрытый
  файловый архив, по одному файлу на день.

**Категории:** `app`, `nav`, `search`, `voice`, `mark`, `edit`,
`db`, `error`.

**Экран:** Настройки → Система → Журнал событий. Фильтры по
категории, раскрытие details, двойная очистка с чек-боксом.

#### `logs-1` — фундамент

- `data/logs/LogCategory.kt`, `LogLevel.kt`, `LogEntry.kt`,
  `LogDao.kt`, `LogsDatabase.kt`, `LogFormatter.kt`.
- `LogsDatabase` — отдельная БД, `version = 1`.
- `LogCategoryTest`, `LogLevelTest`, `LogFormatterTest` — 12.

#### `logs-2` — движок

- `data/logs/LogWriter.kt` — канал + батчи (50 записей или
  раз в 500 мс), `trimToMaxEntries(10_000)`.
- `data/logs/LogEntryBuilder.kt` — fluent-API.
- `data/logs/DetailsJson.kt` — Gson-обёртка.
- `data/logs/Log.kt` — точка входа (`Log.app`, `Log.db` и др.).
- `DetailsJsonTest`, `LogEntryBuilderTest` — 19.

#### `logs-3` — интеграция

- `data/logs/DeviceInfo.kt` — снимок устройства.
- `data/logs/CrashRecord.kt`, `CrashHandler.kt` — перехват
  необработанных исключений; файл `pending_crash.json`
  до следующего старта.
- `GeoSampleApp` — `LogWriter.init`, `CrashHandler.install`,
  запись `app_start` со снимком устройства и счётчиков БД.
- `MainActivity` — `onResume` / `onPause` → логирование.
- `NavGraph` — переходы на вкладки.
- `CrashRecordTest` — 6.

#### `logs-4a` — голос

- `data/logs/AppLog.kt` — `typealias` для `Log`, чтобы не
  конфликтовать с `android.util.Log`.
- `VoiceDialog` — запись голосовых команд (`voice:cmd=...`)
  с raw-текстом Vosk, командой и результатом.

#### `logs-4b` — действия в редакторе

- `data/logs/SampleRowDiff.kt` — diff между старой и новой
  пробой, формирует фразу для журнала.
- `ReconciliationViewModel` — логирование отметок, правок,
  удаления, заметок, фото, Undo/Redo, массовых операций.
- `SampleRowDiffTest` — 11.

#### `logs-4c` — операции с БД

- `DbViewModel` — логирование экспорта, импорта, отката,
  очистки, слияния, диагностики.
- Единая точка `performReplacement` — фразы по `operation`.

#### `logs-5` — экран журнала

- `data/logs/LogsFilter.kt` — enum с русскими метками.
- `ui/screens/LogsViewModel.kt` — стейты, фильтры, очистка.
- `ui/screens/LogsScreen.kt` — полноэкранный экран, разделители
  по дням, чипы фильтра, раскрытие details.
- `SettingsScreen` — раздел «Система» с кнопкой «Открыть журнал
  событий».
- `LogsFilterTest` — 6.

#### `logs-6` / `logs-7` — файловый архив + формат

- `data/logs/LogFileWriter.kt` — файловый архив.
- Формат — Unicode-маркеры `●` / `⚠` / `✕`.
- Шапка дня с разделителем `━`.
- Время в записи — `HH:mm:ss` (дата в шапке).
- Details раскрыты построчно через `└ key: value`.
- Категория выровнена до 12 символов.
- Папка — `Downloads/GeoSampleManager/.logs/` (с точкой —
  скрыта от стандартных файловых менеджеров).
- `LogFileWriterTest` — 11.

#### `logs-8a` — UI-поиск и контекст

- `ReconciliationViewModel` — логирование поисковых запросов
  (после дебаунса, без спама).
- Логирование выбора участка и наряда (категория `nav`).

#### `logs-8b` — импорт Excel

- `AddViewModel` — логирование импорта: файл, число листов,
  пропуск/создание/замена наряда, финал (счётчики).
- Ошибки чтения Excel — категория `error`.

### Вкладка «Редактирование» — закрыта

**Дата:** 2026-10-01 … 2026-10-05
**Пачки:** `edit-viewmodel`, `edit-screen-search`, `edit-add-sample`,
`edit-status-blank`, `edit-save-guard`, `edit-multiselect`,
`edit-mass-ops`, `db-restructure-edit` (1).

**Что стало:** реактивный редактор с деревом, поиском, фильтрами,
добавлением, правкой, мультивыбором, массовыми операциями.
Управление участками и нарядами (создание, удаление).

### Пачка `report-xlsx` — закрыта

- ✅ `xlsx-core`, `xlsx-cells`, `xlsx-styles`, `xlsx-links`.
- ✅ `xlsx-multi`, `html-multi`, `xlsx-ui`.
- ✅ `report-html-tests`, `multi-report-ui`.

### Инфраструктурные пачки

- ✅ `docs/5.9-docs-2`, `docs/5.9-docs-3`, `docs/5.9-docs-4`.
- ✅ `fix/5.9-cleanup`, `5.9-cleanup-2`.
- ✅ `docs/5.9-edit-docs`.
- ✅ `docs/5.9-db-docs`.
- ✅ `docs/5.9-ai-rules-confirm` — §24 в `AI_RULES.md`.
- ✅ `docs/5.9-db-rollback-docs`.
- ✅ `docs/5.9-db-series`.
- ✅ `docs/5.9-db-merge`.
- ✅ `docs/5.9-db-compare`.
- ✅ `docs/5.9-logs-docs-1`, `docs/5.9-logs-docs-2`.

### Пачки Статистики (закрыты ранее)

`stats-screen`, `stats-reactive`, `stats-layout`, `stats-search`,
`stats-search-2`, `stats-order-status`, `report-html`,
`stats-charts`, `stats-fixes`, `stats-compare`, `stats-compare-2`.

---

## 5.8.11-sort-fix серия — SORT и голосовая навигация

**Дата:** 2026-09-26 (вечер)
**Ветка:** `fix/5.8.11-sort-fix` → merge в `feature/5.8.11-e4-voice-v2`

- `5.8.11-sort-fix` (bab0a01) — SORT flat + авто-split.
- `5.8.11-sort-fix-3` (8769657) — фикс SORT.
- `5.8.11-sort-ui` (405d6f1) — SORT пишет в UI.
- `5.8.11-sort-fix-4` (ad26841) — задвоение токенов.
- `5.8.11-sort-fix-5` (eb4d1f5) — унификация Next.

### Что осталось

- **И-24** — Vosk обрывает по короткой паузе.
- **И-35** — Vosk путает «четвёртая» / «четырнадцатая».
- **Docs + PR фичи `e4` в `main`**.

---

## 5.8.11-e4 серия — рефакторинг ГП

**Контекст:** аудит голосового пути, pin скважины, очередь
мультизапроса, мимикрия TTS, честная ошибка вместо fallback,
маркеры намерения, подтверждение массовых, очередь веса, префиксы
по буквам.

### Закрытые пачки серии

- `5.8.11-e4-fix-voice-1` (device ✅).
- `5.8.11-e4-ui-1` — кнопка «Наверх».
- `5.8.11-e4-weight-queue`.
- `5.8.11-e4-prefix-1` (device ✅).
- `5.8.11-e4-speak-1` (device ✅).
- `5.8.11-e4-markers-2` (device ✅).
- `5.8.11-e4-markers` (device ✅).
- `5.8.11-e4-pin-7` (device ✅).
- `5.8.11-e4-pin-6` (откачен в pin-7).
- `5.8.11-e4-pin-5`, `-pin-4`, `-pin-3`, `-pin-2`, `-pin-1`.
- `5.8.11-e4e-bundle` — мимикрия + единый путь.
- `5.8.11-e4-tests`.

---

## 5.8.11 серия — унификация поиска и ответа (SEARCH_MODEL)

- `5.8.11-d2` — Response + Presenter.
- `5.8.11-d1` — SearchResult + SearchService.
- `5.8.11-c2` — групповые кандидаты.
- `5.8.11-c1` — DigitGroup + DigitGrouper.
- `5.8.11-b` — единый ввод.
- `5.8.11-a` — состояния ГП.

## 5.8.10 серия — настройки UI, онбординг, импорт, голос

- `5.8.10-g1/g2`, `f`, `e`, `d`, `c`, `b`, `a`.

## 5.8.6 серия — Vosk-полировка

`5f`, `5g`, `5c`, `5a`, `4`, `3`, `2`, `2a`.

## 5.8.9 серия — голосовой ввод

`f-2b`, `i-1/2/3`, `d-3c2b1/3c2b2`, `f-1a-fix-1`, `d-2a`, `g-1/3`.

## Ранее (выборочно)

- `5.8.9h-2` — `UnifiedSearch` в UI и ГП.
- `5.8.9-infra-2d` — CI вручную.
- Базовый голосовой ввод, Vosk-модель, Excel-импорт, фото, заметки.