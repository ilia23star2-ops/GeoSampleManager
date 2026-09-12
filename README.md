# GeoSample Manager

Android-приложение для управления геохимическими пробами в горнодобывающей
промышленности. Учёт нарядов, импорт описей проб из Excel, сверка фактического
наличия и весовой контроль.

---

## 🧭 Этот файл — главная точка входа для ИИ

Если ты читаешь проект впервые — начни отсюда, затем прочитай по порядку:

1. **`README.md`** (этот файл) — где что лежит, как всё связано.
2. **`PROGRESS.md`** — что уже сделано, что в работе прямо сейчас.
3. **`NEXT_STEPS.md`** — детальный план текущего этапа и открытые вопросы.
4. **`DECISIONS.md`** — все договорённости по UI и логике (читать перед любым
   изменением UI).
5. **`DATABASE.md`** — схема БД, как данные попадают в базу.
6. **`ROADMAP.md`** — общий план развития до релиза MVP.

**Правило:** перед изменением Room-сущностей — сверить с `DATABASE.md` и
`NEXT_STEPS.md`, там указаны запланированные миграции.

---

## Технологии

- **Kotlin**, **Jetpack Compose** (Material 3)
- **Room** (SQLite), KSP
- **Navigation Compose**
- **Kotlin Coroutines + Flow**
- **Свой парсер `.xlsx`** (без Apache POI)
- **Gson** — для настроек и истории импорта

Версии: AGP 8.1.4, Kotlin 1.9.20, compileSdk 34, minSdk 24, Java 17.

---

## Целевые устройства и аудитория

- **Планшеты** — основной сценарий. Телефон — вторичный (адаптивность).
- **Аудитория — простые рабочие.** Крупные элементы, понятные формулировки,
  минимум лишнего, все настройки автосохраняются.

---

## Правила разработки (обязательные)

1. **Всегда присылать полные файлы с полным путём** — не фрагменты.
2. **Комментарии и UI — на русском.** Код — стандартный Kotlin.
3. **Не использовать Apache POI.**
4. **Room-сущности, DAO и репозиторий** — менять только с предупреждением,
   сначала согласовать миграцию.
5. **Настройки автосохраняются.** Кнопок «Сохранить» в настройках нет.
6. **Один заход = один законченный кусок.** Не смешивать инфраструктуру и UI.

---

## Структура проекта (актуальная)

Корень пакета: `app/src/main/java/com/example/geosamplemanager/`

### Точка входа

| Файл | Назначение |
|---|---|
| `MainActivity.kt` | Activity. Ставит `GeoSampleManagerTheme` → `AppScaffold()`. |
| `GeoSampleApp.kt` | `Application`. Инициализирует 3 репозитория и держит их как `lateinit`. |

### `data/` — слой данных

| Файл | Назначение |
|---|---|
| `AppDatabase.kt` | Room-БД. Сейчас **version = 1**, 5 сущностей, без миграций. |
| `DatabaseRepository.kt` | Обёртка над DAO. Единственный источник данных для UI. |

#### `data/entity/` — Room-сущности
`AreaEntity`, `OrderEntity`, `OrderWellEntity`, `SampleEntity`, `SampleNoteEntity`.

#### `data/dao/` — DAO
`AreaDao`, `OrderDao`, `OrderWellDao`, `SampleDao`, `SampleNoteDao`.

`SampleDao` содержит много **атомарных UPDATE** (`setFound`, `setWeight`,
`setHasNote`, `setControlWeight`, `setStatus`, `setSampleType`,
`setMaterialDesc`, `setSampleNumber`, `setWellNumber`, `setInterval`,
`updateAll`) — используются в `ReconciliationViewModel` без чтения строки.

#### `data/excel/` — парсер и анализ Excel

| Файл | Назначение |
|---|---|
| `XlsxReader.kt` | Ленивый парсер `.xlsx`. `readMetadata`, `readSheet`, старый `read`. |
| `ExcelAnalyzer.kt` | Автоопределение шапки и ролей колонок. `Roles`, `SheetAnalysis`, `ColumnProfile`. |
| `ExcelImporter.kt` | Собирает `ParsedOrder` из листа Excel. |
| `ExcelModels.kt` | DTO: `ParsedSample`, `ParsedOrder`. |
| `ImportContext.kt` | Результат разбора листа. |
| `AreaResolver.kt` | Определяет участок по префиксам скважин. |
| `OrderNumberExtractor.kt` | Извлекает номер наряда из имени файла / листа. |
| `SampleFilter.kt` | KEEP / SKIP_BLANK / SKIP_EMPTY, тип и статус пробы. |

#### `data/history/` — история импортов
- `ImportHistory.kt` — `ImportHistoryEntry`, `ImportHistoryItem`.
- `ImportHistoryRepository.kt` — JSON-файл `filesDir/import_history.json`.

#### `data/settings/` — настройки импорта
- `ImportSettings.kt` — модель настроек + дефолтные словари + `normalizeHeaderWord`.
- `SettingsRepository.kt` — JSON-файл `filesDir/import_settings.json`, экспорт/импорт через `Uri`.

### `ui/navigation/` — навигация

| Файл | Назначение |
|---|---|
| `NavGraph.kt` | `AppScaffold()` — Drawer + TopAppBar + NavHost. |
| `Screen.kt` | Enum вкладок: MAIN, ADD, SEARCH, STATS, EDIT, DB, SETTINGS. |

### `ui/screens/` — экраны

**Основные экраны (по вкладкам):**

| Файл | Что делает |
|---|---|
| `MainScreen.kt` | Заглушка. |
| `AddScreen.kt` + `AddViewModel.kt` | Импорт Excel: выбор файла, очередь листов, предпросмотр, конфликты, история. |
| `SearchScreen.kt` | «Сверка и поиск» — основной рабочий экран. |
| `StatsScreen.kt` | Заглушка. |
| `EditScreen.kt` | Заглушка. |
| `DbScreen.kt` + `DbViewModel.kt` | Управление БД: участки, наряды, пробы, бэкап. |
| `SettingsScreen.kt` + `SettingsViewModel.kt` | Настройки импорта, словари, экспорт/импорт. |

**Модели и состояние сверки:**

| Файл | Что содержит |
|---|---|
| `ReconciliationModels.kt` | `SampleGroup`, `SampleRow`, `GroupStats`, `MatchInfo`, `UndoAction`, `SampleType`, `SampleStatus`, фильтры, статистика, поиск. |
| `ReconciliationState.kt` | `ReconciliationState` — @Stable класс: группы, undo/redo (20 шагов), раскрытие, все действия над пробами. |
| `ReconciliationMapper.kt` | `SampleEntity ↔ SampleRow`, `buildSampleGroup`. |
| `ReconciliationViewModel.kt` | AndroidViewModel. Загружает данные из БД, синхронизирует действия. |

**Диалоги и вспомогательные компоненты:**

| Файл | Что содержит |
|---|---|
| `ReconciliationDialogs.kt` | Все диалоги сверки: `WeightDialog`, `CharacteristicDialog`, `NoteDialog` (ждёт 5.5), `EditSampleDialog`, `DeleteSampleDialog`, `OrderSettingsDialog`, `ConfirmResetBlankWeightDialog`, `BulkActionsDialog`, `AlreadyFoundDialog`, `ImportErrorDialog`, `PostponedDialog`. |
| `KeywordsDialogs.kt` | `HeaderKeywordsDialog` — редактирование пользовательских словарей. |
| `MappingEditorDialog.kt` | Ручной маппинг колонок Excel. |
| `RememberChanges.kt` | DTO для «запомнить для будущих импортов». |
| `RoleColors.kt` | Цвета ролей колонок + `roleTitle`, `ALL_ROLES`. |
| `SampleDisplay.kt` | Отображение `ParsedSample`: тип, заголовок скважины/выработки. |
| `SamplesTable.kt` | Таблица предпросмотра проб в диалоге импорта. |

### `ui/theme/`
`Color.kt`, `Theme.kt`, `Type.kt` — светлая/тёмная схемы.

### `res/`
`values/` (строки, темы), `xml/` (пока нет — `file_paths.xml` появится в 5.5.1),
`drawable/`, `mipmap/`.

---

## Как запускается приложение

1. `MainActivity` → `GeoSampleManagerTheme` → `AppScaffold()`.
2. `GeoSampleApp.onCreate` создаёт:
    - `DatabaseRepository(applicationContext)`
    - `SettingsRepository(applicationContext)`
    - `ImportHistoryRepository(applicationContext)`
3. ViewModel'и берут репозиторий через `(application as GeoSampleApp).repository`.

---

## Ключевые сценарии

- **Импорт Excel** → `AddScreen` + `AddViewModel` + `data/excel/*`.
- **Сверка и поиск** → `SearchScreen` + `Reconciliation*` + `data/dao/SampleDao`.
- **Управление БД** → `DbScreen` + `DbViewModel`.
- **Настройки** → `SettingsScreen` + `SettingsViewModel` + `SettingsRepository`.
- **Заметки и фото (в работе, этап 5.5)** → см. `NEXT_STEPS.md`.

---

## Что НЕ хранится в БД

- Сырые заголовки Excel, номер строки, дата отбора, примечания из Excel.
- Настройки холостых/ВК по наряду — живут в памяти `ReconciliationState`
  (см. открытые вопросы в `NEXT_STEPS.md`).

---

## Быстрая шпаргалка «куда идти, если…»

| Задача | Файлы |
|---|---|
| Изменить логику поиска | `ReconciliationModels.kt`, `ReconciliationState.kt` |
| Изменить отображение строки пробы | `SearchScreen.kt` → `SampleRowItem` |
| Добавить поле в пробу | `SampleEntity.kt` + `SampleDao.kt` + `ReconciliationMapper.kt` + миграция в `AppDatabase.kt` + `DATABASE.md` |
| Изменить парсинг Excel | `data/excel/*` |
| Изменить настройки импорта | `ImportSettings.kt` + `SettingsRepository.kt` + `SettingsScreen.kt` |
| Добавить диалог на экране сверки | `ReconciliationDialogs.kt` + подключить в `SearchScreen.kt` |

---

## Файлы документации

- `README.md` — этот файл.
- `PROGRESS.md` — статус проекта.
- `NEXT_STEPS.md` — текущий этап и план заходов.
- `DECISIONS.md` — все решения по UI и логике.
- `DATABASE.md` — схема БД и путь данных.
- `ROADMAP.md` — план развития до релиза MVP.