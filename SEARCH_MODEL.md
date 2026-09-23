# SEARCH_MODEL.md — единая модель поиска и ответа

> Спецификация того, **как в проекте устроен поиск** — и ручной, и голосовой.
>
> Цель: **свести две существующие системы к одной**. Чтобы ручной ввод и ГП
> находили одно и то же, отвечали одинаково и вели себя предсказуемо.

**Статус:** 🟢 полный документ
**Обновлён:** 2026-09-23
**Связанные файлы:** `VOICE.md`, `DECISIONS.md`, `AI_RULES.md`

---

## 0. О документе

### Зачем он

Сейчас в проекте **две параллельные системы поиска**:

1. **Ручной ввод** — пользователь вводит номер в строку, дебаунс 500 мс,
   результат в `MatchInfo`.
2. **Голосовой ввод** — пользователь говорит, Vosk распознаёт, результат
   в `VoiceExecResult`.

Обе используют один движок `UnifiedSearch`, но **презентеры разные**.
Отсюда:
- разные формулировки на экране и в TTS;
- разные крайние случаи;
- разное поведение в сортировке;
- ложные срабатывания ГП.

Документ описывает **единую модель**, к которой всё это сведётся.

### Что описываем

- Модель ввода (ручной + голосовой).
- Модель поиска.
- Модель ответа (`Response`).
- Презентер — как из `Response` получаются UI-строки и TTS-фразы.
- Состояния ГП и что в каком состоянии принимается.

### Что НЕ описываем

- UI-компоновку экрана (это `DECISIONS.md`).
- Звуковую карту (это `VoiceFeedback.kt` + `DECISIONS.md §13.4`).
- Vosk endpoint tuning (И-24, отложено).
- Схему БД (это `DATABASE.md`).

---

## 1. Термины и границы

### 1.1. Ключевые термины

| Термин | Что значит |
|---|---|
| **Запрос** | Пользователь ввёл или сказал что-то. Один токен или несколько. |
| **Токен** | Один фрагмент запроса: число, код, префикс. «15 24 01» → 3 токена. |
| **Кандидат** | Нормализованный вид токена, который идёт в поиск: `«пятнадцать»` → `«15»`. |
| **Попадание (hit)** | Проба из БД, которая совпала с запросом. Тип `VoiceSampleHit`. |
| **Поиск** | Операция сопоставления кандидатов с базой. Возвращает `SearchResult`. |
| **Ответ (`Response`)** | Единая модель того, что показать и сказать. Строится из `SearchResult`. |
| **Презентер** | Функция `Response` → `visual` (строки UI) + `spoken` (фраза TTS). |
| **Состояние ГП** | Режим, в котором находится голосовой помощник. Определяет, что он принимает. |

### 1.2. Три «двойки», которые убираем

| Что | Ручной | Голосовой |
|---|---|---|
| **Парсер** | `setQuery()` + `analyzeMatch()` | `VoiceCommandParser.parse()` |
| **Модель ответа** | `MatchInfo` | `VoiceExecResult` |
| **Презентер** | UI рисует из `MatchInfo` | `VoiceDialog` строит фразы для TTS |

После унификации:

| Что | Единый путь |
|---|---|
| **Парсер** | `QueryNormalizer` → нормализованные токены |
| **Модель ответа** | `SearchResult` → `Response` |
| **Презентер** | `Presenter.render(Response)` → `visual` + `spoken` |

### 1.3. Границы

**Внутри спецификации:** парсинг чисел, нормализация префиксов, логика
сопоставления, формирование `SearchResult` и `Response`, озвучка.

**Вне спецификации:** визуальный дизайн, звуковая карта, работа с БД, Vosk.

---

## 2. Аудит текущего состояния

### 2.1. Что уже унифицировано ✅

- **Единый движок поиска** — `UnifiedSearch` (7 уровней совпадения).
- **Единая модель состояния** — `AnswerReason` / `AnswerState` (8 причин, 4 состояния).
- **Единая палитра** — `AnswerStateColors`.
- **Единая звуковая карта** — `VoiceFeedback` (3 звука).

### 2.2. Что НЕ унифицировано ❌

- **Две модели ответа:** `MatchInfo` (UI) и `VoiceExecResult` (ГП).
- **Два презентера:** `SearchScreen` (UI) и `VoiceDialog.describeResult()` (TTS).
- **Два парсера:** `setQuery` / `analyzeMatch` и `VoiceCommandParser.parse`.
- **Разная логика `filterMode`:** у ручного есть, у голосового нет.

### 2.3. Конкретные расхождения (болячки)

- **Б1. Ложные срабатывания ГП.** `VoiceCommandParser` не знает состояния.
- **Б2а. Глюки с цифрами.** `VoiceNumberParser` — правила меняются под сценарий.
- **Б2б. Произношение номеров.** `spellOut("1090031")` → «10 90 03 1».
- **Б3. Разные ответы UI и TTS.** Две функции дают разные формулировки.
- **Б4. Сортировка сбрасывается.** `voiceSort()` при ненайденном вызывает `setQuery`.
- **Б5. Неточные срабатывания.** Следствие Б1–Б4.
- **Б6. Нет единой логики поиска.** Частично решена, презентеры разные.

### 2.4. Диагноз

Корень — **разные презентеры одной модели**. Всё остальное — следствия.
Два парсера → Две модели → Два презентера → Б1, Б2б, Б3, Б4

text

**Что решаем в первую очередь:** модель `Response` + состояния ГП.

### 2.5. Что менять не будем

`UnifiedSearch`, `AnswerReason`, `AnswerState`, `AnswerStateColors`, `VoiceFeedback`.

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
| `SORTING` | Режим сортировки. Отметки отключены. |

### 3.2. Диаграмма переходов
IDLE ──[старт]──▶ LISTENING
│
┌───────────┼───────────┬─────────────┬──────────────┐
▼ ▼ ▼ ▼ ▼
[пауза] [Вес?] [Найден N] [Уже отмечена] [сортировка]
│ │ │ │ │
▼ ▼ ▼ ▼ ▼
PAUSED AWAITING_ AWAITING_ AWAITING_ SORTING
WEIGHT CONTINUE CHOICE
│ │ │ │ │
└───────────┴───────────┴─────────────┴──────────────┘
[число/продолжить/снять/поиск]
│
▼
LISTENING

text

### 3.3. Что принимается в каждом состоянии

| Состояние | Принимает | Всё остальное |
|---|---|---|
| `LISTENING` | Всё: поиск, все команды | — |
| `AWAITING_WEIGHT` | Число-вес, `Undo`, `Stop` | «Скажите вес или отмена» |
| `AWAITING_CONTINUE` | `Resume`, `Pause`, `Stop`, `Search`, `Sort` | «Скажите продолжить или стоп» |
| `AWAITING_CHOICE` | `ChoiceRemove/Postpone/Skip`, `MarkCurrent` (для POSTPONED), `Undo`, `Stop` | «Скажите: снять, отложить или пропустить» |
| `PAUSED` | `Resume`, `Stop` | «Пауза. Скажите продолжить или стоп» |
| `SORTING` | Поиск (без отметок), `Sort`, `Next`, `Stop`, `Pause`, `Resume`, `Undo`, `Redo`, `Help`, `SetMode(SEARCH)` | Отметки заблокированы |

### 3.4. Глобальные команды

| Команда | Работает |
|---|---|
| `Stop` | Из любого состояния. Закрывает сессию. |
| `Pause` | Из `LISTENING`, `AWAITING_*`. → `PAUSED`. |
| `Resume` | Из `PAUSED`, `AWAITING_CONTINUE`. → `LISTENING`. |
| `Undo` | Везде. В `AWAITING_*` сбрасывает ожидание. |

### 3.5. Реализация

**Новый enum `VoiceState`.** `VoiceSession.state` — единственный источник
правды. Флаги (`isPaused`, `awaitingWeight`, …) — производные от `state`.

**Парсер принимает состояние.** `VoiceCommandParser.parse(text, state)`.
Решение о `Unknown` — на основе состояния. Это убирает Б1.

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
Дальше — общий путь.

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

Для каждого токена — набор кандидатов:
1. Один `Number` → `["1524"]`.
2. Несколько `Number` → `["152401", "15|24|01"]`.
3. `Prefix + Number` → `["KPD1090031", "KPD109|00|31", "1090031"]`.

Порядок: от слитного к разделённому.

### 4.6. Примеры

**Голос «пятнадцать двадцать четыре»** → токены `[15, 24]` →
кандидаты `["1524", "15|24"]` → поиск.

**Голос «семь утра было холодно»** (в `LISTENING`):
токены `[Number(7), Unknown("утра"), ...]`. `Unknown` в середине →
вся фраза `Unknown`. **Не `MarkOrdinal(7)`**.

**Голос «семь»** (в `AWAITING_WEIGHT`):
токен `Number(7)`, состояние ждёт вес → `SetWeight(7.0)`.
**Не `MarkOrdinal`**.

---

## 5. Модель числа

### 5.1. Проблема

Б2а: правила `VoiceNumberParser` меняются под сценарий.
Б2б: `spellOut("1090031")` → «10 90 03 1» — структура ввода теряется.

### 5.2. DigitGroup — единица модели
data class DigitGroup(
val value: String, // «109», «00», «31»
val kind: GroupKind,
val sourceText: String? // «сто девять» (если голосом)
)

text

| Kind | Что значит | Пример |
|---|---|---|
| `PLAIN` | Обычная группа 1–3 цифры | `109`, `31`, `5` |
| `LEADING_ZERO` | Группа нулей | `00`, `000` |
| `PREFIX` | Латинский префикс | `KPD`, `NV` |
| `SINGLE` | Одиночная цифра | `7`, `0` |

**Почему нули отдельно:** «ноль ноль» — это разряд, не число.

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

`VoiceSpeaker.spellOut(groups: List<DigitGroup>)`:

| Группы | Озвучка |
|---|---|
| `[15, 24]` | «пятнадцать, двадцать четыре» |
| `[109, 00, 31]` | «сто девять, ноль ноль, тридцать один» |
| `[KPD, 109, 00, 31]` | «ка пэ дэ, сто девять, ноль ноль, тридцать один» |
| `[7]` | «семь» |

Озвучка идёт **по группам**, не по слитной строке. Это фикс Б2б.

---

## 6. Канал поиска

### 6.1. Единый SearchService

**Сейчас:**
- Ручной — `SampleDao.findOrderIdsByQuery` (SQL).
- Голосовой — `VoiceSearchRepository.loadAll()` + `UnifiedSearch` (in-memory).

**После унификации:** один `SearchService` с выбором стратегии.
SearchService.search(candidates, scope) → SearchResult

text

**Выбор стратегии:**

| Размер БД | Стратегия |
|---|---|
| < 5000 проб | In-memory: `loadAll()` + `UnifiedSearch` |
| ≥ 5000 проб | SQL: `findOrderIdsByQuery` + `UnifiedSearch` по подмножеству |

Порог — настройка, можно изменить без правки логики.

### 6.2. Модель SearchResult
sealed class SearchResult {
data class Found(
val hits: List<VoiceSampleHit>,
val matchedKind: UnifiedMatchKind, // WELL / SAMPLE / NONE
val matchedValue: String, // что нашли
val level: Int, // уровень совпадения 0..6
val isUnique: Boolean, // один наряд?
val groups: List<DigitGroup>, // исходная структура (для озвучки)
val queryTokens: List<QueryToken> // что ввёл пользователь
) : SearchResult()

data object NotFound : SearchResult()
data class Failed(val error: String) : SearchResult()
}

text

**Ключевое:** `SearchResult` хранит **и попадания, и структуру запроса**.
Отсюда презентер одинаково строит UI и TTS.

### 6.3. Что делает

1. Принимает кандидатов от `QueryToCandidates`.
2. Вызывает `UnifiedSearch.search(...)`.
3. Упаковывает результат в `SearchResult`.
4. Возвращает.

`SearchService` **не решает**, что показать. Это работа презентера.

---

## 7. Модель ответа `Response`

### 7.1. Зачем

Сейчас UI и TTS строятся **двумя разными функциями** из одного и того же
`SearchResult`. Отсюда Б3 — разные формулировки.

`Response` — **единый объект**, из которого и UI, и TTS берут данные.
Презентер — один на всех.

### 7.2. Структура Response
data class Response(
val kind: ResponseKind,
val primary: String, // «Скважина 15 24» — суть
val details: List<String>, // структурированные детали
val reason: AnswerReason, // причина (для цвета)
val sound: SoundKind, // OK / ATTENTION / ERROR / NONE
val pause: Boolean, // ждать ответа?
val groups: List<DigitGroup>?, // для озвучки номеров
val context: ResponseContext? // доп. данные (вес, проба, …)
)

text

| Поле | Назначение |
|---|---|
| `kind` | Тип ответа (FOUND_ONE / FOUND_MANY / MARKED / …) |
| `primary` | Главная строка: «Скважина 15 24» |
| `details` | Детали: «Наряд №7», «Проб 3», «Отмечено 0» |
| `reason` | `AnswerReason` — определяет цвет и звук |
| `sound` | Какой звук играть |
| `pause` | Ждём ли ответа (для AWAITING_*) |
| `groups` | Исходная структура номеров (для TTS) |
| `context` | Дополнительные данные (вес, ordinal, выбор) |

### 7.3. ResponseKind

| Kind | Что значит |
|---|---|
| `FOUND_ONE` | Одна проба или скважина |
| `FOUND_MANY` | Несколько нарядов с этим номером |
| `NOT_FOUND` | Нет в БД |
| `MARKED` | Проба отмечена |
| `MARKED_MULTIPLE` | Отмечено несколько |
| `MARKED_ALL` | Отмечены все |
| `UNMARKED` | Отметка снята |
| `WEIGHT_SET` | Вес установлен |
| `MODE_CHANGED` | Режим переключён |
| `NEXT` | Переход к следующей скважине |
| `MESSAGE` | Информационное сообщение |
| `ASKING_WEIGHT` | Запрос веса |
| `ASKING_CHOICE` | Запрос выбора (снять/отложить/пропустить) |
| `ASKING_CONTINUE` | Запрос «продолжить или стоп» |
| `UNKNOWN` | Не понял |
| `STOPPED` | Сессия завершена |

### 7.4. SoundKind

| Kind | Когда |
|---|---|
| `OK` | Действие выполнено |
| `ATTENTION` | Нужно решение |
| `ERROR` | Не получилось |
| `NONE` | Без звука (успешный поиск, IDLE, пауза) |

### 7.5. Маппинг SearchResult → Response

**Единственное место**, где определяется формулировка ответа.

| SearchResult | Response.kind | reason | sound | pause |
|---|---|---|---|---|
| `Found` (уникальный, SAMPLE) | `FOUND_ONE` | `OK_SINGLE` | `NONE` | false |
| `Found` (уникальный, WELL) | `FOUND_ONE` | `OK_SINGLE` | `NONE` | false |
| `Found` (несколько нарядов) | `FOUND_MANY` | `FOUND_MULTIPLE` | `ATTENTION` | true |
| `Found` (другой наряд) | `FOUND_ONE` | `FOUND_OTHER_ORDER` | `ATTENTION` | true |
| `Found` (другой участок) | `FOUND_ONE` | `FOUND_OTHER_AREA` | `ATTENTION` | true |
| `NotFound` | `NOT_FOUND` | `NOT_FOUND` | `ERROR` | false |
| `Failed` | `NOT_FOUND` | `SEARCH_FAILED` | `ERROR` | false |

### 7.6. Поля primary и details

**Правило:** `primary` — короткая суть. `details` — список деталей.

**Пример для скважины:**
primary = "Скважина пятнадцать двадцать четыре"
details = [
"Наряд №7",
"Всего три пробы",
"Отмечено ноль",
"Холостых одна"
]

text

**Пример для пробы:**
primary = "Проба пятнадцать двадцать четыре ноль один"
details = [
"Наряд №7",
"Уже отмечена"
]

text

**Озвучка:** `primary + ". " + details.joinToString(". ") + "."`.

**UI:** `primary` — заголовок, `details` — строки под ним.

**Один объект — два представления.**

---

## 8. Презентер

### 8.1. Задача

Преобразовать `Response` в:
- `visual: VisualRender` — строки для UI.
- `spoken: String` — фраза для TTS.

### 8.2. VisualRender
data class VisualRender(
val state: AnswerState, // для цвета (OK / ATTENTION / ERROR / IDLE)
val title: String, // «Наряд 7 · проба NV152601»
val shortStatus: String, // «Найдено»
val reasonLabel: String, // «Другой наряд»
val indicatorColor: Color // из AnswerStateColors
)

text

Строится из `Response.reason` + `Response.primary`.

### 8.3. SpokenRender
fun renderSpoken(response: Response, groups: List<DigitGroup>?): String

text

**Правила:**

1. `primary` — произносится как есть (номера через `VoiceSpeaker.spellOut(groups)`).
2. `details` — через запятую или точку.
3. Если `response.pause == true` — добавляется вопрос:
   - `ASKING_WEIGHT` → «Вес?»
   - `ASKING_CHOICE` → «Снять, отложить или пропустить?»
   - `ASKING_CONTINUE` → «Скажите продолжить или стоп»

**Пример:**
Response(
kind = FOUND_ONE,
primary = "Скважина 15 24",
details = ["Наряд №7", "Всего три пробы", "Отмечено ноль"],
...
)

spoken = "Скважина пятнадцать двадцать четыре. Наряд семь. " +
"Всего три пробы. Отмечено ноль."

text

### 8.4. Единый Presenter

**Одна точка входа** — `Presenter.render(response)`:
data class RenderedResponse(
val visual: VisualRender,
val spoken: String,
val sound: SoundKind
)

fun render(response: Response, groups: List<DigitGroup>?): RenderedResponse

text

**Что это даёт:**
- UI и ГП получают **один и тот же** ответ.
- Формулировки гарантированно совпадают.
- Добавление новой команды — в одном месте (`Response` + маппинг).
- Б3 (разные ответы) закрыт.

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
| `помощь` | `Help` | `MESSAGE` (список команд) | `NONE` |
| `сколько осталось` | `HowManyLeft` | `MESSAGE` (список) | `NONE` |
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
| `снять все` | `ClearAll` | `MESSAGE` («Снято N») | `OK` |
| `вес два пять` | `SetWeight(2.5)` | `WEIGHT_SET` | `OK` |
| `снять отложенную` | `Unpostpone` | `MESSAGE` | `OK` |
| `снять` (в AWAITING_CHOICE) | `ChoiceRemove` | `UNMARKED` | `OK` |
| `отложить` (в AWAITING_CHOICE) | `ChoicePostpone` | `MESSAGE` | `OK` |
| `пропустить` (в AWAITING_CHOICE) | `ChoiceSkip` | `MESSAGE` | `NONE` |
| (число) в `LISTENING` | `Search` | `FOUND_*` / `NOT_FOUND` | по ситуации |
| (число) в `AWAITING_WEIGHT` | `SetWeight` | `WEIGHT_SET` | `OK` |

### 9.2. Фразы-вопросы (pause=true)

| Response.kind | Озвучка | Переход |
|---|---|---|
| `ASKING_WEIGHT` | «Вес?» | → `AWAITING_WEIGHT` |
| `ASKING_CHOICE` | «Снять, отложить или пропустить?» | → `AWAITING_CHOICE` |
| `ASKING_CONTINUE` | «Скажите продолжить или стоп» | → `AWAITING_CONTINUE` |

---

## 10. Открытые вопросы

### 10.1. Что осталось не решённым

1. **Порог SQL vs in-memory.** 5000 проб — гипотеза. Может быть, 2000 или 10000. Уточняется по замерам на устройстве.

2. **Реакция на `Unknown` в `LISTENING`.** Если пользователь сказал что-то непонятное — молчать или сказать «Не понял»? Сейчас — говорит. Возможно, стоит молчать, если это не команда.

3. **Множественная отметка при ошибке.** Если из «первая вторая третья» отметились только первая и третья — что говорить? «Отмечено: первая, третья» (текущее) или переспрашивать?

4. **Связь с `VoiceSessionMode`.** `SORT` — это состояние (`SORTING`) или режим? Сейчас — режим, но в спецификации описан как состояние. Уточнить.

5. **Порядок `details`.** «Наряд, Всего, Отмечено, Холостые» — сейчас так. Может, менять под ситуацию.

### 10.2. Что НЕ входит в спецификацию

- Vosk endpoint tuning (И-24).
- UI-компоновка (DECISIONS §13).
- Звуковая карта (DECISIONS §13.4).
- Схема БД.

### 10.3. План реализации

Порядок заходов:

1. `VoiceState` (enum + `VoiceSession.state`).
2. `QueryToken` + `QueryNormalizer` + `QueryTokenizer`.
3. `DigitGroup` + `DigitGrouper` + `GroupToCandidates`.
4. `SearchService` (единый).
5. `SearchResult`.
6. `Response` + маппинг `SearchResult → Response`.
7. `Presenter` (единый).
8. Перевод `setQuery` на новый путь.
9. Перевод `VoiceCommandParser` на новый путь.
10. Перевод `VoiceSpeaker` на `List<DigitGroup>`.

Каждый заход — 1–2 файла, отдельная ветка, CI + device-check.

### 10.4. Риски

- **Vosk-грамматика.** После изменения парсера может потребоваться
  корректировка `VoiceGrammar.kt`. Проверять на устройстве.
- **Существующие тесты.** Многие завязаны на текущее поведение. Часть
  придётся переписать.
- **Обратная совместимость.** `VoiceExecResult` используется в `VoiceDialog`.
  При переходе на `Response` — переписать разом, не частями.
