# TESTING.md — что и как тестировать

> JUnit 4.13.2. Тесты в `app/src/test/java/com/example/geosamplemanager/`.
> Имена тестов — **только латиница** (backtick-кириллица ломает Gradle).

---

## Принцип

**Тестируем критичное, что легко сломать молча.**

- Парсеры (Excel, голос, числа, запросы).
- Поиск (уровни `UnifiedSearch`, fuzzy).
- Доменные решения (`MarkDecision`, `analyzeMark`).
- Маппинг результатов (`ResponseMapper`).
- Логика undo/redo.
- Отчёты (XLSX, HTML).
- Чистые функции вкладки Редактирование.
- Чистая логика БД-бэкапов и слияния (`RollbackBackups`,
  `PublicBackupsLister`, `BackupManifest`, `CleanConfirmState`,
  `BackupManagerStats`, `MergeEngine`).
- Миграции БД.

UI не тестируем — проверяем руками. I/O, zip, MediaStore, БД —
device-check.

**Правило (§23 `AI_RULES.md`):** каждый заход с новым кодом → тесты.

---

## Что уже есть

### `data/voice/`

| Файл | Что проверяет | Тестов |
|---|---|---|
| `VoiceNumberParserTest.kt` | «сто двадцать четыре» → 124 | 12 |
| `VoicePrefixResolverTest.kt` | «капэдэ» → KPD, «энвэ» → NV | 7 |
| `VoiceCommandParserTest.kt` | «первая» → `MarkOrdinal(1)` | 12 |
| `WeightVoiceParserTest.kt` | «два и шесть» → 2.6 | 19 |
| `UnifiedSearchTest.kt` | Уровни поиска | 18 |
| `AnswerStateTest.kt` | `AnswerState` из `AnswerReason` | 22 |
| `VoiceCommandParserFindTest.kt` | «найди X» | 8 |
| `VoiceCommandParserPausedTest.kt` | PAUSED | 6 |
| `VoiceCommandParserPin3Test.kt` | pin: «дальше», «следующая X» | 10 |
| `VoiceCommandParserPin4Test.kt` | pin: склейка числительных | 7 |
| `VoiceCommandParserPinnedTest.kt` | `FOUND_PINNED` | 8 |
| `VoiceCommandParserQueueTest.kt` | Очередь | 6 |
| `VoiceCommandParserWeightsTest.kt` | Вес | 17 |
| `VoiceMarkOrdinalFallbackTest.kt` | `hintFor` | 27 |
| `VoiceMarkersTest.kt` | Маркеры, отложение | 17 |
| `VoiceSpeakerTest.kt` | `spellMimicry`, `splitLikeHuman` | 19 |

### `data/backup/`

| Файл | Что проверяет | Тестов |
|---|---|---|
| `RollbackBackupsTest.kt` | Парсер имени, фильтр, сортировка, ротация, merge, fromPublic | 30 |
| `PublicBackupsListerTest.kt` | `extractSubDir`, `isAutoBackupSubDir` | 19 |
| `PublicBackupsMigratorTest.kt` | `subdirFor` | 10 |
| `BackupManifestTest.kt` | Парсер `manifest.json`, `operation` | 6 |
| `BackupManagerStatsTest.kt` | `summarize`, `formatSize` | 11 |

### `data/merge/`

| Файл | Что проверяет | Тестов |
|---|---|---|
| `MergeEngineTest.kt` | `planAreas` / `planOrders` / `planSamples` / `planWells` / `planNotes` / `planPhotos`, `diffFields`, `resolveSample`, `displayFor`, `FieldResolution`, `buildConflictTree`, `extractArchivePhotoName`, `MergeStats` | ~80 |

### `data/reconciliation/`

| Файл | Что проверяет | Тестов |
|---|---|---|
| `MarkDecisionTest.kt` | `analyzeMark` | 27 |

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
| `EditAddSampleTest.kt` | `planInsertPosition` и др. | 21 |
| `EditMultiselectTest.kt` | `applyMultiselectToggle` | 13 |
| `EditMassOpsTest.kt` | `applyMassEditToRow` | 14 |
| `CleanConfirmStateTest.kt` | `CleanConfirmState` | 6 |

**Всего: ~500+ тестов.** Все зелёные.

---

## Что ещё нужно

### Приоритет 1 (закладки `5.8.11-b/c`)

- `QueryTokenizerTest.kt`, `QueryNormalizerTest.kt`.
- `DigitGrouperTest.kt`, `GroupToCandidatesTest.kt`.
- `SearchServiceTest.kt`, `VoiceSessionStateTest.kt`.

### Приоритет 3

- `AppDatabaseTest.kt` — миграции. Только device-check.
- `GsmBackupWriterTest.kt`, `GsmBackupReaderTest.kt` — если решим.

---

## Что НЕ покрываем тестами

- **Vosk** — галлюцинации, распознавание. Только device-check.
- **TTS** — произношение, кулдаун.
- **Compose UI** — отдельная тема.
- **Реальная БД** — только миграции.
- **`ReconciliationViewModel`, `EditViewModel`, `DbViewModel`** —
  связаны с Application. Device-check.
- **Backup/restore, merge** — io + zip + замена файлов. Device-check.
- **MediaStore** — реальные запросы. Device-check.

---

## Как запускать

### В Android Studio

ПКМ по `app/src/test/` → **Run 'Tests in …'**.

### В терминале
./gradlew testDebugUnitTest

Отчёт: `app/build/reports/tests/testDebugUnitTest/index.html`.

### Автоматически на PR

CI (`.github/workflows/build.yml`):
- Job **`unit-tests`** — на каждый PR в `main` и `feature/*`.
- Блокирует merge при красном.

### Вручную (Actions)

Actions → **Build & Test** → **Run workflow**.

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
7. **Комментарии в тестах — на русском.**

---

## Что делать при падении теста

1. **Не удалять тест.** Найти причину.
2. Тест — контракт.
3. Если тест прав, а код неправ — откатить правку.
4. Если тест устарел — обновить с пояснением.

**Замечание:** `android.net.Uri.parse()` в JVM-тестах возвращает
`null` (`returnDefaultValues = true`). Если модель зависит от
Android — тест упадёт. Решение: не тащить `Uri` в data-модели,
хранить строкой (`PublicBackup.uri`, `RollbackBackup.publicUri`).

---

## Приоритеты

| Приоритет | Что |
|---|---|
| 🔴 Сейчас | Закрыты: Редактирование, большая часть БД |
| 🟡 После `e4-dicts` | `QueryTokenizer`, `DigitGrouper`, `SearchService` |
| 🟢 Потом | `AppDatabaseTest`, `GsmBackup*Test` |

---

## Долг — сводка

**Сделано (серия `e4`):** ~370 тестов.
**Сделано (5.9 Статистика):** XLSX, ~106 + HTML, 43.
**Сделано (5.9 Редактирование):** 6 файлов, 91 тест.
**Сделано (5.9 БД):** `data/backup/` (5 файлов, ~76),
`data/merge/` (1 файл, ~80), `CleanConfirmStateTest`.

**Осталось:**
- `QueryTokenizerTest`, `DigitGrouperTest`, `SearchServiceTest` — после `e4-dicts`.
- `AppDatabaseTest` — миграции.
- `GsmBackupWriterTest`, `GsmBackupReaderTest` — если решим покрывать.