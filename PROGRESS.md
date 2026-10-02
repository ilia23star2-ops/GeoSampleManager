# PROGRESS.md — история заходов

## 5.9 серия — допиливание вкладок

**Дата:** 2026-09-29 … 2026-10-02
**Ветка:** `feature/5.9-full-project`
**Контекст:** после релизной серии 5.8.11 — планомерное закрытие
всех вкладок, кроме сверки. Закрыты: Статистика, Редактирование.
В работе — БД.

### Вкладка «БД» — в работе

**Дата:** 2026-10-01 … 2026-10-02
**Пачки:** `db-style`, `db-info`, `db-backup-v2`, `db-backup-fix`,
`db-restore-v2` (с 2 hotfix), `db-rollback`, `db-clean`,
`db-backups-ops` (2 подзахода), `db-smooth-restart`, `db-soft-restart`,
`db-import-picker` (с фиксом), `db-rollback-public` (с фиксом).

**Что было:** вкладка БД — просмотр участков/нарядов/проб,
добавление участков и нарядов, удаление, простой бэкап файла `.db`
без фото. `Toast` вместо Snackbar. Копирование `.db` без
`wal_checkpoint` — потенциально неконсистентная копия.

**Что стало:** полноценный менеджер БД — экспорт/импорт, инфо-панель,
авто-бэкапы с ротацией, откат, очистка, бесшовный перезапуск стека.

#### `db-style` — стилевые правки

- `Toast` → `SnackbarHost`.
- `maxWidth > 600.dp` → `>= 600.dp`.
- Убран `!!` при работе с `selectedOrder`.
- Комментарии приведены к общему стилю.

#### `db-info` — инфо-панель

- Кнопка «Инфо» в шапке → `DbInfoDialog`.
- `DatabaseRepository.getDbInfo()` — счётчики всех таблиц + размеры
  файла БД и папки фото.
- `SampleNoteDao.countAll()`, `SampleImageDao.countAll()`,
  `OrderWellDao.countAll()` — новые запросы.
- Отображение: путь, размер БД, размер фото, дата изменения,
  участков/нарядов/скважин/проб/фото/заметок.
- Кнопка «Обновить» + авто-загрузка при открытии.

#### `db-backup-v2` — экспорт `.gsmbackup`

- `GsmBackupWriter` — zip-архив с `manifest.json`, `geosamples.db`,
  `sample_photos/`.
- `BackupCounts` — счётчики для манифеста.
- `manifest.json`: `format_version`, `created_at`, `app_version`,
  `db_schema_version`, `counts`.
- `DatabaseRepository.checkpointWal()` — `PRAGMA
  wal_checkpoint(TRUNCATE)` перед чтением `.db`.
- `DatabaseRepository.getPhotosDir()` — путь к фото.
- `DB_SCHEMA_VERSION = 2` — константа.
- `DbBackupDialog` — имя файла (редактируемое) + чекбокс «Сохранить
  во внутреннюю папку / наружу».
- `DbViewModel.exportToInternal`, `exportToUri`.

#### `db-backup-fix` — сохранение в публичные Загрузки

**Проблема:** внутренняя папка `filesDir/db_backups/` невидима
рабочему в проводнике. Файл «не находится».

**Решение:**
- `db-backup-v2` переделан: сохранение в **публичные Загрузки**
  через `MediaStore` (API 29+).
- Путь: `Downloads/GeoSampleManager/geosamples_YYYYMMDD_HHMM.gsmbackup`.
- Имя папки «Downloads» на каждом устройстве своё (Android переводит
  сам) — например, на MIUI «Donwload».
- На API < 29 — fallback через `CreateDocument` (системный диалог).
- Чекбокс «Поделиться после сохранения» в диалоге — открывает
  Share-интент после записи.
- `_lastExportUri` в ViewModel; `LaunchedEffect` в UI читает и
  запускает Share.

#### `db-restore-v2` — импорт `.gsmbackup`

- `GsmBackupReader` — чтение архива, парсинг `manifest.json`,
  извлечение `.db` + фото.
- `BackupManifest` — data-класс.
- `DbRestoreDialog` — превью: имя файла, дата, версия схемы, счётчики.
  Проверки: `format_version = 1` (блокирует), `db_schema_version = 2`
  (предупреждает).
- `DbViewModel.restoreFromUri` — авто-бэкап → закрытие БД → удаление
  `.db`/`-wal`/`-shm` → очистка фото → распаковка → resetRepository.
- `AppDatabase.closeAndReset()` — новый метод.
- `GeoSampleApp.resetRepository()` — новый метод.
- `RestoreState` — sealed class (Idle / InProgress / Done / Error).
- Overlay с прогрессом на время импорта.
- Кнопка «Импорт» в шапке БД.

**Hotfix 1:** `activity.recreate()` не сбрасывает ViewModel.
Заменено на `killProcess` + `AlarmManager`.

**Hotfix 2:** `killProcess` + `AlarmManager` не работают на новых
Android. Финальное решение — `startActivity(MainActivity,
NEW_TASK|CLEAR_TASK)` + `finish()`.

**Device-check ✅** (01.10.2026): экспорт + импорт + пересоздание
стека работают.

#### `db-rollback` — откат к авто-бэкапу

- Кнопка «Откатиться к авто-бэкапу» (позже переименована в «Откат»).
- `RollbackBackups` — чистая логика: парсер имени
  `pre_restore_YYYYMMDD_HHmm.gsmbackup`, фильтр, сортировка, ротация.
- `GsmBackupReader` — перегрузки для `File` (без ContentResolver).
- `DatabaseRepository.getRollbackBackupsDir()` — `filesDir/db_backups/`.
- `DbViewModel.loadRollbackBackups`, `rollbackFromInternal`, общий
  метод `performReplacement` для import/rollback.
- `DbRollbackDialog` — список точек отката.
- `DbRollbackConfirmDialog` — двойное подтверждение.
- Авто-бэкап `pre_rollback_*` перед откатом.
- Ротация `pre_restore_*` после успешной замены (5 последних).
- Тесты: `RollbackBackupsTest` — 12 тестов.

**Device-check ✅** (02.10.2026).

#### `db-backups-ops` — универсальная инфраструктура авто-бэкапов

**Подзаход /1 — operation + универсальная ротация.**

- `GsmBackupWriter` — параметр `operation` в `write()`, поле в манифест.
- `GsmBackupReader.BackupManifest` — поле `operation` (`optString`).
- `RollbackBackups` — поддержка трёх префиксов (`pre_restore_`,
  `pre_rollback_`, `pre_clean_`); `RollbackBackup.operation`;
  `rotateByPrefix` — ротация отдельно по каждой операции.
- `DbViewModel.autoBackup(operation)` вместо `autoBackup(prefix)`.
- `DbRollbackDialog` — плашка операции (Импорт/Откат/Очистка/Экспорт).
- Тесты: `RollbackBackupsTest` (расширен), `BackupManifestTest` (6).

**Подзаход /2 — папки в Загрузках + миграция.**

- Структура: `GeoSampleManager/{pre_restore, pre_rollback, pre_clean,
  exports}/`.
- `PublicBackupsMigrator` — одноразовая ленивая миграция старых
  файлов из корня `GeoSampleManager/` в подпапки (флаг в
  `SharedPreferences`).
- `DbScreen` — `LaunchedEffect(Unit)` вызывает миграцию при
  первом показе.
- Тесты: `PublicBackupsMigratorTest` (10).

#### `db-clean` — полная очистка БД

- Кнопка «Очистить БД» (красный контур).
- Двойное подтверждение через `CleanConfirmState` (галочка «Понимаю»).
- Авто-бэкап `pre_clean_*` перед очисткой.
- `DatabaseRepository.clearAllData()` — `db.clearAllTables()` +
  удаление всех фото.
- Ротация `pre_*_` после успешной очистки.
- Тесты: `CleanConfirmStateTest` (6).

#### `db-smooth-restart` — бесшовный перезапуск (промежуточный)

- `RestartRouter` — сохранение route в SharedPreferences перед
  `startActivity(CLEAR_TASK)`.
- `NavGraph` — `startDestination` из сохранённого route.
- `overridePendingTransition(0, 0)` — без анимации.
- Кнопка «Откатиться к авто-бэкапу» → «Откат».

**Не закрыло проблему:** белый экран остался (пересоздание Activity).

#### `db-soft-restart` — пересоздание ViewModel без пересоздания Activity

**Решение проблемы белого экрана.**

- `GeoSampleApp`: `restartRequest: StateFlow<RestartRequest?>`,
  `requestRestart(route)`, `scheduleRestartMessage`, `consumeRestartMessage`.
- `SimpleViewModelStoreOwner` — владелец ViewModelStore для
  Compose-поддерева.
- `MainActivity.ReadyContent(app)`: `key(tick)` +
  `CompositionLocalProvider(LocalViewModelStoreOwner)` +
  `DisposableEffect` → `owner.viewModelStore.clear()`.
- `NavGraph.AppScaffold(initialRoute)`.
- `DbScreen` — вместо `startActivity` → `app.requestRestart(Screen.DB.route)`.
- Удалён `RestartRouter` + `RestartRouterTest`.
- Снекбар «Готово. …» показывается **после** пересборки поддерева —
  через `app.consumeRestartMessage()` в `LaunchedEffect(Unit)`.
- **Activity больше не пересоздаётся.** Белого экрана нет.

#### `db-import-picker` — импорт из списка exports

- Кнопка «Импорт» открывает диалог со списком `.gsmbackup` из
  `Загрузки/GeoSampleManager/exports/`.
- Авто-бэкапы `pre_*` в импорте **не показываются** — ими
  занимается «Откат».
- `PublicBackup`, `PublicBackupsLister.listForImport`,
  `PublicBackupsLister.listAutoBackups`.
- `DbImportPickerDialog` — строка: имя файла (моноширинный),
  дата, размер, счётчики.
- Кнопка «Выбрать файл вручную…» (SAF) — всегда.
- Тесты: `PublicBackupsListerTest` (12).

**Фикс:** `PublicBackup.uri` — строка, не `android.net.Uri`
(иначе JVM-тесты падают на `Uri.parse` = null).

#### `db-rollback-public` — приватные + публичные авто-бэкапы

- Откат объединяет `filesDir/db_backups/` и
  `Загрузки/GeoSampleManager/pre_*/`.
- `RollbackBackup.source` — `PRIVATE` / `PUBLIC`.
- `RollbackBackups.merge()` — дедупликация по имени файла,
  приоритет приватного.
- `RollbackBackups.fromPublic()` — `PublicBackup` → `RollbackBackup`.
- `PublicBackupsLister.rotateAutoBackups()` — ротация публичных
  через MediaStore, 5 на операцию.
- `DbViewModel.rollbackFromPublic(uri)`.
- `loadRollbackBackups()` — сначала ротация публичных, потом сбор
  и merge.
- `rotateAllBackups()` — чистит и приватные, и публичные.
- Метка источника в строке: «Внутренний» / «Загрузки».
- Тесты: `RollbackBackupsTest` (расширен — `merge`, `fromPublic`).

**Device-check ✅** (02.10.2026).

### Вкладка «Редактирование» — закрыта

**Дата:** 2026-10-01
**Пачки:** `edit-viewmodel`, `edit-screen-search`, `edit-add-sample`,
`edit-status-blank`, `edit-save-guard`, `edit-multiselect`,
`edit-mass-ops`.

**Что было:** `EditScreen.kt` — пустая заглушка.

**Что стало:** реактивный редактор с деревом, поиском, фильтрами,
добавлением, правкой, мультивыбором, массовыми операциями.

- `edit-viewmodel` — `EditViewModel`, `buildEditTree`.
- `edit-screen-search` — адаптивный экран, поиск, чипы.
- `edit-add-sample` — умная вставка со сдвигом номеров и интервалов.
- `edit-status-blank` — интервал для холостых.
- `edit-save-guard` — проверка № по БД.
- `edit-multiselect` — выделение.
- `edit-mass-ops` — массовая правка/удаление.

**Device-check ✅** (01.10.2026): все сценарии прошли.

#### Известные недоработки Редактирования

- Общий сервис сдвига (`SampleShiftPlanner`) — обсуждён, не реализован.
- Правка интервала не сдвигает последующие.
- Undo для массовых операций отсутствует.

### Пачка `report-xlsx` — закрыта

**Формат отчёта:** 1 лист = 1 наряд. Шапка A1:I1..A4:I4. Таблица
проб с цветами как в сверке. Заметки — колонка + лист «Приложения».
Фото — счётчик + картинки. Гиперссылки. Легенда в колонке J.

**Подзаходы:**

- ✅ `xlsx-core` — ручной генератор .xlsx.
- ✅ `xlsx-cells` — заполнение ячеек.
- ✅ `xlsx-styles` — цвета строк.
- ✅ `xlsx-links` — гиперссылки.
- ✅ `xlsx-multi` — N нарядов → N листов + «Приложения».
- ✅ `html-multi` — мультинарядный HTML.
- ✅ `xlsx-ui` — подключение Excel.
- ✅ `report-html-tests` — тесты одиночного HTML.
- ✅ `multi-report-ui` — экран выбора нарядов.

### `xlsx-ui` — критичные фиксы XLSX

- `styles rel` в `workbook.xml.rels` — обязателен.
- `fileVersion`, `workbookPr`, `calcPr` в `workbook.xml`.
- `sheetViews`, `sheetFormatPr` в `sheet.xml`.
- Порядок в `<font>` по ECMA-376.
- `bgColor = fgColor` в solid fill.
- `indexed` + `rgb` одновременно в `<fgColor>`.
- Запись через `ByteArrayOutputStream`.
- `theme` в `<fgColor>` — не использовать.

### Инфраструктурные пачки

- ✅ `docs/5.9-docs-2` — доки после `xlsx-multi` и `html-multi`.
- ✅ `fix/5.9-cleanup` — убраны дубликаты в корне.
- ✅ `5.9-cleanup-2` — warnings компилятора в `XlsxWriter`.
- ✅ `docs/5.9-docs-3` — доки после `xlsx-ui`.
- ✅ `docs/5.9-docs-4` — доки после `report-xlsx`.
- ✅ `docs/5.9-edit-docs` — доки после Редактирования.
- ✅ `docs/5.9-db-docs` — доки после первых пачек БД.
- ✅ `docs/5.9-ai-rules-confirm` — §24 в `AI_RULES.md`.
- ✅ `docs/5.9-db-rollback-docs` — доки после `db-rollback`.
- ✅ `docs/5.9-db-series` — этот заход.

### Пачки Статистики (закрыты ранее)

`stats-screen`, `stats-reactive`, `stats-layout`, `stats-search`,
`stats-search-2`, `stats-order-status`, `report-html`,
`stats-charts`, `stats-fixes`, `stats-compare`, `stats-compare-2`.
`bulk-confirm-2`, `table-responsive`, `row-highlight` — в сверке.

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