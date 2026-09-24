# TESTING.md — что и как тестировать

> JUnit 4.13.2. Тесты в `app/src/test/java/com/example/geosamplemanager/`.
> Имена тестов — **только латиница** (backtick-кириллица ломает Gradle).

---

## Принцип

**Тестируем критичное, что легко сломать молча.**

Не всё подряд. Только:
- Парсеры (Excel, голос, числа, запросы).
- Поиск (уровни `UnifiedSearch`, fuzzy).
- Доменные решения (`MarkDecision`, `analyzeMark`).
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
| `VoiceNumberParserTest.kt` | «сто двадцать четыре» → 124, «ноль ноль три» → 003, «тысяча пятьсот шестьдесят два» → 1562 |
| `VoicePrefixResolverTest.kt` | «капэдэ» → KPD, «энвэ» → NV, префиксы из `ImportSettings` |
| `VoiceCommandParserTest.kt` | «первая» → `MarkOrdinal(1)`, «снять первую» → `ClearOrdinal(1)`, «вес два пять» → `SetWeight(2.5)`, «1524» → `Search`, «стоп» → `Stop` |
| `WeightVoiceParserTest.kt` | Разбор веса: «два и шесть» → 2.6, «полтора» → 1.5, «две целых шесть десятых» → 2.6 |
| `UnifiedSearchTest.kt` | Уровни поиска, точное / суффикс / fuzzy |
| `AnswerStateTest.kt` | Определение `AnswerState` из `AnswerReason` |

### `data/reconciliation/`

| Файл | Что проверяет |
|---|---|
| `MarkDecisionTest.kt` | `analyzeMark`: уже отмечена, ошибка импорта, отложена, ВК, холостая, `CanMark` |

### `ui/screens/`

| Файл | Что проверяет |
|---|---|
| `AnalyzeMatchTest.kt` | Функция `analyzeMatch` (сопоставление) |

---

## Что ДОЛЖНО появиться (по серии `5.8.11-e4`)

**Долг — не покрыто.** Пачка `e4-tests`:

### Приоритет 1 (новые правки без тестов)

| Файл | Что проверять |
|---|---|
| `VoiceCommandParserWeightsTest.kt` | `parseWeightAnswer`: мусор «семь утра было холодно» → null; «семь» → 7.0; «два и шесть» → 2.6; `isCleanWeightPhrase` |
| `VoiceCommandParserStatesTest.kt` | `parseForWeight`: «пауза» → `Pause`, «стоп» → `Stop`, «отмена» → `Undo`. `parseForPaused`: «стоп»/«хатит» → `Stop` |
| `VoiceCommandParserFindTest.kt` | «найди» → `Find(null)`; «найди 1524» → `Find("1524")`; «найти KPD1090031» → `Find(...)` |
| `VoiceSpeakerTest.kt` | `spellOut(groups)`: «KPD1090031» → «капэдэ сто девять ноль ноль тридцать один»; без запятых; W → «даблю» |

### Приоритет 2 (закладки без тестов)

| Файл | Что проверять |
|---|---|
| `QueryTokenizerTest.kt` | `QueryTokenizer`: `KPD1090031` → `[Prefix, Number]`; «1524» → `Number`; «первая» → `Ordinal` |
| `QueryNormalizerTest.kt` | Lowercase, ё→е, дефисы, пунктуация |
| `DigitGrouperTest.kt` | «109 00 31» → три группы; «1524» → одна; нули → `LEADING_ZERO` |
| `GroupToCandidatesTest.kt` | Порядок кандидатов: слитно / по группам / по парам |
| `SearchServiceTest.kt` | `SearchService.search` — мок `VoiceSampleSource`, проверка `Found`/`NotFound` |
| `VoiceSessionStateTest.kt` | `VoiceSession.state` — все переходы |

### Приоритет 3 (старые, из планов)

| Файл | Что проверять |
|---|---|
| `AppDatabaseTest.kt` | Миграции БД (version 1 → 2). Только на устройстве (instrumented). |

---

## Что НЕ покрываем тестами

- **Vosk** — галлюцинации, распознавание. Только device-check.
- **TTS** — произношение, кулдаун. Только device-check.
- **Compose UI** — Compose UI-тесты отдельная тема, не сейчас.
- **Реальная БД** — только миграции, остальное на устройстве.
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
- Пропускается, если в PR только документация (job остаётся зелёным).
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

---

## Правила написания тестов

1. **Имя теста — латиница.** `parseThousand`, не `парситТысячу`.
2. **Ассерты — простые.** `assertEquals(expected, actual)`.
3. **Один тест — одна проверка.** Не смешивать.
4. **Не тестировать UI.** Compose-тесты — отдельная тема.
5. **Не тестировать БД целиком.** Только миграции.
6. **Тест должен проходить за <100 мс.** Если дольше — что-то не так.
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
| 🔴 Сейчас | Пачка `e4-tests` — покрыть `e4b`, `e4g`, `e4g3`, `e4e-a`, `e4e-bundle/3` |
| 🟡 После `e4-tests` | Закладки `5.8.11-b/c` — `QueryTokenizer`, `DigitGrouper`, `SearchService` |
| 🟢 Потом | `AppDatabaseTest` (миграции), `ReconciliationStateTest` (undo/redo) |

---

## Долг — сводка

**Сделано (серия `e4`):**
- `e4a` — вес в `AWAITING_WEIGHT`. Нет тестов.
- `e4b` — фильтр мусора в весе. **Критично покрыть.**
- `e4g` — Pause + опечатка. **Критично покрыть.**
- `e4g3` — команда «Найди». **Критично покрыть.**
- `e4-fix-1` — убрано «четвертью». Грамматика, не тестируется.
- `e4-fix-2` — кулдаун 250 мс. Константа.
- `e4e-a` — мимикрия в `VoiceSpeaker`. **Критично покрыть.**
- `e4e-bundle/2` — поле `groups`. Тривиально.
- `e4e-bundle/3` — `voiceSearch` через `SearchService`. **Частично покрыть** (`buildGroupsForVoice`).

**План:** пачка `e4-tests` (5–7 файлов) закрывает приоритет 1.
