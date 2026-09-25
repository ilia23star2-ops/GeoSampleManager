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
- Миграции БД.

UI не тестируем — проверяем руками.

**Правило (§23 `AI_RULES.md`):** каждый заход с новым кодом → тесты.

---

## Что уже есть

Тесты в `app/src/test/java/com/example/geosamplemanager/`:

### `data/voice/`

| Файл | Что проверяет |
|---|---|
| `VoiceNumberParserTest.kt` | «сто двадцать четыре» → 124, «тысяча пятьсот шестьдесят два» → 1562 |
| `VoicePrefixResolverTest.kt` | «капэдэ» → KPD, «энвэ» → NV |
| `VoiceCommandParserTest.kt` | «первая» → `MarkOrdinal(1)`, «снять первую» → `ClearOrdinal(1)` |
| `WeightVoiceParserTest.kt` | Разбор веса: «два и шесть» → 2.6, «полтора» → 1.5 |
| `UnifiedSearchTest.kt` | Уровни поиска, точное / суффикс / fuzzy |
| `AnswerStateTest.kt` | Определение `AnswerState` из `AnswerReason` |
| **`VoiceSpeakerTest.kt`** | `spellOut(groups)`: «KPD1090031» → «капэдэ сто девять ноль ноль тридцать один» |
| **`VoiceMarkOrdinalFallbackTest.kt`** | Подсказки при промахе по номеру: 14 → 4, 40 → 4, 400 → 4, 4000 → 4; 4 → 14/40/400/4000; для 1..3 — пусто. 26 тестов. |

### `data/reconciliation/`

| Файл | Что проверяет |
|---|---|
| `MarkDecisionTest.kt` | `analyzeMark`: уже отмечена, ошибка импорта, отложена, ВК, холостая, `CanMark` |

### `ui/screens/`

| Файл | Что проверяет |
|---|---|
| `AnalyzeMatchTest.kt` | Функция `analyzeMatch` (сопоставление) |

---

## Что ДОЛЖНО появиться

### Приоритет 1 (закладки `5.8.11-b/c`)

| Файл | Что проверять |
|---|---|
| `QueryTokenizerTest.kt` | `KPD1090031` → `[Prefix, Number]`; «1524» → `Number` |
| `QueryNormalizerTest.kt` | Lowercase, ё→е, дефисы, пунктуация |
| `DigitGrouperTest.kt` | «109 00 31» → три группы; нули → `LEADING_ZERO` |
| `GroupToCandidatesTest.kt` | Порядок кандидатов: слитно / по группам / по парам |
| `SearchServiceTest.kt` | `SearchService.search` — мок `VoiceSampleSource` |
| `VoiceSessionStateTest.kt` | `VoiceSession.state` — все переходы |

### Приоритет 2 (после `e4-markers`)

| Файл | Что проверять |
|---|---|
| `VoiceMarkersTest.kt` | Маркеры намерения: «отметь» → `AWAITING_MARK`, «снять» → `AWAITING_CLEAR` |
| `VoiceConfirmTest.kt` | Подтверждение массовых: «да» / «нет» / тайм-аут |

### Приоритет 3 (старые)

| Файл | Что проверять |
|---|---|
| `AppDatabaseTest.kt` | Миграции БД (version 1 → 2). Только на устройстве (instrumented). |

---

## Что НЕ покрываем тестами

- **Vosk** — галлюцинации, распознавание. Только device-check.
- **TTS** — произношение, кулдаун. Только device-check.
- **Compose UI** — отдельная тема, не сейчас.
- **Реальная БД** — только миграции.
- **`ReconciliationViewModel`** целиком — связан с Application, Vosk, БД.

---

## Как запускать

### В Android Studio

1. ПКМ по `app/src/test/` → **Run 'Tests in …'**.
2. Отчёт: вкладка `Run` внизу.

### В терминале
./gradlew testDebugUnitTest

text

Отчёт: `app/build/reports/tests/testDebugUnitTest/index.html`.

### Автоматически на PR

CI (`.github/workflows/build.yml`):
- Job **`unit-tests`** — на каждый PR в `main` и `feature/*`.
- Пропускается, если в PR только документация.
- Блокирует merge при красном (branch protection).

### Вручную (Actions)

1. Actions → **Build & Test**.
2. **Run workflow**.
3. Ветка.
4. ✅ `Run unit tests`.
5. ⬜ `Build Debug APK` (если нужен APK).
6. **Run workflow**.

Артефакты:
- `test-report` — HTML-отчёт.
- `app-debug` — APK (если `build_apk`).

---

## Правила написания тестов

1. **Имя теста — латиница.** `parseThousand`, не `парситТысячу`.
2. **Ассерты — простые.** `assertEquals(expected, actual)`.
3. **Один тест — одна проверка.** Не смешивать.
4. **Не тестировать UI.** Compose-тесты — отдельная тема.
5. **Не тестировать БД целиком.** Только миграции.
6. **Тест должен проходить за <100 мс.**
7. **Комментарии в тестах — на русском.** Имена — латиница.

---

## Что делать при падении теста

1. **Не удалять тест.** Найти причину.
2. Тест — контракт. Если он упал — сломалось поведение.
3. Если тест прав, а код неправ — откатить правку (§19 `AI_RULES.md`).
4. Если тест устарел — обновить тест с пояснением в комментарии.

---

## Приоритеты

| Приоритет | Что |
|---|---|
| 🔴 Сейчас | Пачка `e4-markers` — покрыть новые состояния |
| 🟡 После | Закладки `5.8.11-b/c` — `QueryTokenizer`, `DigitGrouper`, `SearchService` |
| 🟢 Потом | `AppDatabaseTest` (миграции), `ReconciliationStateTest` (undo/redo) |

---

## Долг — сводка

**Закрыто (серия `e4-pin`):**
- `e4-pin-1…7` — покрыто `VoiceMarkOrdinalFallbackTest` (26 тестов), `VoiceSpeakerTest`, `VoiceCommandParserTest`.
- `e4-tests` — покрытие парсера (3 файла, 31 тест).

**Осталось:**
- `e4e-bundle/3` — `voiceSearch` через `SearchService`. Частично покрыть (`buildGroupsForVoice`).
- `e4-markers` — новые состояния. Критично покрыть после реализации.
