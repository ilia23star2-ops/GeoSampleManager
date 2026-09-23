# PROGRESS.md — история заходов

## 5.8.6 серия — Vosk-полировка и защита от ложных срабатываний

### `5.8.6-5g` (закрыт unit, 🟡 device) — «четвертых» → Unknown
- `VoiceOrdinals.kt`: добавлен `pluralForms` — формы порядковых во мн.ч. («четвертых», «пятых», «десятых»).
- `VoiceCommandParser.kt`: если во фразе есть такая форма — возвращается `Unknown`, до сортировки и поиска.
- Итог: «пять четвертых» → Unknown, не Search("5"). «четвертая» (одиночное) — по-прежнему `MarkOrdinal(4)`.

### `5.8.6-5f` (закрыт unit, 🟡 device) — Undo UI
- `SearchScreen.kt`: блок `items` переведён на `remember { derivedStateOf { ... } }` без ключей.
- Внутри `derivedStateOf` явные чтения `state.groups.toList()` и `state.queryGroups.toList()` — гарантия подписки.
- Причина: при пустом `query` функция `visibleGroups` возвращала `emptyList()`, не читая `_groups`, и `derivedStateOf` не пересчитывался. Отмена не перерисовывала экран.

### `5.8.6-5c` (закрыт unit, 🟡 device) — повтор команд + звук снятия/отмены
- `VoiceController.kt`: вечный игнор одинаковых фраз → time-based debounce 600 мс.
- `VoiceDialog.kt`: звук и озвучка для `Unmarked`, `Undone`, `Redone`.
- fix-1/fix-2: «назад/верни» → Undo, «вперёд/вперед» → Redo.
- fix-3: тест `undoAndRedo` обновлён — «повтори» теперь `Unknown`.

### `5.8.6-5a` (закрыт unit, 🟡 device) — строгий голосовой шлюз
- «семья» → «семь» больше не отмечает пробу.
- Отметка только по явным конструкциям: «первая», «отметь 7», «отметить седьмую», «отметь эту».
- `voiceMarkAll()` через `analyzeMark`: блокирует, если есть холостые/ВК без веса.
- Сортировка с разделителями: «и», «запятая», «тире», `;`, `,`.
- «помощь» озвучивает список команд, не врёт про открытие справки.
- fix-1: составные порядковые «двадцать первая», «тридцать первая».
- fix-2: `num in 30` → `num in 1..30`.

### `5.8.6-4` (закрыт unit, 🟡 device) — грамматика Vosk под текущие команды
- Добавлены: `снять/отложить/пропустить`, русский вес (`целых`, `десятых`, `сотых`, `половиной`, `четвертью`, `полтора`, `полкило`), служебные команды.

### `5.8.6-3` (закрыт unit, 🟡 device) — защита команд от поиска
- `VoiceCommandParser`: параметр `pendingChoice` возвращён; `ChoiceRemove/Postpone/Skip` распознаются только в состоянии выбора; служебные слова блокируют поиск.
- `ReconciliationViewModel`: `handleSearchInSession` больше не делает авто-отметку по числу; `isLikelyVoiceSearchQuery` для фильтра.
- fix-1: поддержка `ChoiceRemove/Postpone/Skip` в `when (cmd)` + `pendingChoice` в `VoiceDialog`.

### `5.8.6-2` (закрыт unit, 🟡 device) — ноль + anti-echo
- `VoiceNumberParser`: одиночное «ноль» → цифра 0, счётчик только на «два ноля / три нуля».
- `VoiceController`: скорость TTS 1.10, `RESUME_DELAY_MS = 800`, окно подавления эха по длине фразы.

### `5.8.6-2a` (закрыт unit, 🟡 device) — разбор «15 24 01»
- «четыре ноль один» больше не превращается в «0000 1».

## 5.8.9 серия — голосовой ввод

### `5.8.9i-1/2/3` (закрыты unit, 🟡 device) — русский TTS
- `VoiceSpeaker`: склонения, `spokenWeight()`, `samples()`, `wells()`, `errors()`.
- `VoiceController`: скорость 1.10, глушение Vosk во время TTS.
- `VoiceDialog`: применение нормализатора в фразах.

### `5.8.9d-3c2b1/3c2b2` (закрыты unit, 🟡 device) — pending mark choice
- `VoiceCommand`: `ChoiceRemove`, `ChoicePostpone`, `ChoiceSkip`.
- `VoiceSession`: `pendingMarkChoice`, `startPendingMarkChoice()`, `clearPendingMarkChoice()`.
- `VoiceCommandParser`: параметр `pendingChoice`.
- `ReconciliationViewModel`: обработка выбора, `voiceChoiceMarkCurrent()`.
- `VoiceDialog`: передача `pendingChoice` в парсер.

### `5.8.9f-1a-fix-1` — `SetMode` (SORT/SEARCH)
### `5.8.9d-2a` — `MarkCurrent` (отметить найденную пробу)
### `5.8.9g-1/3` — `MarkByNumbers`, `MarkAll`, явные слова в грамматике

## Ранее (выборочно)

- `5.8.9h-2` — `UnifiedSearch` в UI и ГП.
- `5.8.9-infra-2d` — CI вручную.
- Базовый голосовой ввод, Vosk-модель, Excel-импорт, фото, заметки.
