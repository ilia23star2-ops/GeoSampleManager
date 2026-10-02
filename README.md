# GeoSample Manager

Android-приложение для управления геохимическими пробами в горнодобывающей
промышленности. Учёт нарядов, импорт описей проб из Excel, сверка фактического
наличия, весовой контроль, заметки и фото, голосовой помощник.

---

## 🧭 Этот файл — главная точка входа для ИИ

**Читай в таком порядке:**

1. **`AI_RULES.md`** — как работать. **Обязательно первым.**
2. **`CONTEXT_BRIEF.md`** — где мы сейчас (одна страница).
3. **`README.md`** (этот файл) — где что лежит.
4. **`PROGRESS.md`** — полная история.
5. **`NEXT_STEPS.md`** — текущий заход.
6. **`DECISIONS.md`** — все решения по UI и логике.
7. **`DATABASE.md`** — схема БД.
8. **`ROADMAP.md`** — план до релиза.
9. **`VOICE.md`** — спецификация ГП.
10. **`GLOSSARY.md`** — термины.
11. **`ISSUES.md`** — открытые проблемы.
12. **`TESTING.md`** — что тестировать.

**Правило:** перед изменением Room-сущностей — сверить с `DATABASE.md` и
`NEXT_STEPS.md`, там указаны запланированные миграции.

**Первым делом ИИ спрашивает: «где ты — дома или на работе?»** И дома,
и на работе — Android Studio + git в терминале. Fallback — github.dev
или github.com. См. `AI_RULES.md` §20.

---

## Технологии

- **Kotlin**, **Jetpack Compose** (Material 3)
- **Room** (SQLite), KSP — **version = 2**
- **Navigation Compose**
- **Kotlin Coroutines + Flow**
- **Vosk** — офлайн-распознавание речи
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
6. **Один заход = 1–3 файла.** Больше — делить.
7. **После кода — раздел «Как проверить».**
8. **Стоп-сигналы** — см. `AI_RULES.md` §17.
9. **Работа с двух машин** — см. ниже.

---

## Работа с двух машин

**Контекст:** проект разрабатывается на двух машинах.

- **Дома** — Android Studio, git в терминале. Полный цикл: сборка,
  тесты, эмулятор, реальное устройство.
- **На работе** — тоже Android Studio + git в терминале. Полный цикл.
- **Fallback** — если AS недоступен: `github.dev` (браузерный
  VS Code) или `github.com` (веб).

### Режим А — Android Studio (основной)

1. `git checkout feature/X.Y-xxx` → `git pull`.
2. Создать ветку пачки: `git checkout -b fix/X.Y-bundle` →
   `git push -u origin fix/X.Y-bundle`.
3. Правки в IDE.
4. Локально: `Build → Make Project`, юнит-тесты, эмулятор, устройство.
5. **Ждать «норм»** от пользователя.
6. **Коммит + push** (один общий на пачку).
7. **Merge пачки в фичу** — локально в терминале.
8. Удалить ветку пачки.

**Важно:** git-операции — **только через Terminal** (`Alt+F12`).
Интерфейс AS (Commit/Push) ломает репо.

### Режим Б — github.dev (fallback)

1. Открыть репозиторий на github.com → нажать `.`.
2. Создать ветку пачки от фичи.
3. Править файлы.
4. Source Control (`Ctrl+Shift+G`) → один коммит → Commit & Push.
5. PR → base: фича → Merge → Delete branch.

### Режим В — github.com (fallback)

Используется, если github.dev недоступен.

1. Репозиторий → `main ▾` → Create branch: `fix/X.Y-bundle`.
2. **Каждый файл — отдельный коммит:**
   - Файл 1: открыть → ✏️ → правки → Commit changes → `X.Y/1 — Имя`.
   - Файл 2: ... → `X.Y/2 — Имя`.
3. PR → base: фича → Merge → Delete branch.

### Правила гигиены

- **Всегда `pull` перед началом.**
- **Никогда не работать напрямую в `main`.**
- **Одна задача = одна ветка.**
- **Коммит после каждого захода.**
- **Пуш в конце сессии.**
- **После merge пачки — удалить.**
- **После merge фичи в `main` — НЕ удалять (архив).**

### Схема веток

- `main` — стабильная.
- `feature/<заход>-<краткое>` — новая функциональность.
- `fix/<заход>-<краткое>` — баг, пачка, подзаход.
- `docs/<заход>-<краткое>` — документация.

### CI (GitHub Actions)

**Автоматически:**
- **`unit-tests`** — на каждый PR в `main` и `feature/*`.
- **`build-apk`** — если на PR висит лейбл `build-apk`.

**Вручную (Actions → Build & Test → Run workflow):**
- Прогнать тесты без PR.
- Собрать APK без лейбла.

**Артефакты** на странице запуска:
- `app-debug` — APK (если `build_apk`).
- `test-report` — HTML-отчёт (если `run_tests`).

Vosk-модель не коммитится в Git. В CI она выкачивается из релиза GitHub
(`models-v1`) в `build.yml`. Дома и на работе — локально в
`app/src/main/assets/vosk-model-small-ru-0.22/`.

**Подробности:** `AI_RULES.md` §20.

---

## Структура проекта

Корень пакета: `app/src/main/java/com/example/geosamplemanager/`

### Точка входа
| Файл | Назначение |
|---|---|
| `MainActivity.kt` | Activity. `ReadyContent` — пересбор поддерева через `key(tick)` + `SimpleViewModelStoreOwner`. |
| `GeoSampleApp.kt` | Application. Репозитории, `resetRepository()`, `requestRestart()`. |

### `data/` — слой данных
| Файл | Назначение |
|---|---|
| `AppDatabase.kt` | Room-БД. **version = 2**, 6 сущностей, миграция 1→2. `closeAndReset()`, `buildTemp()`. |
| `DatabaseRepository.kt` | Обёртка над DAO. `checkpointWal()`, `clearAllData()`, `getDbInfo()`. |

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
`VoiceController` — Vosk + TTS.

#### `data/voice/`
`VoiceDictionary`, `VoiceNumberParser`, `VoiceSegmenter`,
`VoiceSettings`, `VoicePrefixResolver`, `VoiceSearch`, `VoiceCommand`,
`VoiceCommandParser`, `VoiceOrdinals`, `VoiceSession`, `VoiceSpeaker`,
`VoiceSearchRepository`, `AnswerState`, `UnifiedSearch`.

#### `data/backup/` — бэкапы и авто-бэкапы
| Файл | Назначение |
|---|---|
| `GsmBackupReader.kt` | Чтение `.gsmbackup` (zip + manifest + db + photos). |
| `GsmBackupWriter.kt` | Запись `.gsmbackup`. |
| `RollbackBackups.kt` | Парсер `pre_*` имён, ротация, merge. |
| `PublicBackupsLister.kt` | Листинг публичных `.gsmbackup` через MediaStore. |
| `PublicBackupsMigrator.kt` | Ленивая миграция старых бэкапов из корня. |
| `BackupManagerStats.kt` | Сводка по бэкапам. |

#### `data/merge/` — слияние БД
| Файл | Назначение |
|---|---|
| `MergeModels.kt` | Планы, конфликты, дерево, resolutions. |
| `MergeEngine.kt` | Движок: план, разрешение конфликтов, apply. |
| `MergeRunner.kt` | Оркестратор apply-фаз. |

#### `data/compare/` — сравнение БД
| Файл | Назначение |
|---|---|
| `CompareModels.kt` | Дерево сравнения, `CompareResult`. |
| `CompareEngine.kt` | `buildResult()` — 4 дерева. |

### `ui/navigation/`
| Файл | Назначение |
|---|---|
| `NavGraph.kt` | `AppScaffold(initialRoute)`. |
| `Screen.kt` | Перечисление экранов. |
| `SimpleViewModelStoreOwner.kt` | Владелец VMStore для пересбора поддерева. |

### `ui/screens/`
Основные экраны: `MainScreen`, `AddScreen` + `AddViewModel`,
`SearchScreen`, `StatsScreen`, `EditScreen` + `EditViewModel`,
`DbScreen` + `DbViewModel`, `SettingsScreen` + `SettingsViewModel`.

Модели и состояние сверки: `ReconciliationModels`,
`ReconciliationState`, `ReconciliationMapper`, `ReconciliationViewModel`.

Диалоги сверки: `ReconciliationDialogs`, `KeywordsDialogs`,
`MappingEditorDialog`, `VoiceDialog`.

Вспомогательные: `RememberChanges`, `RoleColors`, `SampleDisplay`,
`SamplesTable`.

**Диалоги вкладки БД:**
`DbBackupDialog`, `DbRestoreDialog`, `DbInfoDialog`,
`DbRollbackDialog`, `DbCleanDialog`, `DbImportPickerDialog`,
`BackupManagerDialog`.

**Экраны БД:**
`MergeWizard`, `MergeConflictsScreen`, `DbCompareScreen`.

**Модели вкладки БД:**
`CleanConfirmState`.

### `ui/theme/`
`Color.kt`, `Theme.kt`, `Type.kt`.

---

## Как запускается приложение

1. `MainActivity` → `GeoSampleManagerTheme` → `AppRoot()`.
2. `AppRoot` ждёт загрузки Vosk-модели → `ReadyContent(app)`.
3. `ReadyContent` — `key(tick)` + `SimpleViewModelStoreOwner` +
   `AppScaffold(initialRoute)`.
4. `GeoSampleApp.onCreate` создаёт:
   - `DatabaseRepository`
   - `SettingsRepository`
   - `ImportHistoryRepository`
   - `VoiceSettingsRepository`
5. ViewModel'и берут репозиторий через `(application as GeoSampleApp).repository`.

**После замены БД** (импорт/откат/слияние/очистка) — `requestRestart()`
пересобирает поддерево без пересоздания Activity. См. `CONTEXT_BRIEF.md`.

---

## Ключевые сценарии

- **Импорт Excel** → `AddScreen` + `data/excel/*`.
- **Сверка и поиск** → `SearchScreen` + `Reconciliation*` + `SampleDao`.
- **Заметки и фото** → `NotePhotoDialog` + `PhotoStorage` + `SampleImageDao`.
- **Управление БД** → `DbScreen` + `DbViewModel`.
- **Бэкапы и авто-бэкапы** → `data/backup/*`.
- **Слияние двух БД** → `data/merge/*` + `MergeWizard` + `MergeConflictsScreen`.
- **Сравнение двух БД** → `data/compare/*` + `DbCompareScreen`.
- **Настройки** → `SettingsScreen` + `SettingsRepository`.
- **Голосовой помощник** → `VOICE.md` + `data/voice/*`.

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
| Бэкап/восстановление | `data/backup/*` + `DbViewModel` |
| Слияние БД | `data/merge/*` + `MergeWizard` + `MergeConflictsScreen` |
| Сравнение БД | `data/compare/*` + `DbCompareScreen` |
| Термин — что значит | `GLOSSARY.md` |
| Что тестировать | `TESTING.md` |
| Открытые проблемы | `ISSUES.md` |

---

## Файлы документации

- `AI_RULES.md` — правила работы ИИ.
- `CONTEXT_BRIEF.md` — где мы сейчас.
- `README.md` — этот файл.
- `PROGRESS.md` — статус проекта.
- `NEXT_STEPS.md` — текущий этап.
- `DECISIONS.md` — решения по UI и логике.
- `DATABASE.md` — схема БД.
- `ROADMAP.md` — план до релиза.
- `VOICE.md` — спецификация ГП.
- `GLOSSARY.md` — термины.
- `ISSUES.md` — проблемы.
- `TESTING.md` — тесты.