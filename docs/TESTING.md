# TESTING.md — что и как тестировать

> JUnit 4.13.2. Тесты в `app/src/test/java/com/example/geosamplemanager/`.
> Имена тестов — **только латиница**.

---

## Принцип

**Тестируем критичное, что легко сломать молча.**

Парсеры, поиск, доменные решения, чистые функции отчётов,
чистую логику БД-операций (бэкапы, merge, compare, diagnostics),
формат журнала, миграции БД.

UI, I/O, Vosk, TTS, MediaStore, реальная БД — device-check.

**Правило (§21 `AI_RULES.md`):** каждый заход с новым кодом → тесты.

---

## Что уже есть

### `data/voice/` — ГП

`VoiceNumberParserTest`, `VoicePrefixResolverTest`,
`VoiceCommandParserTest`, `WeightVoiceParserTest`,
`UnifiedSearchTest`, `AnswerStateTest`, `VoiceSpeakerTest`,
`VoiceNumberParserSortTest` (новый, 5.9-sort-normalize),
+ серия pin/markers/queue/paused/weights/fallback — все зелёные.

### `data/backup/`

`RollbackBackupsTest`, `PublicBackupsListerTest`,
`PublicBackupsMigratorTest`, `BackupManifestTest`,
`BackupManagerStatsTest`.

### `data/merge/`

`MergeEngineTest` — ~80 тестов.

### `data/compare/`

`CompareEngineTest` — 11.

### `data/diagnostics/`

`DbDiagnosticsEngineTest` — 14.

### `data/logs/`

`LogCategoryTest`, `LogLevelTest`, `LogFormatterTest`,
`DetailsJsonTest`, `LogEntryBuilderTest`, `CrashRecordTest`,
`LogsFilterTest`, `LogFileWriterTest`, `SampleRowDiffTest` — ~65.

### `data/reconciliation/`

`MarkDecisionTest` — 27.

### `data/report/`

`XlsxWriterTest`, `XlsxReportBuilderTest`,
`XlsxMultiReportBuilderTest`, `ReportHtmlGeneratorTest`,
`MultiHtmlReportGeneratorTest` — ~120.

### `data/settings/` (5.9-settings)

- `UiScaleTest` — 7.
- `AppThemeTest` — 7.

### `ui/screens/`

`AnalyzeMatchTest`, `ReconciliationWeightQueueTest`,
`SearchScrollTopTest`, `EditViewModelTest`, `EditScreenTreeItemsTest`,
`EditSearchFilterTest`, `EditAddSampleTest`, `EditMultiselectTest`,
`EditMassOpsTest`, `CleanConfirmStateTest`,
`HelpTopicTest` (новый, 5.9-settings-help-1),
`MainInfoTest` (новый, 5.9-main).

**Всего: ~700 тестов.** Все зелёные.

---

## Что ещё нужно

### Приоритет 1

- `AppDatabaseTest` — миграции (device-check).
- `GsmBackupWriterTest`, `GsmBackupReaderTest` — если решим.

### Приоритет 2 (5.10)

- Тесты теневой статистики — см. `docs/SHADOW_STATS.md` (черновик).
- `SessionStateRepositoryTest`.

---

## Что НЕ покрываем тестами

- **Vosk** — галлюцинации, распознавание. Device-check.
- **TTS** — произношение, кулдаун, скорость.
- **Compose UI** — отдельная тема.
- **Реальная БД** — только миграции.
- **ViewModel'и с Application** — device-check.
- **Backup/restore, merge, compare, diagnostics** — io + zip.
- **MediaStore.**
- **`LogWriter`, `LogFileWriter` (I/O), `CrashHandler`.**
- **`ExitBackupWriter`** — I/O + zip, device-check.

---

## Как запускать

### Android Studio
ПКМ по `app/src/test/` → **Run 'Tests in …'**.

### Терминал
./gradlew testDebugUnitTest --no-daemon --max-workers=1

text
Отчёт: `app/build/reports/tests/testDebugUnitTest/index.html`.

### Автоматически на PR
CI (`.github/workflows/build.yml`):
- Job **`unit-tests`** — на каждый PR в `main` и `feature/*`.
- Блокирует merge при красном.

### Вручную (Actions)
Actions → **Build & Test** → **Run workflow**.
Артефакты: `test-report` (HTML), `app-debug` (APK).

---

## Правила написания тестов

1. **Имя теста — латиница.**
2. **Ассерты — простые.** `assertEquals(expected, actual)`.
3. **Один тест — одна проверка.**
4. **Не тестировать UI.**
5. **Не тестировать БД целиком.** Только миграции.
6. **Тест < 100 мс.**
7. **Комментарии — на русском.**

---

## Что делать при падении теста

1. **Не удалять тест.**
2. Тест — контракт.
3. Если тест прав, а код неправ — откатить правку.
4. Если тест устарел — обновить с пояснением.

**Замечание:** `android.net.Uri.parse()` в JVM-тестах возвращает
`null`. Модели не должны зависеть от `Uri` — хранить строкой.

---

## Приоритеты

| Приоритет | Что |
|---|---|
| 🔴 Сейчас | Закрыты все вкладки 5.9 |
| 🟡 После 5.10 | Теневая статистика |
| 🟢 Потом | `AppDatabaseTest`, `GsmBackup*Test`, `ExitBackupWriterTest` |

---

## Долг — сводка

**Сделано:**
- `data/voice/` — ~250 тестов.
- `data/report/` — ~120.
- `data/backup/` — ~76.
- `data/merge/` — ~80.
- `data/compare/` — 11.
- `data/diagnostics/` — 14.
- `data/logs/` — ~65.
- `data/reconciliation/` — 27.
- `data/settings/` — 14 (UiScale + AppTheme).
- `ui/screens/` — ~155.

**Осталось:**
- `AppDatabaseTest` — миграции.
- `ExitBackupWriterTest` — если решим покрывать.