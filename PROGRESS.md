# PROGRESS.md — история заходов

## 5.9 серия — допиливание вкладок

**Дата:** 2026-09-29 … 2026-10-01
**Ветка:** `feature/5.9-full-project`
**Контекст:** после релизной серии 5.8.11 — планомерное закрытие
всех вкладок, кроме сверки. Закрыты: Статистика, Редактирование.
В работе — БД.

### Вкладка «БД» — в работе

**Дата:** 2026-10-01
**Пачки:** `db-style`, `db-info`, `db-backup-v2`, `db-backup-fix`,
`db-restore-v2` (с 2 hotfix), `db-rollback`.

**Что было:** вкладка БД — просмотр участков/нарядов/проб,
добавление участков и нарядов, удаление, простой бэкап файла `.db`
без фото. `Toast` вместо Snackbar. Копирование `.db` без
`wal_checkpoint` — потенциально неконсистентная копия.

**Что стало:** полноценный менеджер БД с экспортом/импортом,
инфо-панелью, авто-бэкапами, откатом.

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
- `DbViewModel.restoreFromUri`:
  1. Авто-бэкап текущей БД (публично + приватно).
  2. `AppDatabase.closeAndReset()`.
  3. Удаление `.db`, `.db-wal`, `.db-shm`.
  4. Очистка `sample_photos/`.
  5. Распаковка нового `.db` + фото.
  6. `GeoSampleApp.resetRepository()`.
- `AppDatabase.closeAndReset()` — новый метод.
- `GeoSampleApp.resetRepository()` — новый метод.
- `RestoreState` — sealed class (Idle / InProgress / Done / Error).
- Overlay с прогрессом на время импорта.
- Кнопка «Импорт» в шапке БД.

**Hotfix 1:** `activity.recreate()` не сбрасывает ViewModel
(переживает реконфигурацию). Заменено на `killProcess` + `AlarmManager`.

**Hotfix 2:** `killProcess` + `AlarmManager` не работают на новых
Android. Финальное решение — `startActivity(MainActivity,
NEW_TASK|CLEAR_TASK)` + `finish()`. Полный сброс Compose-стека:
NavController, ViewModelStore, все экраны — без закрытия процесса.

**Device-check ✅** (01.10.2026): экспорт + импорт + пересоздание
стека работают, данные восстанавливаются, авто-бэкап появляется в
Загрузках.

#### `db-rollback` — откат к авто-бэкапу

- Кнопка «Откатиться к авто-бэкапу» на вкладке БД.
- `RollbackBackups` — чистая логика: парсер имени
  `pre_restore_YYYYMMDD_HHmm.gsmbackup`, фильтр по имени и типу,
  сортировка по убыванию даты, ротация (`MAX_KEEP = 5`).
- `GsmBackupReader` — перегрузки для `File` (чтение из приватной
  папки без ContentResolver). Общая логика вынесена в
  `readManifestFromStream` / `extractFromStream`.
- `DatabaseRepository.getRollbackBackupsDir()` — путь к
  `filesDir/db_backups/`.
- `DbViewModel`: `loadRollbackBackups`, `rollbackFromInternal`,
  общий метод `performReplacement` для import/rollback
  (с префиксом авто-бэкапа и лямбдой extract).
- `DbRollbackDialog` — список точек отката (дата, размер,
  счётчики из манифеста).
- `DbRollbackConfirmDialog` — двойное подтверждение (показ
  содержимого + предупреждение о замене).
- Авто-бэкап `pre_rollback_*` перед откатом (приватно +
  публично).
- Ротация `pre_restore_*` после успешной замены (5 последних).
- SAF-выбор внешнего файла — **убран** (упрощение, меньше
  путаницы с папкой Загрузки).
- Тесты: `RollbackBackupsTest` — 12 тестов.

**Device-check ✅** (02.10.2026): список бэкапов, откат,
авто-бэкап перед откатом, ротация — все сценарии прошли.

### Вкладка «Редактирование» — закрыта

**Дата:** 2026-10-01
**Пачки:** `edit-viewmodel`, `edit-screen-search`, `edit-add-sample`,
`edit-status-blank`, `edit-save-guard`, `edit-multiselect`,
`edit-mass-ops`.

**Что было:** `EditScreen.kt` — пустая заглушка с одним текстом
«Здесь будет дерево проб и форма редактирования». ViewModel
отсутствовал.

**Что стало:** полноценный редактор проб с деревом, поиском,
фильтрами, добавлением, правкой, мультивыбором и массовыми
операциями.

#### `edit-viewmodel` — ViewModel вкладки

- `EditViewModel` — реактивная загрузка дерева участок → наряд
  → проба через `combine(areasFlow, ordersFlow, samplesFlow)`.
- `EditTreeData`, `EditAreaUi`, `EditOrderUi`.
- Чистая функция `buildEditTree` — сортировка `(wellNumber, numberInWell)`,
  фильтрация сирот.
- `findSample`, `findOrder`.
- Тесты: `EditViewModelTest` — 14 тестов.

#### `edit-screen-search` — экран с поиском и фильтрами

- Адаптивный экран: широкий (≥600 dp) — дерево 35% + карточка;
  узкий — дерево во весь экран.
- Строка поиска: подстрока по № пробы / № скважины / характеристике.
- Фильтры-чипы.
- Авто-разворот при поиске.
- Тесты: `EditScreenTreeItemsTest` (8), `EditSearchFilterTest` (18).

#### `edit-add-sample` — умная вставка

Подзаходы /1, /2, /3.

- Префикс наряда — общий буквенный префикс скважин.
- Скважина: dropdown с фильтром или ручной ввод.
- № пробы: автоподстановка `wellNumber + (max+1)`.
- Интервал `from` = `to` предыдущей не-холостой.
- Валидация в реальном времени.
- Конфликт № пробы: ⚠ + второй диалог «Со сдвигом / Отмена».
- Сдвиг номеров и интервалов при вставке.
- HOTFIX UNIQUE: сдвиги применяются в **обратном порядке**.
- Холостая: интервал null, сдвиг интервалов не выполняется.
- Тесты: `EditAddSampleTest` — 21 тест.

#### `edit-status-blank` — интервал для холостых

- В `EditSampleDialog` при статусе «Холостая» интервал скрыт.
- При возврате — восстанавливается.
- При сохранении — «—» (null).
- Для не-холостой интервал обязателен.

#### `edit-save-guard` — проверка № по БД

- `SampleDao.findByOrderAndNumber`, `DatabaseRepository.findSampleByOrderAndNumber`.
- `saveEditedRow` / `saveSample` проверяют по БД, не по state.
- `findConflictForEdit` — синхронная проверка для диалога.
- `humanSaveError` — человеческий текст вместо `UNIQUE constraint`.
- Откат state при ошибке БД.

#### `edit-multiselect` — выделение

- Режим `multiselectMode` + `selectedIds`.
- Вход: длинный тап или кнопка «Выделять».
- BottomBar: «N проб · [Изменить] [Удалить] [Снять] [Выход]».
- Сброс при смене поиска/фильтров/наряда.
- Чистые функции: `applyMultiselectToggle`, `computeSelectionLabel`.
- Тесты: `EditMultiselectTest` — 13 тестов.

#### `edit-mass-ops` — массовые операции

Подзаходы /1, /2.

- `MassEditFields` — характеристика, тип, статус (независимо).
- `MassEditDialog` — чекбокс на каждое поле.
- `MassDeleteDialog` — чекбокс «Пересчитать №».
- `applyMassEdit` — пакетная запись.
- `deleteSelected` — последовательное удаление.
- **FIX /2:** синхронизация `status` ↔ `weightControl`:
  CONTROL → `weightControl = true`; NORMAL/BLANK → `false`;
  null → не трогаем.
- Тесты: `EditMassOpsTest` — 14 тестов.

**Device-check ✅** (01.10.2026): все сценарии прошли.

#### Известные недоработки Редактирования

- Общий сервис сдвига (`SampleShiftPlanner`) — обсуждён, не реализован.
- Правка интервала (from/to) не сдвигает последующие и не
  предупреждает о разрывах/пересечениях.
- RENAME не проверяет интервалы — согласовано: интервалы едут с
  пробой, дырки допускаются.
- Undo для массовых операций отсутствует.

### Пачка `report-xlsx` — закрыта

**Формат отчёта:** 1 лист = 1 наряд. Шапка (участок, №, дата)
объединена A1:I1..A4:I4. Таблица проб с цветами как в сверке.
Заметки — колонка + лист «Приложения». Фото — счётчик + картинки
в блоке приложений. Гиперссылки внутри файла. Легенда цветов
в колонке J.

**Подзаходы:**

- ✅ `xlsx-core` — ручной генератор .xlsx (zip + XML).
- ✅ `xlsx-cells` — заполнение ячеек из `ReportData`.
- ✅ `xlsx-styles` — цвета строк, жирный.
- ✅ `xlsx-links` — гиперссылки.
- ✅ `xlsx-multi` — N нарядов → N листов + общий лист «Приложения».
- ✅ `html-multi` — мультинарядный HTML.
- ✅ `xlsx-ui` — подключение Excel + большой блок доработок.
- ✅ `report-html-tests` — тесты на одиночный HTML-генератор.
- ✅ `multi-report-ui` — экран выбора нарядов.

### `xlsx-ui` — детально

**Инфраструктурные фиксы XLSX (критичные):**

- `styles rel` в `workbook.xml.rels` — обязателен.
- `fileVersion`, `workbookPr`, `calcPr` в `workbook.xml`.
- `sheetViews`, `sheetFormatPr` в `sheet.xml`.
- Порядок в `<font>` по ECMA-376.
- `bgColor = fgColor` в solid fill.
- `indexed` + `rgb` одновременно в `<fgColor>`.
- Запись через `ByteArrayOutputStream` (не `zip.finish()`).
- `theme` в `<fgColor>` — **не использовать**.

### `multi-report-ui` — детально

- `MultiReportScreen` — полноэкранный overlay.
- `detectDuplicateSheetNames`, `prepareOrdersForReport`.
- Кнопки Excel / HTML.
- Диалог при совпадении имён листов.

**Device-check ✅** (01.10.2026): мультиотчёт Excel и HTML работают.

### Инфраструктурные пачки

- ✅ `docs/5.9-docs-2` — доки после `xlsx-multi` и `html-multi`.
- ✅ `fix/5.9-cleanup` — убраны дубликаты в корне.
- ✅ `5.9-cleanup-2` — warnings компилятора в `XlsxWriter`.
- ✅ `docs/5.9-docs-3` — доки после `xlsx-ui`.
- ✅ `docs/5.9-edit-docs` — доки после Редактирования.
- ✅ `docs/5.9-db-docs` — доки после пачек БД.
- ✅ `docs/5.9-db-rollback-docs` — доки после `db-rollback`.

### Пачки Статистики (закрыты ранее)

`stats-screen`, `stats-reactive`, `stats-layout`, `stats-search`,
`stats-search-2`, `stats-order-status`, `report-html`, `stats-charts`,
`stats-fixes`, `stats-compare`, `stats-compare-2`.
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
- `5.8.11-e4-tests` — покрытие парсера (Weights/Find/Paused).

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