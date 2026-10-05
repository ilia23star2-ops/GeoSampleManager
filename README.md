# GeoSample Manager

Android-приложение для управления геохимическими пробами в горнодобывающей
промышленности. Учёт нарядов, импорт описей проб из Excel, сверка фактического
наличия, весовой контроль, заметки и фото, голосовой помощник, журнал аудита.

---

## 🧭 Этот файл — главная точка входа

**Документы разработки — в папке `docs/`.**
Корень репозитория — только код и этот файл.

### Порядок чтения (для ИИ — обязательно первым)

1. **`docs/AI_RULES.md`** — как работать. **Обязательно первым.**
2. **`docs/CONTEXT_BRIEF.md`** — где мы сейчас (одна страница).
3. **`README.md`** — этот файл (в корне).
4. **`docs/PROGRESS.md`** — полная история.
5. **`docs/NEXT_STEPS.md`** — текущий заход.
6. **`docs/DECISIONS.md`** — все решения по UI и логике.
7. **`docs/DATABASE.md`** — схема БД.
8. **`docs/ROADMAP.md`** — план до релиза.
9. **`docs/VOICE.md`** — спецификация ГП.
10. **`docs/GLOSSARY.md`** — термины.
11. **`docs/ISSUES.md`** — открытые проблемы.
12. **`docs/TESTING.md`** — что тестировать.
13. **`docs/SCENARIOS.md`** — сценарии вкладки «Сверка».
14. **`docs/SEARCH_MODEL.md`** — единая модель поиска.

**Правило:** перед изменением Room-сущностей — сверить с
`docs/DATABASE.md` и `docs/NEXT_STEPS.md`.

**Первым делом ИИ спрашивает: «где ты — дома или на работе?»** И дома,
и на работе — Android Studio + git в терминале. Fallback — github.dev
или github.com. См. `docs/AI_RULES.md` §20.

---

## Технологии

- **Kotlin**, **Jetpack Compose** (Material 3)
- **Room** (SQLite), KSP — **основная версия = 2**,
  отдельная `logs.db` (version = 1)
- **Navigation Compose**
- **Kotlin Coroutines + Flow**
- **Vosk** — офлайн-распознавание речи
- **Свой парсер `.xlsx`** (без Apache POI)
- **Gson** — настройки, история импорта, details журнала

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
8. **Стоп-сигналы** — см. `docs/AI_RULES.md` §17.
9. **Работа с двух машин** — см. ниже.

---

## Работа с двух машин

**Контекст:** проект разрабатывается на двух машинах.

- **Дома** — Android Studio, git в терминале. Полный цикл.
- **На работе** — тоже Android Studio + git в терминале. Полный цикл.
- **Fallback** — если AS недоступен: `github.dev` или `github.com`.

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

**Подробности:** `docs/AI_RULES.md` §20.

---

## Структура проекта

Корень пакета: `app/src/main/java/com/example/geosamplemanager/`

### Точка входа
| Файл | Назначение |
|---|---|
| `MainActivity.kt` | Activity. `ReadyContent` — пересбор поддерева через `key(tick)` + `SimpleViewModelStoreOwner`. Логирование lifecycle (`onResume` / `onPause`). |
| `GeoSampleApp.kt` | Application. Репозитории, `resetRepository()`, `requestRestart()`, `LogWriter.init()`, `CrashHandler.install()`. |

### `data/` — слой данных
| Файл | Назначение |
|---|---|
| `AppDatabase.kt` | Основная Room-БД. **version = 2**, 6 сущностей, миграция 1→2. `closeAndReset()`, `buildTemp()`. |
| `DatabaseRepository.kt` | Обёртка над DAO. `checkpointWal()`, `clearAllData()`, `getDbInfo()`, `runDiagnostics()`, `applyDiagnosticsFixes()`. |

#### `data/entity/`
`AreaEntity`, `OrderEntity`, `OrderWellEntity`, `SampleEntity`,
`SampleNoteEntity`, `SampleImageEntity`.

#### `data/dao/`
`AreaDao`, `OrderDao`, `OrderWellDao`, `SampleDao`, `SampleNoteDao`,
`SampleImageDao`.

#### `data/excel/`
`XlsxReader`, `ExcelAnalyzer`, `ExcelImporter`, `ExcelModels`,
`ImportContext`, `AreaResolver`, `OrderNumberExtractor`, `SampleFilter`.

#### `data/history/`
`ImportHistory`, `ImportHistoryRepository`.

#### `data/settings/`
`ImportSettings`, `SettingsRepository`.

#### `data/util/`
`PhotoStorage`, `VoiceController`.

#### `data/voice/`
`VoiceDictionary`, `VoiceNumberParser`, `VoiceSegmenter`, `VoiceSettings`,
`VoicePrefixResolver`, `VoiceSearch`, `VoiceCommand`, `VoiceCommandParser`,
`VoiceOrdinals`, `VoiceSession`, `VoiceSpeaker`, `VoiceSearchRepository`,
`AnswerState`, `UnifiedSearch`.

#### `data/backup/` — бэкапы и авто-бэкапы
| Файл | Назначение |
|---|---|
| `GsmBackupReader.kt` | Чтение `.gsmbackup`. |
| `GsmBackupWriter.kt` | Запись `.gsmbackup`. |
| `RollbackBackups.kt` | Парсер `pre_*`, ротация, merge. Четыре операции: restore / rollback / clean / diagnostics. |
| `PublicBackupsLister.kt` | Листинг публичных бэкапов. |
| `PublicBackupsMigrator.kt` | Ленивая миграция старых бэкапов. |
| `BackupManagerStats.kt` | Сводка по бэкапам. |

#### `data/merge/` — слияние БД
| Файл | Назначение |
|---|---|
| `MergeModels.kt` | Планы, конфликты, дерево. |
| `MergeEngine.kt` | Движок. |
| `MergeRunner.kt` | Оркестратор apply-фаз. |

#### `data/compare/` — сравнение БД
| Файл | Назначение |
|---|---|
| `CompareModels.kt` | Дерево сравнения. |
| `CompareEngine.kt` | `buildResult()` — 4 дерева. |

#### `data/diagnostics/` — диагностика БД
| Файл | Назначение |
|---|---|
| `DbIssue.kt` | Sealed-класс проблем: `OrphanOrder`, `OrphanSample`, `BrokenPhotoLink`, `PhotoFlagMismatch`. |
| `DbDiagnosticsEngine.kt` | Чистая логика поиска проблем. |
| `DiagnosticsModels.kt` | `DbDiagnosticsState`. |

#### `data/logs/` — журнал аудита
| Файл | Назначение |
|---|---|
| `LogCategory.kt` | Категории с русскими метками. |
| `LogLevel.kt` | Уровни (info / warn / error). |
| `LogEntry.kt` | Entity для `logs.db`. |
| `LogDao.kt` | DAO журнала. |
| `LogsDatabase.kt` | Отдельная Room-БД. |
| `LogFormatter.kt` | Формат даты и времени. |
| `LogWriter.kt` | Канал + батчи + запись в БД и файл. |
| `LogEntryBuilder.kt` | Fluent-API. |
| `DetailsJson.kt` | Gson-обёртка для details. |
| `Log.kt` | Точка входа (`Log.app`, `Log.db`, …). |
| `AppLog.kt` | `typealias` для использования рядом с `android.util.Log`. |
| `LogFileWriter.kt` | Файловый архив `.logs/YYYY-MM-DD.log`. |
| `LogsFilter.kt` | Фильтр UI журнала. |
| `DeviceInfo.kt` | Снимок устройства для app_start. |
| `CrashRecord.kt` | Запись о крэше (файл `pending_crash.json`). |
| `CrashHandler.kt` | Глобальный перехват исключений. |
| `SampleRowDiff.kt` | Diff между старой и новой пробой. |

### `ui/navigation/`
`NavGraph.kt` (переходы на вкладки логируются), `Screen.kt`,
`SimpleViewModelStoreOwner.kt`.

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
`DbBackupDialog`, `DbRestoreDialog`, `DbRollbackDialog`,
`DbCleanDialog`, `DbImportPickerDialog`, `BackupManagerDialog`,
`DbDiagnosticsDialog`.

**Экраны БД:**
`MergeWizard`, `MergeConflictsScreen`, `DbCompareScreen`.

**Модели вкладки БД:** `CleanConfirmState`.

**Экран журнала:** `LogsScreen` + `LogsViewModel`
(открывается из Настройки → Система).

### `ui/theme/`
`Color.kt`, `Theme.kt`, `Type.kt`.

---

## Как запускается приложение

1. `MainActivity` → `GeoSampleManagerTheme` → `AppRoot()`.
2. `AppRoot` ждёт загрузки Vosk-модели → `ReadyContent(app)`.
3. `ReadyContent` — `key(tick)` + `SimpleViewModelStoreOwner` +
   `AppScaffold(initialRoute)`.
4. `GeoSampleApp.onCreate`:
   - `LogWriter.init(this)` — старт журнала;
   - `CrashHandler.install(this)` — перехват падений;
   - создаёт репозитории:
      - `DatabaseRepository`
      - `SettingsRepository`
      - `ImportHistoryRepository`
      - `VoiceSettingsRepository`;
   - записывает `app_start` со снимком устройства и счётчиков БД.
5. ViewModel'и берут репозиторий через `(application as GeoSampleApp).repository`.

**После замены БД** (импорт/откат/слияние/очистка) — `requestRestart()`
пересобирает поддерево без пересоздания Activity.
См. `docs/CONTEXT_BRIEF.md`.

---

## Ключевые сценарии

- **Импорт Excel** → `AddScreen` + `data/excel/*`.
- **Сверка и поиск** → `SearchScreen` + `Reconciliation*` + `SampleDao`.
- **Заметки и фото** → `NotePhotoDialog` + `PhotoStorage` + `SampleImageDao`.
- **Управление БД** → `DbScreen` + `DbViewModel`.
- **Бэкапы и авто-бэкапы** → `data/backup/*`.
- **Слияние двух БД** → `data/merge/*` + `MergeWizard` + `MergeConflictsScreen`.
- **Сравнение двух БД** → `data/compare/*` + `DbCompareScreen`.
- **Диагностика БД** → `data/diagnostics/*` + `DbDiagnosticsDialog`.
- **Журнал аудита** → `data/logs/*` + `LogsScreen` (Настройки → Система).
- **Настройки** → `SettingsScreen` + `SettingsRepository`.
- **Голосовой помощник** → `docs/VOICE.md` + `data/voice/*`.

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
| Логика ГП | `docs/VOICE.md` + `data/voice/*` |
| Бэкап/восстановление | `data/backup/*` + `DbViewModel` |
| Слияние БД | `data/merge/*` + `MergeWizard` + `MergeConflictsScreen` |
| Сравнение БД | `data/compare/*` + `DbCompareScreen` |
| Диагностика БД | `data/diagnostics/*` + `DbDiagnosticsDialog` |
| Журнал | `data/logs/*` + `LogsScreen` |
| Создание/удаление участков и нарядов | `EditScreen.kt`, `EditViewModel.kt` |
| Термин — что значит | `docs/GLOSSARY.md` |
| Что тестировать | `docs/TESTING.md` |
| Открытые проблемы | `docs/ISSUES.md` |
| Сценарии сверки | `docs/SCENARIOS.md` |
| Модель поиска | `docs/SEARCH_MODEL.md` |

---

## Файлы документации

Все документы — в папке `docs/`.

- `docs/AI_RULES.md` — правила работы ИИ.
- `docs/CONTEXT_BRIEF.md` — где мы сейчас.
- `docs/PROGRESS.md` — история.
- `docs/NEXT_STEPS.md` — текущий этап.
- `docs/DECISIONS.md` — решения по UI и логике.
- `docs/DATABASE.md` — схема БД.
- `docs/ROADMAP.md` — план до релиза.
- `docs/VOICE.md` — спецификация ГП.
- `docs/GLOSSARY.md` — термины.
- `docs/ISSUES.md` — проблемы.
- `docs/TESTING.md` — тесты.
- `docs/SCENARIOS.md` — сценарии сверки.
- `docs/SEARCH_MODEL.md` — единая модель поиска.