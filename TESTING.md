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
| `VoiceCommandParserPausedTest.kt` | Состояние PAUSED: «продолжить», «стоп», «хватит» | 6 |
| `VoiceCommandParserPin3Test.kt` | pin: «дальше», «следующая X», вес «X сотни» | 10 |
| `VoiceCommandParserPin4Test.kt` | pin: склейка числительных, fallback 14↔4 | 7 |
| `VoiceCommandParserPinnedTest.kt` | `FOUND_PINNED`: голое число → `MarkOrdinal` | 8 |
| `VoiceCommandParserQueueTest.kt` | Очередь: «дальше» → `NextInQueue` | 6 |
| `VoiceCommandParserWeightsTest.kt` | Вес: «два шесть», «два семьсот», мусор → Unknown | 17 |
| `VoiceMarkOrdinalFallbackTest.kt` | `hintFor`: 14→4, 40→4, 400→4; 1..3 → null | 27 |
| `VoiceMarkersTest.kt` | Маркеры, отложение одной фразой, SORT+pin | 17 |
| `VoiceSpeakerTest.kt` | `spellMimicry`, `splitLikeHuman`, префиксы по буквам | 19 |

### `data/reconciliation/`

| Файл | Что проверяет | Тестов |
|---|---|---|
| `MarkDecisionTest.kt` | `analyzeMark`: уже отмечена, ошибка, отложена, ВК, холостая | 27 |

### `ui/screens/`

| Файл | Что проверяет | Тестов |
|---|---|---|
| `AnalyzeMatchTest.kt` | Функция `analyzeMatch` | 18 |
| `ReconciliationWeightQueueTest.kt` | `buildWeightQueue`: холостые, ВК, порядок | 11 |
| `SearchScrollTopTest.kt` | `shouldShowScrollTop`: порог >10 | 5 |

**Всего: ~260 тестов.** Все зелёные.

---

## Что ещё нужно

### Приоритет 1 (закладки `5.8.11-b/c`)

| Файл | Что проверять |
|---|---|
| `QueryTokenizerTest.kt` | `KPD1090031` → `[Prefix, Number]`; «1524» → `Number` |
| `QueryNormalizerTest.kt` | Lowercase, ё→е, дефисы, пунктуация |
| `DigitGrouperTest.kt` | «109 00 31» → три группы; нули → `LEADING_ZERO` |
| `GroupToCandidatesTest.kt` | Порядок кандидатов: слитно / по группам / по парам |
| `SearchServiceTest.kt` | `SearchService.search` — мок `VoiceSampleSource` |
| `VoiceSessionStateTest.kt` | `VoiceSession.state` — все переходы |

### Приоритет 2 (после `e4-dicts`)

| Файл | Что проверять |
|---|---|
| `VoiceGrammarStateTest.kt` | Размер словаря в разных состояниях ГП |

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
3. **Один тест — одна проверка.**
4. **Не тестировать UI.**
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
| 🔴 Сейчас | Закрыто: серия `e4` полностью |
| 🟡 После `e4-dicts` | Закладки `5.8.11-b/c` — `QueryTokenizer`, `DigitGrouper`, `SearchService` |
| 🟢 Потом | `AppDatabaseTest` (миграции), `ReconciliationStateTest` (undo/redo) |

---

## Долг — сводка

**Сделано (серия `e4`):**
- `e4-tests` — 3 файла, 31 тест.
- `e4-pin-3/4` — `VoiceCommandParserPin3Test`, `VoiceCommandParserPin4Test`.
- `e4-pin-7` — `VoiceMarkOrdinalFallbackTest` (27 тестов).
- `e4-markers` — `VoiceMarkersTest` (17 тестов).
- `e4-markers-2` — расширение `VoiceMarkersTest`.
- `e4-speak-1` — `VoiceSpeakerTest` (19 тестов).
- `e4-prefix-1` — обновление `VoiceSpeakerTest`.
- `e4-weight-queue` — `ReconciliationWeightQueueTest` (11 тестов).
- `e4-ui-1` — `SearchScrollTopTest` (5 тестов).

**Осталось:**
- Закладки `QueryTokenizerTest`, `DigitGrouperTest`, `SearchServiceTest` — приоритет после `e4-dicts`.
- `AppDatabaseTest` — миграции.