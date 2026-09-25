# PROGRESS.md — история заходов

## 5.8.11-e4 серия — рефакторинг ГП

**Контекст:** аудит голосового пути, сведение UI и ГП в один путь,
закрепление скважины, очередь мультизапроса, честная обработка ошибок.

### `5.8.11-e4-pin-7` (закрыт unit) — честная ошибка вместо fallback
- Отказ от fallback: Vosk путает «четвёртая» ↔ «четырнадцатая» в обе
  стороны, отличить намерение нельзя. Молчаливая подмена опасна.
- `VoiceMarkOrdinalFallback`: `resolve` и `isSubstituted` удалены.
  Осталась `candidatesFor` / `hintFor` — какие числа «спорные».
- `ReconciliationViewModel.voiceMarkOrdinal`: если пробы с распознанным
  номером нет — `MarkOrdinalNotFound(ordinal, hintOrdinal)`.
- `VoiceExecResult.MarkOrdinalNotFound` — новое. UI и озвучка показывают:
  «Пробы №14 нет. Если нужна №4 — произнесите «четыре».»
- Accent fix: слово «Распознано» убрано из голоса (резало слух).
- Тесты: `VoiceMarkOrdinalFallbackTest` — 26 тестов на `hintFor`
  и `candidatesFor`.
- **Фикс после падения теста:** для 1..3 кандидатов нет — Vosk не
  путает «перв/втор/трет» с 10/100/1000.

### `5.8.11-e4-pin-6` (закрыт unit, откачен в pin-7) — показ подмены
- `VoiceExecResult.Marked.recognizedOrdinal` — исходный номер от Vosk.
- `VoiceModels.Marked.isSubstituted()` — extension «была ли подмена».
- `VoiceDialog`: `Распознано «четырнадцатая» → Четвёртая отмечена.`
- Признано ошибочным: показ подмены не решает главную проблему —
  молчаливое неверное действие. Откачено в pin-7.

### `5.8.11-e4-pin-5` (закрыт unit) — расширение fallback
- `VoiceMarkOrdinalFallback` — новый файл. Fallback расширен:
  14↔4, 40↔4, 90↔9, 400↔4, 900↔9, 4000↔4, 9000↔9.
- `ReconciliationViewModel.voiceMarkOrdinal` делегирует в helper.
- Тесты: `VoiceMarkOrdinalFallbackTest` — 6 тестов.

### `5.8.11-e4-pin-4` (закрыт unit) — числительные в pin + fallback 14→4
- `VoiceCommandParser.parseForPinned`: фраза из числительных склеивается
  в одно число («тринадцать шестьдесят семь» → `MarkOrdinal(1367)`).
- `ReconciliationViewModel.voiceMarkOrdinal`: fallback 14↔4.

### `5.8.11-e4-pin-3` (закрыт unit) — вес «X сотни», «следующая X»
- `VoiceCommandParser.parseWeightAnswer`: «два семьсот» → 2,7.
- `VoiceCommandParser.parse`: «следующая X» → `Find(X)`.
- `VoiceModels.FoundOne.queueSize`: префикс «Найдено N скважин».

### `5.8.11-e4-pin-2` (закрыт unit) — очередь мультизапроса
- `VoiceCommand.NextInQueue`.
- `VoiceSession.queue`, `enqueue`, `nextInQueue`, `clearQueue`, `hasQueue`.
- `ReconciliationViewModel.voiceSort`: очередь при мультизапросе.

### `5.8.11-e4-pin-1` (закрыт unit) — закрепление скважины
- `VoiceState.FOUND_PINNED`.
- `PinnedScope` — orderId, orderTitle, areaTitle, wellNumber.
- `VoiceSession.pin/unpin/isPinned`.
- `VoiceCommandParser.parseForPinned`: голое число → `MarkOrdinal`.

### `5.8.11-e4e-bundle` (закрыт unit) — мимикрия + единый путь
- `VoiceSpeaker.spellOut(groups)`: без запятых, префикс одним словом.
- `ReconciliationViewModel.voiceSearch`: через `SearchService`.

### `5.8.11-e4-tests` (закрыт unit) — покрытие парсера
- `VoiceCommandParserWeightsTest` — 17 тестов.
- `VoiceCommandParserFindTest` — 8 тестов.
- `VoiceCommandParserPausedTest` — 6 тестов.

### Архитектурные решения серии

- **Pin скважины** — `FOUND_PINNED`, `PinnedScope`. Реализовано.
- **Очередь мультизапроса** — реализовано.
- **Мимикрия TTS** — `spellOut(groups)`. Реализовано.
- **Честная ошибка вместо fallback** — реализовано (pin-7).
- **Маркеры намерения** — запланировано (`e4-markers`).
- **Подтверждение массовых** — запланировано (`e4-markers`).
- **Динамические словари Vosk** — запланировано (`e4-dicts`).

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
