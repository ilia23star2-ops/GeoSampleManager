# PROGRESS.md — история заходов

## 5.8.11-sort-fix серия — SORT и голосовая навигация

**Дата:** 2026-09-26 (вечер)
**Ветка:** `fix/5.8.11-sort-fix` → merge в `feature/5.8.11-e4-voice-v2`
**Контекст:** после закрытия серии `e4` на device-check выявились баги
SORT и голосового авто-разделителя. Пять пачек починки.

### `5.8.11-sort-fix` (bab0a01) — SORT flat + авто-split

- `VoiceSession.advanceToNext()` больше не сбрасывает `mode` —
  пользователь в SORT остаётся в SORT после «следующая».
- `VoiceCommandParser`: добавлен `trySplitByNumberBlocks` — попытка
  авто-разделить голосовую фразу по длине групп (≥ 4 цифр).
- `VoiceSpeaker.spellMimicry`: не сыпет буквами на кириллице —
  для чистого «это», «семь» и т.п. возвращает текст как есть.
- `VoiceDialog`: в SORT используется `voiceSortFlat` — плоский
  короткий ответ на все запросы сразу (без очереди, без pin).
- `SearchScreen.buildAnswerLine` → «Наряд №N · NV…» — раньше было
  непонятное «1 · NV1366».
- Убран мусор `.github/app/`.

### `5.8.11-sort-fix-3` (8769657) — фикс SORT, Message.spoken, тесты

- **`trySplitByNumberBlocks` удалён.** Авто-split без «и» невозможен:
  Vosk не сохраняет паузы между числами, все разбиения равновероятны.
  Признано ограничением Vosk.
- `VoiceModels.Message.spoken: String? = null` — TTS читает `spoken ?: text`.
- `VoiceDialog.handleFeedback`: `Message` → `spoken ?: text`.
- `ReconciliationViewModel.voiceNext()`: в SORT отвечает
  «В режиме сортировки не используется.»
- `VoiceCommandParserMultiTest` — 4 теста обновлены под
  отключённый авто-split (`Search` вместо `Sort`).
- `MessageSpokenTest` — 3 новых теста.

### `5.8.11-sort-ui` (405d6f1) — SORT пишет в UI

- `ReconciliationViewModel.voiceSortFlat`: после сборки `descriptions`
  собирает канонические номера в `uiTokens` и вызывает `setQuery(...)`.
  UI строит мультизапрос как при ручном вводе.
- До фикса SORT отвечал только голосом, `state.query` оставался пустым,
  список под лампочкой не отрисовывался.

### `5.8.11-sort-fix-4` (ad26841) — задвоение токенов + Message.display

- **Найдено и починено задвоение в мультизапросе.** Причина:
  `QueryTokenizer.appendTokens` для слитного «prefix+number»
  (`nv1366`) клал в `Prefix.raw` и `Number.raw` **исходный кусок
  целиком**. В `setQuery` строка собиралась через
  `joinToString(" "){it.raw}` → `"nv1366 nv1366"`. Отсюда
  «Запрос №1: nv1366 nv1366» и «Нет ответов».
- `QueryTokenizer`: `raw` у `Prefix` и `Number` — свои части.
- `ReconciliationViewModel.buildQueryString(tokens)` — склейка
  Prefix+Number без пробела, остальные — через пробел.
- `VoiceModels.Message.display: String? = null` — канонический номер
  для UI-поля «Распознано» в SORT.
- `VoiceDialog.displayRecognized`: для `Message` читает `display ?: raw`.
- `MessageSpokenTest` расширен (5 тестов).

### `5.8.11-sort-fix-5` (eb4d1f5) — унификация Next + каноника в Result

- **`VoiceCommand.NextInQueue` удалён.** Синонимы «дальше», «далее»,
  «следующая», «следующий», «следующую» → одна команда `Next`.
  Vosk путает «дальше»/«далее» — унификация убирает класс ошибок.
- `VoiceCommandParser.nextVerbWords` расширен словом «дальше».
- `parseForPinned` — убрана отдельная ветка `NextInQueue`.
- `ReconciliationViewModel.voiceNext()`: `suspend`, проверяет
  `hasQueue` → `voiceNextInQueue()` (переключение), иначе
  `advanceToNext()` (полный сброс).
- `voiceSortFlat`: формирует `text` (каноника, для поля «Результат»)
  и `spoken` (фонетика, для TTS) параллельно. Раньше UI видел
  фонетику: «Скважина эн вэ тринадцать шестьдесят шесть…».
- Обновлены `VoiceCommandParserPin3Test` и `VoiceCommandParserQueueTest`.

### Что осталось после серии

- **И-24** — Vosk обрывает по короткой паузе. Пользователь не успевает
  договорить длинную фразу — Vosk отдаёт промежуточный `onResult`.
  Ждёт отдельного захода.
- **И-35** — Vosk путает «четвёртая» / «четырнадцатая». Ждёт `e4d`.
- **Docs + PR фичи `e4` в `main`** — серия полностью готова, но
  ещё не влита в main.

### Device-check — все 5 сценариев ✅

1. Очередь + «далее» ×2 — переключение → «Очередь пуста».
2. «Следующая» без очереди — полный сброс.
3. Синонимы — «дальше» = «далее» = «следующая».
4. SORT + Result — `NV1366` в обоих полях.
5. SORT + 2 номера — два заголовка, каноника в Result.

---

## 5.8.11-e4 серия — рефакторинг ГП

**Контекст:** аудит голосового пути, сведение UI и ГП в один путь,
закрепление скважины, очередь мультизапроса, честная обработка ошибок,
маркеры намерения, подтверждение массовых, мимикрия везде.

### `5.8.11-e4-fix-voice-1` (закрыт, device ✅) — 4 фикса голоса
- `VoiceDialog`: поле «Распознано» = канонический номер из БД
  (`FoundOne.query`), а не сырой текст Vosk.
- `ReconciliationViewModel.voiceClearOrdinal`: если проба не найдена —
  `MarkOrdinalNotFound` с подсказкой (было просто `Message`).
- `VoiceDialog.buildWeightQueueDonePhrase`: если `skipped == 0` —
  «Все пробы скважины отмечены.» вместо «Очередь веса завершена.».
- `voiceSort` и `voiceNextInQueue`: `attentionReason` для участка/наряда
  (было жёстко `null`).
- SORT-режим теперь корректно предупреждает о другом участке/наряде.

### `5.8.11-e4-ui-1` (закрыт) — кнопка «Наверх»
- `SearchScreen`: `listState`, `derivedStateOf { shouldShowScrollTop(...) }`,
  `SmallFloatingActionButton` внизу справа.
- Порог показа: `firstVisibleItemIndex > 10`.
- Скролл — мгновенный (`scrollToItem(0)`).
- `SearchScrollTopTest` — 5 тестов.

### `5.8.11-e4-weight-queue` (закрыт) — очередь веса
- `VoiceState.AWAITING_WEIGHT_QUEUE`.
- `VoiceModels.WeightQueueKind`, `WeightQueueItem`, `WeightQueueAsked`,
  `WeightQueueDone`.
- `VoiceSession`: `weightQueue`, `currentWeightItem`, `advanceWeightQueue`.
- `ReconciliationWeightQueue.buildWeightQueue(rows)` — чистая функция.
- `ReconciliationViewModel.voiceMarkAll`: отметить всё, что можно,
  потом очередь веса для холостых/ВК без веса.
- `VoiceCommandParser.parseForWeightQueue` + `VoiceCommand.SkipWeightItem`.
- `VoiceDialog`: фразы вопросов и завершения очереди.
- Тесты: `ReconciliationWeightQueueTest` — 11 тестов.

### `5.8.11-e4-prefix-1` (закрыт, device ✅) — префиксы по буквам
- `VoiceSpeaker.spellLetters`: буквы через пробел — «KPD» → «ка пэ дэ».
- `VoiceGrammar`: добавлены звуки букв (`VoiceLetterSounds.sounds.keys`).
- `VoiceLetterSounds`: «дабл-ю» → «даблю» (синхронизация с TTS).
- Тесты: `VoiceSpeakerTest` — префиксы раздельно.
- **Итог:** TTS и Vosk симметричны. Речь и распознавание — один набор.

### `5.8.11-e4-speak-1` (закрыт, device ✅) — разбиение длинных номеров
- `VoiceSpeaker.splitLikeHuman(digits)`: чётная длина — пары, нечётная —
  первая 3, потом пары. `1090031` → `109|00|31`.
- `spellMimicry` переписан: сначала простая ветка «префикс + цифры»,
  потом fallback через `QueryTokenizer + DigitGrouper`.
- Симметрия: что человек сказал, то и услышит от TTS.
- Тесты: `VoiceSpeakerTest` — 19 тестов.

### `5.8.11-e4-markers-2` (закрыт, device ✅) — отложение одной фразой
- `VoiceCommand.PostponeOrdinal(ordinal)`.
- `VoiceCommandParser`: глаголы отложения + номер одной фразой
  («отложить вторую», «отложи 7»).
- `VoiceDialog.substitutedPhrase`: TTS говорит номер пробы через
  `spellMimicry` (было «сто тридцать шесть тысяч шестьсот два»).
- `ReconciliationViewModel`: fallback-подсказка в `voiceClearOrdinal`
  (баг, закрыт в `e4-fix-voice-1`).
- Тесты: `VoiceMarkersTest` — расширены.

### `5.8.11-e4-markers` (закрыт, device ✅) — маркеры намерения
- `VoiceState.AWAITING_MARK`, `AWAITING_CLEAR`, `AWAITING_POSTPONE`,
  `AWAITING_CONFIRM`.
- `VoiceCommand.MarkIntent`, `ClearIntent`, `PostponeIntent`, `Confirm`,
  `Decline`.
- `VoiceSession.pendingMarkIntent`, `pendingConfirm`.
- Тайм-аут 30 сек с предупреждением на 20-й (в `checkWaitTimeout`).
- Подтверждение массовых: «подтверждаю» / «отменяю».
- `VoiceDialog`: `LaunchedEffect` с тиком 250 мс для тайм-аута.
- Тесты: `VoiceMarkersTest` — 17 тестов.

### `5.8.11-e4-pin-7` (закрыт, device ✅) — честная ошибка вместо fallback
- Отказ от fallback: Vosk путает «четвёртая» ↔ «четырнадцатая» в обе
  стороны. Молчаливая подмена опасна.
- `VoiceMarkOrdinalFallback`: `resolve` и `isSubstituted` удалены.
  Осталась `candidatesFor` / `hintFor`.
- `VoiceExecResult.MarkOrdinalNotFound(ordinal, hintOrdinal)`.
- UI: «Пробы №14 нет. Если нужна №4 — произнесите „четыре".»
- Accent fix: «Распознано» убрано из голоса.
- **И-35** — зафиксировано как ограничение Vosk. Лечится в `e4d`.

### `5.8.11-e4-pin-6` (закрыт, откачен в pin-7) — показ подмены
- `Marked.recognizedOrdinal`, `isSubstituted()`.
- Признано ошибочным: показ подмены не решает главную проблему —
  молчаливое неверное действие. Откачено.

### `5.8.11-e4-pin-5` (закрыт) — расширение fallback
- `VoiceMarkOrdinalFallback` — новый файл. 14↔4, 40↔4, 400↔4, 4000↔4.

### `5.8.11-e4-pin-4` (закрыт) — числительные в pin + fallback 14→4
- `VoiceCommandParser.parseForPinned`: фраза из числительных
  склеивается в одно число.
- `voiceMarkOrdinal`: fallback 14↔4.

### `5.8.11-e4-pin-3` (закрыт) — вес «X сотни», «следующая X»
- `VoiceCommandParser.parseWeightAnswer`: «два семьсот» → 2,7.
- «следующая X» → `Find(X)`.
- `VoiceModels.FoundOne.queueSize`: префикс «Найдено N скважин».

### `5.8.11-e4-pin-2` (закрыт) — очередь мультизапроса
- `VoiceCommand.NextInQueue` (удалён в `sort-fix-5`).
- `VoiceSession.queue`, `enqueue`, `nextInQueue`, `clearQueue`, `hasQueue`.

### `5.8.11-e4-pin-1` (закрыт) — закрепление скважины
- `VoiceState.FOUND_PINNED`, `PinnedScope`.
- `VoiceSession.pin/unpin/isPinned`.
- `VoiceCommandParser.parseForPinned`: голое число → `MarkOrdinal`.

### `5.8.11-e4e-bundle` (закрыт) — мимикрия + единый путь
- `VoiceSpeaker.spellOut(groups)`: без запятых, префикс одним словом.
- `ReconciliationViewModel.voiceSearch`: через `SearchService`.

### `5.8.11-e4-tests` (закрыт) — покрытие парсера
- `VoiceCommandParserWeightsTest` — 17 тестов.
- `VoiceCommandParserFindTest` — 8 тестов.
- `VoiceCommandParserPausedTest` — 6 тестов.

### Архитектурные решения серии

- **Pin скважины** — `FOUND_PINNED`, `PinnedScope`. Реализовано.
- **Очередь мультизапроса** — реализовано.
- **Мимикрия TTS везде** — `spellMimicry`. Реализовано.
- **Честная ошибка вместо fallback** — реализовано (pin-7).
- **Маркеры намерения** — реализовано (`e4-markers`).
- **Подтверждение массовых** — реализовано (`e4-markers`).
- **Очередь веса** — реализовано (`e4-weight-queue`).
- **Префиксы по буквам** — реализовано (`e4-prefix-1`).
- **Динамические словари Vosk** — `e4-dicts` (следующая серия).

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