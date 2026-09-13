# GeoSample Manager

Android-приложение для управления геохимическими пробами в горнодобывающей
промышленности. Учёт нарядов, импорт описей проб из Excel, сверка фактического
наличия, весовой контроль, заметки и фото, голосовой помощник.

---

## 🧭 Этот файл — главная точка входа для ИИ

Если ты читаешь проект впервые — начни отсюда, затем прочитай по порядку:

1. **`README.md`** (этот файл) — где что лежит, как всё связано.
2. **`PROGRESS.md`** — что уже сделано, что в работе прямо сейчас.
3. **`NEXT_STEPS.md`** — детальный план текущего этапа.
4. **`DECISIONS.md`** — все договорённости по UI и логике.
5. **`DATABASE.md`** — схема БД, как данные попадают в базу.
6. **`ROADMAP.md`** — общий план развития до релиза MVP.
7. **`VOICE.md`** — полная спецификация голосового помощника.

**Правило:** перед изменением Room-сущностей — сверить с `DATABASE.md` и
`NEXT_STEPS.md`, там указаны запланированные миграции.

---

## Технологии

- **Kotlin**, **Jetpack Compose** (Material 3)
- **Room** (SQLite), KSP — **version = 2**
- **Navigation Compose**
- **Kotlin Coroutines + Flow**
- **Vosk** — офлайн-распознавание речи (план, этап 5.8)
- **Свой парсер `.xlsx`** (без Apache POI)
- **Gson** — настройки и история импорта

Версии: AGP 8.1.4, Kotlin 1.9.20, compileSdk 34, minSdk 24, Java 17.

---

## Целевые устройства и аудитория

- **Планшеты** — основной сценарий.
- **Аудитория — простые рабочие.** Крупные элементы, понятные формулировки.
- **Все настройки автосохраняются.**

---

## Правила разработки (обязательные)

1. **Всегда присылать полные файлы с полным путём** — не фрагменты.
2. **Комментарии и UI — на русском.**
3. **Не использовать Apache POI.**
4. **Room-сущности, DAO и репозиторий** — менять только с предупреждением
   и миграцией.
5. **Настройки автосохраняются.**
6. **Один заход = один законченный кусок.**
7. **После кода — раздел «Как проверить».**

---

## Структура проекта

Корень пакета: `app/src/main/java/com/example/geosamplemanager/`

### Точка входа
| Файл | Назначение |
|---|---|
| `MainActivity.kt` | Activity. |
| `GeoSampleApp.kt` | Application. Инициализирует 3 репозитория. |

### `data/` — слой данных
| Файл | Назначение |
|---|---|
| `AppDatabase.kt` | Room-БД. **version = 2**, 6 сущностей, миграция 1→2. |
| `DatabaseRepository.kt` | Обёртка над DAO. |

#### `data/entity/`
`AreaEntity`, `OrderEntity`, `OrderWellEntity`, `SampleEntity`,
`SampleNoteEntity`, `SampleImageEntity`.

#### `data/dao/`
`AreaDao`, `OrderDao`, `OrderWellDao`, `SampleDao`, `SampleNoteDao`,
`SampleImageDao`.

`SampleDao` — много атомарных UPDATE.

#### `data/excel/`
`XlsxReader`, `ExcelAnalyzer`, `ExcelImporter`, `ExcelModels`,
`ImportContext`, `AreaResolver`, `OrderNumberExtractor`, `SampleFilter`.

#### `data/history/`
`ImportHistory`, `ImportHistoryRepository`.

#### `data/settings/`
`ImportSettings`, `SettingsRepository`.

#### `data/util/`
`PhotoStorage` — работа с фото.

#### `data/voice/` (план, 5.8.2+)
`VoiceNumberParser`, `VoicePrefixResolver`, `VoiceSearch`,
`VoiceSegmenter`, `VoiceSettings`.

### `ui/navigation/`
`NavGraph.kt` (AppScaffold), `Screen.kt`.

### `ui/screens/`
Основные экраны: `MainScreen`, `AddScreen` + `AddViewModel`,
`SearchScreen`, `StatsScreen`, `EditScreen`, `DbScreen` + `DbViewModel`,
`SettingsScreen` + `SettingsViewModel`.

Модели и состояние сверки: `ReconciliationModels`,
`ReconciliationState`, `ReconciliationMapper`, `ReconciliationViewModel`.

Диалоги: `ReconciliationDialogs`, `KeywordsDialogs`,
`MappingEditorDialog`, `VoiceDialog`.

Вспомогательные: `RememberChanges`, `RoleColors`, `SampleDisplay`,
`SamplesTable`.

### `ui/theme/`
`Color.kt`, `Theme.kt`, `Type.kt`.

---

## Как запускается приложение

1. `MainActivity` → `GeoSampleManagerTheme` → `AppScaffold()`.
2. `GeoSampleApp.onCreate` создаёт:
    - `DatabaseRepository`
    - `SettingsRepository`
    - `ImportHistoryRepository`
3. ViewModel'и берут репозиторий через `(application as GeoSampleApp).repository`.

---

## Ключевые сценарии

- **Импорт Excel** → `AddScreen` + `data/excel/*`.
- **Сверка и поиск** → `SearchScreen` + `Reconciliation*` + `SampleDao`.
- **Заметки и фото** → `NotePhotoDialog` + `PhotoStorage` + `SampleImageDao`.
- **Управление БД** → `DbScreen` + `DbViewModel`.
- **Настройки** → `SettingsScreen` + `SettingsRepository`.
- **Голосовой помощник** → см. `VOICE.md` (в разработке).

---

## Быстрая шпаргалка «куда идти, если…»

| Задача | Файлы |
|---|---|
| Логика поиска | `ReconciliationModels.kt`, `ReconciliationState.kt` |
| Отображение строки | `SearchScreen.kt` → `SampleRowItem` |
| Новое поле в пробу | `SampleEntity.kt` + DAO + маппер + миграция |
| Парсинг Excel | `data/excel/*` |
| Настройки импорта | `ImportSettings.kt` + `SettingsRepository.kt` |
| Диалог сверки | `ReconciliationDialogs.kt` + `SearchScreen.kt` |
| Логика ГП | `VOICE.md` + `data/voice/*` |

---

## Файлы документации

- `README.md` — этот файл.
- `PROGRESS.md` — статус проекта.
- `NEXT_STEPS.md` — текущий этап.
- `DECISIONS.md` — все решения по UI и логике.
- `DATABASE.md` — схема БД.
- `ROADMAP.md` — план до релиза.
- `VOICE.md` — спецификация ГП.