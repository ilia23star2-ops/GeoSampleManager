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

**Первым делом ИИ спрашивает: «где ты — дома или на работе?»** От ответа
зависит формат ответа. См. `AI_RULES.md` §20.

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

## Работа с двух машин — три режима

**Контекст:** проект разрабатывается на двух машинах.

- **Дома** — Android Studio. Полный цикл: сборка, тесты, эмулятор,
  реальное устройство.
- **На работе** — без IDE. Git на машине не установлен. Приоритет —
  `github.dev` (браузерный VS Code), fallback — `github.com` (веб).

### Режим А — дома (Android Studio)

1. `git pull`.
2. Создать feature-ветку: `feature/X.Y-краткое`.
3. Правки в IDE.
4. Локально: `Build → Make Project`, юнит-тесты, эмулятор, устройство.
5. **Коммит + push** (все файлы одним коммитом).
6. **CI (вручную)** — GitHub → Actions → Build & Test → Run workflow.
7. PR → Merge.
8. Удалить ветку.

### Режим Б — на работе, `github.dev` (приоритет)

1. Открыть репозиторий на github.com → нажать `.`.
2. Создать feature-ветку (внизу слева кнопка с именем ветки).
3. Править файлы.
4. Source Control (`Ctrl+Shift+G`) → **один коммит** на все файлы →
   Commit & Push.
5. PR → **CI (вручную)** → Merge → Delete branch.

### Режим В — на работе, `github.com` (fallback)

Используется, если `github.dev` недоступен.

1. Репозиторий → `main ▾` → Create branch: `feature/X.Y-краткое`.
2. **Каждый файл — отдельный коммит:**
   - Файл 1: открыть → ✏️ → правки → Commit changes → `X.Y/1 — Имя`.
   - Файл 2: ... → `X.Y/2 — Имя`.
3. PR → **CI (вручную)** → Merge → Delete branch.

### Правила гигиены

- **Всегда `pull` перед началом.**
- **Никогда не работать напрямую в `main`.**
- **Одна задача = одна ветка.**
- **Коммит после каждого захода.**
- **Пуш в конце сессии.**
- **После merge — удалить ветку.**

### Схема веток

- `main` — стабильная.
- `feature/<заход>-<краткое>` — новая функциональность.
- `fix/<заход>-<краткое>` — баг.
- `docs/<заход>-<краткое>` — документация.

### CI (GitHub Actions) — только вручную

**Автозапуска по push / PR нет.** CI запускается **по команде**.

**Как запустить:**

1. GitHub → вкладка **Actions**.
2. Слева — **Build & Test**.
3. Справа сверху — **Run workflow**.
4. Выбрать ветку.
5. Поставить чек-боксы:
    - **Run unit tests** — прогнать юнит-тесты (по умолчанию ✅).
    - **Build Debug APK** — собрать APK (по умолчанию ⬜).
6. Зелёная кнопка **Run workflow**.

**Что когда выбирать:**

| Ситуация | run_tests | build_apk |
|---|---|---|
| Быстрая проверка (только тесты) | ✅ | ⬜ |
| Полная сборка APK | ✅ | ✅ |
| Только APK (тесты уже прогонялись) | ⬜ | ✅ |

**Артефакты** на странице запуска:
- `app-debug` — APK (если `build_apk`).
- `test-report` — HTML-отчёт (если `run_tests`).

Vosk-модель не коммитится в Git. В CI она выкачивается из релиза GitHub
отдельным шагом (заход 2e).

**Подробности:** `AI_RULES.md` §20.

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

#### `data/voice/`
`VoiceController`, `VoiceDictionary`, `VoiceNumberParser`,
`VoiceSegmenter`, `VoiceSettings`, `VoicePrefixResolver`, `VoiceSearch`,
`VoiceCommand`, `VoiceCommandParser`, `VoiceOrdinals`, `VoiceSession`,
`VoiceSpeaker`, `VoiceSearchRepository`, `AnswerState`, `UnifiedSearch`.

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