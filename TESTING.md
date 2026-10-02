# TESTING.md — что и как тестировать

> JUnit 4.13.2. Тесты в `app/src/test/java/com/example/geosamplemanager/`.
> Имена тестов — **только латиница** (backtick-кириллица ломает Gradle).

---

## Принцип

**Тестируем критичное, что легко сломать молча.**

Не всё подряд. Только:
- Парсеры (Excel, голос, числа, запросы).
- Поиск (уровни `UnifiedSearch`, fuzzy).
- Доменные решения (`MarkDecision`, `analyzeMark`, `VoiceMarkOrdinalFallback`).
- Маппинг результатов (`ResponseMapper`).
- Логика undo/redo.
- Отчёты (XLSX, HTML).
- Чистые функции вкладки Редактирование.
- Чистая логика БД-бэкапов (`RollbackBackups`).
- Миграции БД.

UI не тестируем — проверяем руками. I/O, zip, БД — тоже
device-check.

**Правило (§23 `AI_RULES.md`):** каждый заход с новым кодом → тесты.
Для вкладки БД — чистая логика покрывается юнит-тестами,
io/zip/состояние Activity — device-check.

---

## Что уже есть

Тесты в `app/src/test/java/com/example/geosamplemanager/`.

### `data/voice/`

| Файл | Что проверяет | Тестов |
|---|---|---|
| `VoiceNumberParserTest.kt` | «сто двадцать четыре» → 124, «тысяча пятьсот шестьдесят два» → 1562 | 12 |
| `VoicePrefixResolverTest.kt` | «капэдэ» → KPD, «энвэ» → NV | 7 |
| `VoiceCommandParserTest.kt` | «первая» → `MarkOrdinal(1)`, «снять первую» → `ClearOrdinal(1)` | 12 |
| `WeightVoiceParserTest.kt` | «два и шесть» → 2.6, «полтора» → 1.5 | 19 |
| `UnifiedSearchTest.kt` | Уровни поиска, точное / суффикс / fuzzy | 18 |
| `AnswerStateTest.kt` | `AnswerState` из `AnswerReason` | 22 |
| `VoiceCommandParserFindTest.kt` | «найди X» → `Find(X)`, «найди» → `Find(null)` | 8 |
| `VoiceCommandParserPausedTest.kt` | Состояние PAUSED | 6 |
| `VoiceCommandParserPin3Test.kt` | pin: «дальше», «следующая X», вес «X сотни» | 10 |
| `VoiceCommandParserPin4Test.kt` | pin: склейка числительных, fallback 14↔4 | 7 |
| `VoiceCommandParserPinnedTest.kt` | `FOUND_PINNED` | 8 |
| `VoiceCommandParserQueueTest.kt` | Очередь: «дальше» → `NextInQueue` | 6 |
| `VoiceCommandParserWeightsTest.kt` | Вес: «два шесть» и т.п. | 17 |
| `VoiceMarkOrdinalFallbackTest.kt` | `hintFor`: 14→4, 40→4, 400→4; 1..3 → null | 27 |
| `VoiceMarkersTest.kt` | Маркеры, отложение, SORT+pin | 17 |
| `VoiceSpeakerTest.kt` | `spellMimicry`, `splitLikeHuman` | 19 |

### `data/backup/`

| Файл | Что проверяет | Тестов |
|---|---|---|
| `RollbackBackupsTest.kt` | Парсер имени `pre_restore_*`, фильтр, сортировка, ротация | 12 |

### `data/reconciliation/`

| Файл | Что проверяет | Тестов |
|---|---|---|
| `MarkDecisionTest.kt` | `analyzeMark`: уже отмечена, ошибка, отложена, ВК, холостая | 27 |

### `data/report/`

| Файл | Что проверяет | Тестов |
|---|---|---|
| `MultiHtmlReportGeneratorTest.kt` | Мультинарядный HTML | 17 |
| `ReportHtmlGeneratorTest.kt` | Одиночный HTML | 26 |
| `XlsxMultiReportBuilderTest.kt` | Мульти XLSX | 20 |
| `XlsxReportBuilderTest.kt` | Одиночный XLSX | 27 |
| `XlsxWriterTest.kt` | Низкоуровневый zip/XML | 32 |

### `ui/screens/`

| Файл | Что проверяет | Тестов |
|---|---|---|
| `AnalyzeMatchTest.kt` | `analyzeMatch` | 18 |
| `ReconciliationWeightQueueTest.kt` | `buildWeightQueue` | 11 |
| `SearchScrollTopTest.kt` | `shouldShowScrollTop` | 5 |
| `EditViewModelTest.kt` | `buildEditTree` | 17 |
| `EditScreenTreeItemsTest.kt` | `buildTreeItems` | 8 |
| `EditSearchFilterTest.kt` | `applyEditFilters` | 18 |
| `EditAddSampleTest.kt` | `planInsertPosition`, `parseSuffixNumber` и др. | 21 |
| `EditMultiselectTest.kt` | `applyMultiselectToggle`, `computeSelectionLabel` | 13 |
| `EditMassOpsTest.kt` | `applyMassEditToRow` | 14 |

**Всего: ~382 теста.** Все зелёные.

---

## Что ещё нужно

### Приоритет 1 (закладки `5.8.11-b/c`)

| Файл | Что проверять |
|---|---|
| `QueryTokenizerTest.kt` | `KPD1090031` → `[Prefix, Number]` |
| `QueryNormalizerTest.kt` | Lowercase, ё→е, дефисы |
| `DigitGrouperTest.kt` | «109 00 31» → три группы |
| `GroupToCandidatesTest.kt` | Порядок кандидатов |
| `SearchServiceTest.kt` | Мок `VoiceSampleSource` |
| `VoiceSessionStateTest.kt` | `VoiceSession.state` — все переходы |

### Приоритет 2

| Файл | Что проверять |
|---|---|
| `VoiceGrammarStateTest.kt` | Размер словаря в состояниях ГП |

### Приоритет 3

| Файл | Что проверять |
|---|---|
| `AppDatabaseTest.kt` | Миграции БД (version 1 → 2). Только на устройстве. |
| `GsmBackupWriterTest.kt` | Запись `.gsmbackup` — манифест, структура zip. |
| `GsmBackupReaderTest.kt` | Чтение манифеста из архива. |

**Заметка:** для БД-пачек (backup, restore) тесты не писались —
всё покрыто device-check. С `db-rollback` начали покрывать
чистую логику: `RollbackBackupsTest`. Io/zip/замена файлов
остаются под device-check.

---

## Что НЕ покрываем тестами

- **Vosk** — галлюцинации, распознавание. Только device-check.
- **TTS** — произношение, кулдаун. Только device-check.
- **Compose UI** — отдельная тема, не сейчас.
- **Реальная БД** — только миграции.
- **`ReconciliationViewModel`** целиком — связан с Application, Vosk, БД.
- **`EditViewModel`** целиком — связан с Application и Room.
- **`DbViewModel`** — io, состояние Activity. Device-check.
- **Backup/restore** — io + zip + замена файлов. Device-check.

---

## Как запускать

### В Android Studio

1. ПКМ по `app/src/test/` → **Run 'Tests in …'**.
2. Отчёт: вкладка `Run` внизу.

### В терминале
./gradlew testDebugUnitTest

Отчёт: `app/build/reports/tests/testDebugUnitTest/index.html`.

### Автоматически на PR

CI (`.github/workflows/build.yml`):
- Job **`unit-tests`** — на каждый PR в `main` и `feature/*`.
- Пропускается, если в PR только документация.
- Блокирует merge при красном.

### Вручную (Actions)

1. Actions → **Build & Test**.
2. **Run workflow**.
3. Ветка.
4. ✅ `Run unit tests`.
5. ⬜ `Build Debug APK`.
6. **Run workflow**.

Артефакты:
- `test-report` — HTML.
- `app-debug` — APK (если `build_apk`).

---

## Правила написания тестов

1. **Имя теста — латиница.**
2. **Ассерты — простые.** `assertEquals(expected, actual)`.
3. **Один тест — одна проверка.**
4. **Не тестировать UI.**
5. **Не тестировать БД целиком.** Только миграции.
6. **Тест должен проходить за <100 мс.**
7. **Комментарии в тестах — на русском.** Имена — латиница.

---

## Что делать при падении теста

1. **Не удалять тест.** Найти причину.
2. Тест — контракт.
3. Если тест прав, а код неправ — откатить правку.
4. Если тест устарел — обновить с пояснением.

---

## Приоритеты

| Приоритет | Что |
|---|---|
| 🔴 Сейчас | Закрыто: Редактирование. В работе: БД (тесты на чистую логику + device-check) |
| 🟡 После `e4-dicts` | `QueryTokenizer`, `DigitGrouper`, `SearchService` |
| 🟢 Потом | `AppDatabaseTest`, `ReconciliationStateTest`, `GsmBackup*Test` |

---

## Долг — сводка

**Сделано (серия `e4`):** 3 файла, 31 тест + 8 отдельных файлов.
**Сделано (5.9 Статистика):** 5 файлов XLSX, ~106 тестов + HTML, 43.
**Сделано (5.9 Редактирование):** 6 файлов, 91 тест.
**Сделано (5.9 БД):** 1 файл, 12 тестов (`RollbackBackupsTest`).

**Осталось:**
- `QueryTokenizerTest`, `DigitGrouperTest`, `SearchServiceTest` — после `e4-dicts`.
- `AppDatabaseTest` — миграции.
- `GsmBackupWriterTest`, `GsmBackupReaderTest` — если решим покрывать.