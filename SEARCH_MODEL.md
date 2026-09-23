# SEARCH_MODEL.md — единая модель поиска и ответа

> Спецификация того, **как в проекте устроен поиск** — и ручной, и голосовой.
>
> Цель: **свести две существующие системы к одной**.

**Статус:** 🟢 полный документ (обсуждён, решения зафиксированы)
**Обновлён:** 2026-09-23
**Связанные файлы:** `VOICE.md`, `DECISIONS.md`, `AI_RULES.md`

---

## 0. О документе

### Зачем он

Сейчас в проекте **две параллельные системы поиска**: ручной и голосовой.
Обе используют `UnifiedSearch`, но **презентеры разные**. Отсюда разные
формулировки, крайние случаи, ложные срабатывания.

Документ описывает **единую модель**, к которой всё сведётся.

### Что описываем

Модель ввода (ручной + голосовой), модель поиска, модель ответа
(`Response`), презентер, состояния ГП.

### Что НЕ описываем

UI-компоновку (`DECISIONS.md`), звуковую карту (`VoiceFeedback`),
Vosk endpoint tuning (И-24), схему БД (`DATABASE.md`).

---

## 1. Термины и границы

### 1.1. Ключевые термины

| Термин | Что значит |
|---|---|
| **Запрос** | Пользователь ввёл или сказал что-то. Один токен или несколько. |
| **Токен** | Фрагмент запроса: число, код, префикс. |
| **Кандидат** | Нормализованный вид токена для поиска. |
| **Попадание (hit)** | Проба из БД, совпавшая с запросом. |
| **Поиск** | Сопоставление кандидатов с базой. |
| **Ответ (`Response`)** | Единая модель: что показать и сказать. |
| **Презентер** | `Response` → `visual` + `spoken`. |
| **Состояние ГП** | Режим ожидания: что ГП принимает сейчас. |
| **Режим ГП (`VoiceMode`)** | `SEARCH` / `SORT`. Ортогонален состоянию. |

### 1.2. Три «двойки», которые убираем

| Что | Ручной | Голосовой |
|---|---|---|
| **Парсер** | `setQuery()` + `analyzeMatch()` | `VoiceCommandParser.parse()` |
| **Модель ответа** | `MatchInfo` | `VoiceExecResult` |
| **Презентер** | UI рисует из `MatchInfo` | `VoiceDialog` строит фразы для TTS |

После унификации:

| Что | Единый путь |
|---|---|
| **Парсер** | `QueryNormalizer` → токены → кандидаты |
| **Модель ответа** | `SearchResult` → `Response` |
| **Презентер** | `Presenter.render(Response)` |

### 1.3. Границы

**Внутри:** парсинг чисел, нормализация префиксов, `UnifiedSearch`,
`SearchResult`, `Response`, озвучка.

**Вне:** визуальный дизайн, звуковая карта, БД, Vosk.

---

## 2. Аудит текущего состояния

### 2.1. Что уже унифицировано ✅

- **Единый движок поиска** — `UnifiedSearch`.
- **Единая модель состояния** — `AnswerReason` / `AnswerState`.
- **Единая палитра** — `AnswerStateColors`.
- **Единая звуковая карта** — `VoiceFeedback`.

### 2.2. Что НЕ унифицировано ❌

- **Две модели ответа:** `MatchInfo` (UI) и `VoiceExecResult` (ГП).
- **Два презентера:** `SearchScreen` (UI) и `VoiceDialog.describeResult()` (TTS).
- **Два парсера:** `setQuery` / `analyzeMatch` и `VoiceCommandParser.parse`.
- **Разная логика `filterMode`:** у ручного есть, у голосового нет.

### 2.3. Болячки

- **Б1. Ложные срабатывания ГП.** `VoiceCommandParser` не знает состояния.
- **Б2а. Глюки с цифрами.** Правила `VoiceNumberParser` меняются под сценарий.
- **Б2б. Произношение номеров.** `spellOut("1090031")` → «10 90 03 1».
- **Б3. Разные ответы UI и TTS.**
- **Б4. Сортировка сбрасывается** при ненайденном номере.
- **Б5. Неточные срабатывания.** Следствие Б1–Б4.
- **Б6. Нет единой логики.** Частично решена.

### 2.4. Диагноз

Корень — **разные презентеры одной модели**.

### 2.5. Что менять не будем

`UnifiedSearch`, `AnswerReason`, `AnswerState`, `AnswerStateColors`,
`VoiceFeedback`.

---

## 3. Состояния ГП

### 3.1. Список состояний

| Состояние | Что значит |
|---|---|
| `IDLE` | ГП не запущен. |
| `LISTENING` | Слушает. Принимает команды и поиск. |
| `AWAITING_WEIGHT` | Спросил «Вес?» и ждёт число. |
| `AWAITING_CONTINUE` | Спросил «Выберите на экране». |
| `AWAITING_CHOICE` | Спросил «Снять, отложить или пропустить?». |
| `PAUSED` | Пауза. Активны «продолжить» и «стоп». |

**Режим (`VoiceMode`) — отдельно, ортогонально:**

| Режим | Что значит |
|---|---|
| `SEARCH` | Обычный поиск со статистикой и отметками. |
| `SORT` | Сортировка: только «запрос → наряд», без отметок. |

Режим **не является состоянием**. ГП может быть в `LISTENING + SORT`,
`AWAITING_CONTINUE + SEARCH` и т.д. Переключение режима не меняет
состояние. Отметки блокируются **только** в режиме `SORT`.

### 3.2. Диаграмма переходов состояний
IDLE ──[старт]──▶ LISTENING
│
┌───────────┼───────────┬─────────────┐
▼ ▼ ▼ ▼
[пауза] [Вес?] [Найден N] [Уже отмечена]
│ │ │ │
▼ ▼ ▼ ▼
PAUSED AWAITING_ AWAITING_ AWAITING_
WEIGHT CONTINUE CHOICE
│ │ │ │
└───────────┴───────────┴─────────────┘
[ответ пользователя]
│
▼
LISTENING

text

**Режим `SORT` включается/выключается независимо** — командой «сортировка»
и «поиск». Состояние при этом не меняется.

### 3.3. Что принимается в каждом состоянии

| Состояние | Принимает | Всё остальное |
|---|---|---|
| `LISTENING` | Всё: поиск, все команды | **Молчание** (не команда, не поиск) |
| `AWAITING_WEIGHT` | Число-вес, `Undo`, `Stop` | «Скажите вес или отмена» |
| `AWAITING_CONTINUE` | `Resume`, `Pause`, `Stop`, `Search`, `Sort` | «Скажите продолжить или стоп» |
| `AWAITING_CHOICE` | `ChoiceRemove/Postpone/Skip`, `MarkCurrent` (POSTPONED), `Undo`, `Stop` | «Скажите: снять, отложить или пропустить» |
| `PAUSED` | `Resume`, `Stop` | «Пауза. Скажите продолжить или стоп» |

**В `LISTENING` на непонятное — молчать.** Не «Не понял», не звук.
Просто ждать дальше. Это решение зафиксировано.

**В режиме `SORT`:**
- Поиск работает, но ответ упрощённый: «Запрос — Наряд №7».
- Отметки заблокированы: `MarkOrdinal` и др. → `Message`
  «Режим сортировки — отметки недоступны».

### 3.4. Глобальные команды

| Команда | Работает |
|---|---|
| `Stop` | Из любого состояния. Закрывает сессию. |
| `Pause` | Из `LISTENING`, `AWAITING_*`. → `PAUSED`. |
| `Resume` | Из `PAUSED`, `AWAITING_CONTINUE`. → `LISTENING`. |
| `Undo` | Везде. В `AWAITING_*` сбрасывает ожидание. |
| `SetMode(SEARCH/SORT)` | Меняет режим, **не состояние**. |

### 3.5. Реализация

- Новый enum `VoiceState` — 6 значений (без `SORTING`).
- Новый enum `VoiceMode` — 2 значения (`SEARCH`, `SORT`).
- `VoiceSession.state` + `VoiceSession.mode` — два независимых поля.
- Существующий `VoiceSessionMode` (если есть) — заменяется на `VoiceMode`.
- `VoiceCommandParser.parse(text, state, mode)` — учитывает оба.

---

## 4. Единый ввод

### 4.1. Схема
Ручной (строка) ────┐
├──▶ QueryNormalizer ──▶ Токенизация ──▶ Токены
Голосовой (Vosk) ───┘ │
▼
Кандидаты
│
▼
UnifiedSearch.search

text

Ручной и голосовой различаются **только способом получения текста**.

### 4.2. QueryNormalizer

| Шаг | Пример |
|---|---|
| Lowercase | `«КПД-109»` → `«кпд-109»` |
| Ё→е | `«трёхсот»` → `«трехсот»` |
| Trim | `«  1524  »` → `«1524»` |
| Collapse spaces | `«15  24»` → `«15 24»` |
| Replace dashes | `«15-24»` → `«15 24»` |
| Remove punctuation | `«1524.»` → `«1524»` |

**Не трогает структуру.** «109 00 31» — три токена остаются тремя.

### 4.3. Типы токенов

| Тип | Пример |
|---|---|
| `Prefix` | `KPD`, `NV`, `ACD` |
| `Number` | `1524`, `109`, `31` |
| `Ordinal` | `«первая»` → `1` |
| `CommandWord` | `«отметь»`, `«стоп»` |
| `Separator` | `«и»`, `«запятая»` |
| `Unknown` | `«семья»`, `«это»` |

### 4.4. Префиксы

- Слитно: `KPD1090031` → `prefix="KPD"`, `number="1090031"`.
- Раздельно: `KPD 109 00 31` → `prefix="KPD"`, числа склеиваются.
- Нет префикса: токен только `Number`.

Префикс сверяется с `VoicePrefixResolver`.

### 4.5. Кандидаты

1. Один `Number` → `["1524"]`.
2. Несколько `Number` → `["152401", "15|24|01"]`.
3. `Prefix + Number` → `["KPD1090031", "KPD109|00|31", "1090031"]`.

Порядок: от слитного к разделённому.

### 4.6. Примеры

**«пятнадцать двадцать четыре»** → `[15, 24]` → `["1524", "15|24"]`.

**«семь утра было холодно»** (в `LISTENING`):
`[Number(7), Unknown("утра"), Unknown("было"), ...]` → **молчание**.
Не `MarkOrdinal(7)`, не «Не понял». Просто ждём.

**«семь»** (в `AWAITING_WEIGHT`): `Number(7)` → `SetWeight(7.0)`.

### 4.7. Правило Unknown

**`Unknown` в середине фразы → вся фраза `Unknown`.**
В `LISTENING` — молчание. В `AWAITING_*` — фраза-подсказка.

---

## 5. Модель числа

### 5.1. Проблема

Б2а: правила `VoiceNumberParser` меняются под сценарий.
Б2б: `spellOut("1090031")` → «10 90 03 1» — структура теряется.

### 5.2. DigitGroup
data class DigitGroup(
val value: String,
val kind: GroupKind,
val sourceText: String? = null
)

text

| Kind | Пример |
|---|---|
| `PLAIN` | `109`, `31`, `5` |
| `LEADING_ZERO` | `00`, `000` |
| `PREFIX` | `KPD`, `NV` |
| `SINGLE` | `7`, `0` |

### 5.3. Группировка

| Слова | Группа |
|---|---|
| `сто девять` | `PLAIN("109")` |
| `ноль ноль` | `LEADING_ZERO("00")` |
| `тридцать один` | `PLAIN("31")` |

**Правила:**
1. Число 10..999 → новая группа, если предыдущая тоже 10..999.
2. Единица 0..9 → продолжение текущей.
3. Одиночные нули подряд → `LEADING_ZERO`.
4. Командное слово / разделитель → точка невозврата.

### 5.4. Кандидаты из групп

Из `[109, 00, 31]`:
1. Слитно: `"1090031"`.
2. С разделителями: `"109|00|31"`.
3. По парам справа: `"1|09|00|31"`.

Порядок — от соответствующего вводу к альтернативам.

### 5.5. Озвучка

| Группы | Озвучка |
|---|---|
| `[15, 24]` | «пятнадцать, двадцать четыре» |
| `[109, 00, 31]` | «сто девять, ноль ноль, тридцать один» |
| `[KPD, 109, 00, 31]` | «ка пэ дэ, сто девять, ноль ноль, тридцать один» |
| `[7]` | «семь» |

Озвучка идёт **по группам**, не по слитной строке. Фикс Б2б.

---

## 6. Канал поиска

### 6.1. SearchService — только in-memory

**Решение зафиксировано:** работаем **только через in-memory**.
Загружаем все пробы (`VoiceSearchRepository.loadAll()`) и ищем через
`UnifiedSearch`. Порогов и переключений на SQL **нет**.

**Почему:** проще. Для 10 000 проб хватает памяти. Если реально упрёмся —
добавим SQL позже, отдельным заходом.
SearchService.search(candidates) → SearchResult

text

### 6.2. Модель SearchResult
sealed class SearchResult {
data class Found(
val hits: List<VoiceSampleHit>,
val matchedKind: UnifiedMatchKind,
val matchedValue: String,
val level: Int,
val isUnique: Boolean,
val groups: List<DigitGroup>,
val queryTokens: List<QueryToken>
) : SearchResult()

data object NotFound : SearchResult()
data class Failed(val error: String) : SearchResult()
}

text

`SearchResult` хранит **и попадания, и структуру запроса**. Это позволяет
презентеру одинаково строить UI и TTS.

### 6.3. Что делает

1. Принимает кандидатов.
2. Вызывает `UnifiedSearch.search(...)`.
3. Упаковывает в `SearchResult`.
4. Возвращает.

`SearchService` **не решает**, что показать. Это работа презентера.

---

## 7. Модель ответа `Response`

### 7.1. Зачем

UI и TTS строятся **одним** объектом. Презентер — один на всех.

### 7.2. Структура
data class Response(
val kind: ResponseKind,
val primary: String,
val details: List<String>,
val reason: AnswerReason,
val sound: SoundKind,
val pause: Boolean,
val groups: List<DigitGroup>?,
val context: ResponseContext?
)

text

| Поле | Назначение |
|---|---|
| `kind` | Тип ответа |
| `primary` | Главная строка |
| `details` | Детали (фиксированный порядок) |
| `reason` | `AnswerReason` — цвет и звук |
| `sound` | Какой звук играть |
| `pause` | Ждём ли ответа |
| `groups` | Исходная структура (для TTS) |
| `context` | Доп. данные (вес, ordinal, выбор) |

### 7.3. ResponseKind

| Kind | Что значит |
|---|---|
| `FOUND_ONE` | Одна проба или скважина |
| `FOUND_MANY` | Несколько нарядов |
| `NOT_FOUND` | Нет в БД |
| `MARKED` | Проба отмечена |
| `MARKED_MULTIPLE` | Отмечено несколько |
| `MARKED_ALL` | Отмечены все |
| `UNMARKED` | Отметка снята |
| `WEIGHT_SET` | Вес установлен |
| `MODE_CHANGED` | Режим переключён |
| `NEXT` | Переход к следующей |
| `MESSAGE` | Информационное сообщение |
| `ASKING_WEIGHT` | Запрос веса |
| `ASKING_CHOICE` | Запрос выбора |
| `ASKING_CONTINUE` | Запрос «продолжить или стоп» |
| `UNKNOWN` | Не понял (только в `AWAITING_*`) |
| `STOPPED` | Сессия завершена |

### 7.4. SoundKind

| Kind | Когда |
|---|---|
| `OK` | Действие выполнено |
| `ATTENTION` | Нужно решение |
| `ERROR` | Не получилось |
| `NONE` | Без звука |

### 7.5. Маппинг SearchResult → Response

| SearchResult | kind | reason | sound | pause |
|---|---|---|---|---|
| `Found` (уникальный) | `FOUND_ONE` | `OK_SINGLE` | `NONE` | false |
| `Found` (несколько) | `FOUND_MANY` | `FOUND_MULTIPLE` | `ATTENTION` | true |
| `Found` (другой наряд) | `FOUND_ONE` | `FOUND_OTHER_ORDER` | `ATTENTION` | true |
| `Found` (другой участок) | `FOUND_ONE` | `FOUND_OTHER_AREA` | `ATTENTION` | true |
| `NotFound` | `NOT_FOUND` | `NOT_FOUND` | `ERROR` | false |
| `Failed` | `NOT_FOUND` | `SEARCH_FAILED` | `ERROR` | false |

### 7.6. Порядок details — фиксированный

**Режим `SEARCH` (скважина):**
primary = "Скважина 15 24"
details = ["Наряд №7", "Всего 3 пробы", "Отмечено 0", "Холостых 1"]

text

**Режим `SEARCH` (проба):**
primary = "Проба 15 24 01"
details = ["Наряд №7", "Уже отмечена"]

text

**Режим `SORT` — упрощённый формат:**
primary = "15 24 — Наряд №7"
details = [] (пусто)

text

**Только `запрос → номер наряда`.** Без статистики, без отметок.
Это правило для SORT-режима.

**Правила:**
- Порядок всегда один и тот же.
- Лишние детали не показываются (если холостых 0 — строки нет).
- `SORT` — всегда одна строка, без details.

### 7.7. Озвучка
spoken = primary + ". " + details.joinToString(". ") + "."

text

Для `SORT`: `spoken = primary + "."`. Только одна фраза.

---

## 8. Презентер

### 8.1. Задача

`Response` → `VisualRender` (UI) + `SpokenRender` (TTS).

### 8.2. VisualRender
data class VisualRender(
val state: AnswerState,
val title: String,
val shortStatus: String,
val reasonLabel: String,
val indicatorColor: Color
)

text

### 8.3. SpokenRender
fun renderSpoken(response: Response, groups: List<DigitGroup>?): String

text

1. `primary` — произносится (номера через `VoiceSpeaker.spellOut(groups)`).
2. `details` — через точку.
3. Если `pause == true` — добавляется вопрос.

### 8.4. Единый Presenter
data class RenderedResponse(
val visual: VisualRender,
val spoken: String,
val sound: SoundKind
)

fun render(response: Response, groups: List<DigitGroup>?): RenderedResponse

text

**Что это даёт:**
- UI и ГП получают один и тот же ответ.
- Формулировки совпадают гарантированно.
- Б3 закрыт.

### 8.5. Молчание при Unknown

Если `Response.kind == UNKNOWN` и состояние `LISTENING` — **презентер
возвращает пустой ответ**:
- `visual` — без изменений (не трогаем экран).
- `spoken` — пустая строка.
- `sound` — `NONE`.

**ГП просто ждёт дальше.** Это решение зафиксировано.

---

## 9. Соответствия команд

### 9.1. Таблица

| Фраза | VoiceCommand | Response.kind | sound |
|---|---|---|---|
| `стоп`, `хватит` | `Stop` | `STOPPED` | `NONE` |
| `пауза`, `паузу` | `Pause` | `MESSAGE` («Пауза») | `NONE` |
| `продолжить`, `продолжай` | `Resume` | `MESSAGE` («Продолжаю») | `NONE` |
| `отмена`, `верни`, `назад` | `Undo` | `MESSAGE` («Отменено») | `OK` |
| `вперёд`, `вперед` | `Redo` | `MESSAGE` («Повторено») | `OK` |
| `следующая` и др. | `Next` | `NEXT` | `OK` |
| `помощь` | `Help` | `MESSAGE` (список) | `NONE` |
| `сколько осталось` | `HowManyLeft` | `MESSAGE` | `NONE` |
| `показать отложенные` | `ShowPostponed` | `MESSAGE` | `NONE` |
| `показать найденные` | `ShowFound` | `MESSAGE` | `NONE` |
| `сортировка` | `SetMode(SORT)` | `MODE_CHANGED` | `OK` |
| `поиск` | `SetMode(SEARCH)` | `MODE_CHANGED` | `OK` |
| `первая` и др. | `MarkOrdinal(n)` | `MARKED` | `OK` |
| `первая вторая` | `MarkByNumbers` | `MARKED_MULTIPLE` | `OK` |
| `все`, `отметь все` | `MarkAll` | `MARKED_ALL` | `OK` |
| `отметь`, `эту` | `MarkCurrent` | `MARKED` / `ASKING_*` | `OK` / `ATTENTION` |
| `снять первую` | `ClearOrdinal` | `UNMARKED` | `OK` |
| `снять последнюю` | `ClearLast` | `UNMARKED` | `OK` |
| `снять все` | `ClearAll` | `MESSAGE` | `OK` |
| `вес два пять` | `SetWeight(2.5)` | `WEIGHT_SET` | `OK` |
| `снять отложенную` | `Unpostpone` | `MESSAGE` | `OK` |
| `снять` (в AWAITING_CHOICE) | `ChoiceRemove` | `UNMARKED` | `OK` |
| `отложить` (в AWAITING_CHOICE) | `ChoicePostpone` | `MESSAGE` | `OK` |
| `пропустить` | `ChoiceSkip` | `MESSAGE` | `NONE` |
| (число) в `LISTENING` | `Search` | `FOUND_*` / `NOT_FOUND` | по ситуации |
| (число) в `AWAITING_WEIGHT` | `SetWeight` | `WEIGHT_SET` | `OK` |

### 9.2. Фразы-вопросы (pause=true)

| Response.kind | Озвучка | Переход |
|---|---|---|
| `ASKING_WEIGHT` | «Вес?» | → `AWAITING_WEIGHT` |
| `ASKING_CHOICE` | «Снять, отложить или пропустить?» | → `AWAITING_CHOICE` |
| `ASKING_CONTINUE` | «Скажите продолжить или стоп» | → `AWAITING_CONTINUE` |

### 9.3. Множественная отметка с ошибкой

Если из «первая вторая третья» отметились только первая и третья:
spoken = "Отмечено: первая, третья."

text

Не переспрашивать. Не «Вторая не найдена». Просто перечислить, что
отметили. Решение зафиксировано.

---

## 10. Открытые вопросы и план

### 10.1. Что решено (в этой сессии)

| Вопрос | Решение |
|---|---|
| `SORT` — состояние или режим? | Режим (`VoiceMode`), ортогонален состоянию |
| SQL vs in-memory? | Только in-memory |
| `Unknown` в `LISTENING`? | Молчание |
| Множественная отметка с ошибкой? | «Отмечено: первая, третья» |
| `details` — порядок? | Фиксированный; для SORT — упрощённый формат |
| План реализации? | 5 заходов (сокращён) |
| Переписывание `VoiceDialog`? | Безопасно — рядом с текущим |

### 10.2. Что осталось

1. **Формулировка `primary` для `SORT`.** «15 24 — Наряд №7». Возможно,
   лучше «пятнадцать двадцать четыре, наряд семь». Уточнить при реализации.
2. **Открытие `VoiceDialog` при молчании.** Если ГП молчит, панель не
   меняется. Но должна ли она оставаться открытой? — Да, до явного
   «стоп»/закрытия.
3. **Обработка `Failed` vs `NotFound`.** Обе дают `NOT_FOUND`. Разница
   — только в логе. Не показываем.

### 10.3. План реализации — 5 заходов

**Заход 1 — `5.8.11-a`: Состояния и режимы.**
- `VoiceState` (enum, 6 значений).
- `VoiceMode` (enum, 2 значения).
- `VoiceSession`: `state` + `mode` — раздельные поля.
- Флаги (`isPaused`, `awaitingWeight`, `awaitingContinue`, `pendingMarkChoice`)
  становятся производными.
- **Файлы:** `VoiceSession.kt`, `VoiceState.kt` (новый), `VoiceMode.kt` (новый).

**Заход 2 — `5.8.11-b`: Единый ввод.**
- `QueryToken` (sealed).
- `QueryNormalizer`.
- `QueryTokenizer`.
- **Файлы:** `QueryToken.kt`, `QueryNormalizer.kt`, `QueryTokenizer.kt` (все новые).

**Заход 3 — `5.8.11-c`: Модель числа.**
- `DigitGroup` + `DigitGrouper` + `GroupToCandidates`.
- `VoiceSpeaker.spellOut(groups: List<DigitGroup>)`.
- **Файлы:** `DigitGroup.kt` (новый), `DigitGrouper.kt` (новый),
  `GroupToCandidates.kt` (новый), `VoiceSpeaker.kt` (правка).

**Заход 4 — `5.8.11-d`: Модель ответа.**
- `SearchService` (in-memory).
- `SearchResult`.
- `Response` + маппинг.
- `Presenter`.
- **Файлы:** `SearchService.kt`, `SearchResult.kt`, `Response.kt`,
  `Presenter.kt` (все новые).

**Заход 5 — `5.8.11-e`: Переключение на новый путь.**
- `setQuery` → `QueryNormalizer` + `QueryTokenizer` + `SearchService`.
- `VoiceCommandParser` → принимает `VoiceState` + `VoiceMode`.
- `VoiceDialog` → использует `Response` + `Presenter`.
- **Безопасно:** новые функции рядом со старыми, старое не удаляем
  до проверки на устройстве.
- **Файлы:** `ReconciliationViewModel.kt`, `VoiceCommandParser.kt`,
  `VoiceDialog.kt`.

**Итого:** 5 заходов, каждый — 1–3 файла.

### 10.4. Безопасный переход `VoiceDialog`

В заходе 5:
1. `VoiceDialog` **не переписывается целиком**.
2. Рядом со старым `describeResult()` добавляется `renderFromResponse()`.
3. Старый `VoiceExecResult` остаётся — им пользуется текущий путь.
4. `ReconciliationViewModel.voiceExecute` получает **два варианта**:
   старый (возвращает `VoiceExecResult`) и новый (возвращает `Response`).
5. Переключаем `VoiceDialog` на новый. Если на устройстве всё работает —
   удаляем старый отдельным заходом. Если нет — откат.

Это **безопаснее**, чем единый болезненный переписывающий заход.
