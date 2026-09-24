# PROGRESS.md — история заходов

## 5.8.11-e4 серия — рефакторинг ГП

**Контекст:** аудит голосового пути, сведение UI и ГП в один путь,
закрепление скважины, очередь мультизапроса.

### `5.8.11-e4-pin-4` (закрыт unit) — числительные в pin + fallback 14→4
- `VoiceCommandParser.parseForPinned`: фраза из числительных склеивается
  в одно число («тринадцать шестьдесят семь» → `MarkOrdinal(1367)`,
  «тысяча пятьсот двадцать четыре» → `MarkOrdinal(1524)`).
- `ReconciliationViewModel.voiceMarkOrdinal`: fallback 14↔4.
- **Открыто:** fallback 400→4 — пачка `e4-pin-5`.

### `5.8.11-e4-pin-3` (закрыт unit) — вес «X сотни», «следующая X»
- `VoiceCommandParser.parseWeightAnswer`: «два семьсот» → 2,7.
- `VoiceCommandParser.parse`: «следующая X» → `Find(X)`.
- `VoiceCommandParser.parseForPinned`: любое число → `MarkOrdinal`
  (без Search).
- `VoiceModels.FoundOne.queueSize`: префикс «Найдено N скважин».
- `VoiceDialog.buildFoundOnePhrase`: показывает префикс при `queueSize > 1`.

### `5.8.11-e4-pin-2` (закрыт unit) — очередь мультизапроса
- `VoiceCommand.NextInQueue`.
- `VoiceSession.queue`, `enqueue`, `nextInQueue`, `clearQueue`, `hasQueue`.
- `ReconciliationViewModel.voiceSort`: строит очередь, первый — pin,
  остальные в `queue`.
- `voiceNextInQueue`: переключение в очереди.
- **Ручной ввод UI сбрасывает** pin и очередь.

### `5.8.11-e4-pin-1` (закрыт unit) — закрепление скважины
- `VoiceState.FOUND_PINNED`.
- `PinnedScope` — orderId, orderTitle, areaTitle, wellNumber.
- `VoiceSession.pin/unpin/isPinned`.
- `VoiceCommandParser.parseForPinned`: в pin голое число → `MarkOrdinal`.
- `ReconciliationViewModel.voiceSearch`: после `FoundOne` — pin.
- `voiceNext` / `Undo` / ручной ввод — сбрасывают pin.

### `5.8.11-e4e-bundle` (закрыт unit) — мимикрия + единый путь
- `VoiceSpeaker.spellOut(groups)`: без запятых, префикс одним словом
  («капэдэ»), W → «даблю».
- `VoiceModels.FoundOne.groups` — структура ввода.
- `ReconciliationViewModel.voiceSearch`: переход на `SearchService`
  (`QueryTokenizer` → `DigitGrouper` → `SearchService`).
- `VoiceDialog`: `spellOut(groups)` при `groups.isNotEmpty()`.
- **Фикс после падения теста:** `spellPlain` для 4+ цифр → словами,
  ведущий ноль → по цифрам.

### `5.8.11-e4-tests` (закрыт unit) — покрытие парсера
- `VoiceCommandParserWeightsTest` — 17 тестов (вес, мусор, состояния).
- `VoiceCommandParserFindTest` — 8 тестов («найди», формы).
- `VoiceCommandParserPausedTest` — 6 тестов (PAUSED).
- Фикс опечатки «хатит» → «хватит» в `parse()`, `parseForContinue()`.

### `5.8.11-e4a` (закрыт unit) — вес в `AWAITING_WEIGHT`
- `VoiceExecResult.WeightSet` и обработка `VoiceCommand.SetWeight`.
- `voiceSetWeight`: холостая идёт через `setBlankWeightAndMarkFound`.

### `5.8.11-e4b` (закрыт unit) — фильтр мусора в весе
- `VoiceCommandParser.isCleanWeightPhrase` — отсекает фразы с
  посторонними словами.
- `weightAllowedWords` — белый список.

### `5.8.11-e4g` (закрыт unit) — Pause в `AWAITING_WEIGHT`
- `parseForWeight`: «пауза»/«паузу» → `Pause`.
- Опечатка «хатит» → «хватит» в трёх методах.

### `5.8.11-e4g3` (закрыт unit) — команда «Найди»
- `VoiceCommand.Find(query: String?)`.
- `VoiceGrammar`: глаголы «найди», «найти», «ищи», «искать», «поищи».
- `ReconciliationViewModel`: обработка в `voiceExecute`.

### `5.8.11-e4-fix-2` (закрыт unit) — кулдаун TTS
- `VoiceController.RESUME_DELAY_MS`: 800 → 250 мс.

### `5.8.11-e4-fix-1` (закрыт unit) — «четвертью» из грамматики
- `VoiceGrammar.explicitCommandWords`: убрано «четвертью».

### `5.8.11-e4c` (откачен)
- Числительные в `QueryTokenizer`. Менял UI-путь. Откатили.

### Архитектурные решения серии

- **Pin скважины** — `FOUND_PINNED`, `PinnedScope`. Реализовано.
- **Очередь мультизапроса** — `queue`, `NextInQueue`. Реализовано.
- **Мимикрия TTS** — `spellOut(groups)`. Реализовано.
- **Маркеры намерения** — `AWAITING_MARK` и др. Запланировано.
- **Динамические словари Vosk** — по состояниям. Запланировано.
- **Приоритет левой части** — `e4-search-context`. Запланировано.

## 5.8.11 серия — унификация поиска и ответа (SEARCH_MODEL)

Спецификация — `SEARCH_MODEL.md`.

### `5.8.11-d2` (закрыт unit) — Response + Presenter
### `5.8.11-d1` (закрыт unit) — SearchResult + SearchService
### `5.8.11-c2` (закрыт unit) — групповые кандидаты + озвучка
### `5.8.11-c1` (закрыт unit) — DigitGroup + DigitGrouper
### `5.8.11-b` (закрыт unit) — единый ввод
### `5.8.11-a` (закрыт unit) — состояния ГП

## 5.8.10 серия — настройки UI, онбординг, импорт, голос

- `5.8.10-g1/g2` (✅ device) — панель ГП.
- `5.8.10-f` (✅ device) — приоритет имени листа.
- `5.8.10-e` (закрыт unit) — коллизия по orderTitle.
- `5.8.10-d` (закрыт unit) — проверка импорта по имени участка.
- `5.8.10-c` (закрыт unit) — озвучка списка проб.
- `5.8.10-b` (закрыт unit) — И-9: онбординг.
- `5.8.10-a` (закрыт unit) — И-10: showCharacteristic.

## 5.8.6 серия — Vosk-полировка

- `5f`, `5g`, `5c`, `5a`, `4`, `3`, `2`, `2a`.

## 5.8.9 серия — голосовой ввод

- `f-2b`, `i-1/2/3`, `d-3c2b1/3c2b2`, `f-1a-fix-1`, `d-2a`, `g-1/3`.

## Ранее (выборочно)

- `5.8.9h-2` — `UnifiedSearch` в UI и ГП.
- `5.8.9-infra-2d` — CI вручную.
- Базовый голосовой ввод, Vosk-модель, Excel-импорт, фото, заметки.