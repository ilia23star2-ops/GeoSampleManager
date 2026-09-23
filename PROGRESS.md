# PROGRESS.md — история заходов

## 5.8.10 серия — настройки UI, онбординг, импорт, голос

### `5.8.10-f` (закрыт unit + device) — приоритет имени листа в AUTO
- `OrderNumberExtractor.kt`: в режиме `AUTO` теперь сначала пробуем
  **осмысленное имя листа** (не «Лист1»/«Sheet1»/«TDSheet»), потом имя файла.
- Причина: файл «опись проб 13.xlsx» с листами «НЗ №97..102» давал
  один и тот же наряд «13» для всех листов → импортировался только первый,
  остальные падали в «наряд уже есть в базе».
- Итог: 6 листов → 6 нарядов (97..102). Проверено на устройстве — ОК.

### `5.8.10-e` (закрыт unit) — коллизия нарядов по orderTitle
- `ReconciliationViewModel.kt`: добавлен композитный индекс
  `orderInfoByComposite` с ключом «areaTitle|orderTitle».
- `setSelectedOrder` теперь ищет по композитному ключу, если участок выбран.
- При отсутствии area у наряда — **не пропускаем молча**, а кладём
  с `areaTitle = "—"` и пишем в лог. Раньше наряд «терялся».
- Причина: `orderInfoByTitle = associateBy { it.orderTitle }` схлопывал
  наряды с одинаковым номером в разных участках.

### `5.8.10-d` (закрыт unit) — проверка импорта по имени участка
- `OrderDao.kt`: новый метод `getOrderIdsByName(areaName, orderNumber)` —
  JOIN `orders` + `areas` по `area_name`, устойчив к дубликатам.
- `DatabaseRepository.getExistingOrderStats` переписан на этот метод,
  статистика суммируется по всем совпадениям.
- Причина: `areaDao.getAreaId(name)` с `LIMIT 1` возвращал первый
  дубликат участка, наряд в другом дубликате не находился.

### `5.8.10-c` (закрыт unit) — озвучка списка проб при множественной отметке
- `VoiceDialog.kt`: `MarkedMultiple` теперь озвучивает конкретный список
  («Отмечено: первая, вторая, третья»), а не только N.
- Добавлены функции `resolveOrdinals()` и `buildMarkedMultiplePhrase()`.
- Fallback: если порядковые не нашлись — старое «Отмечено N проб».

### `5.8.10-b` (закрыт unit, 🟡 device) — И-9: онбординг не показывается
- `VoiceSettings.kt`: в `VoiceSettingsRepository.load()` добавлена защита
  `if (!json.contains("\"showOnboarding\"")) parsed = parsed.copy(showOnboarding = true)`.
- Причина: Gson игнорирует Kotlin-дефолты. Если в существующем
  `voice_settings.json` поля не было — Gson ставил `false`.

### `5.8.10-a` (закрыт unit, 🟡 device) — И-10: showCharacteristic переживает перезапуск
- `VoiceSettings.kt`: добавлено поле `showCharacteristic: Boolean = true`;
  в `load()` — защита дефолта.
- `ReconciliationViewModel.kt`: `checkOnboarding()` → `loadVoiceUiSettings()`
  (читает `showOnboarding` + `showCharacteristic`); метод `setShowCharacteristic()`.
- `SearchScreen.kt`: `onShowCharacteristicChange` → через ViewModel.

## 5.8.6 серия — Vosk-полировка и защита от ложных срабатываний

### `5.8.6-5g` (закрыт unit, 🟡 device) — «четвертых» → Unknown
- `VoiceOrdinals.kt`: добавлен `pluralForms` — формы порядковых во мн.ч.
- `VoiceCommandParser.kt`: если во фразе есть такая форма — `Unknown`.
- Итог: «пять четвертых» → Unknown, не Search("5").

### `5.8.6-5f` (закрыт unit, 🟡 device) — Undo UI
- `SearchScreen.kt`: блок `items` переведён на `remember { derivedStateOf { ... } }`.
- Внутри — явные чтения `state.groups.toList()` и `state.queryGroups.toList()`.
- Причина: при пустом `query` функция `visibleGroups` возвращала `emptyList()`
  без обращения к `_groups`, и `derivedStateOf` не пересчитывался.

### `5.8.6-5c` (закрыт unit, 🟡 device) — повтор команд + звук снятия/отмены
- `VoiceController.kt`: time-based debounce 600 мс вместо вечного игнора.
- `VoiceDialog.kt`: звук и озвучка для `Unmarked`, `Undone`, `Redone`.
- fix-1/fix-2: «назад/верни» → Undo, «вперёд/вперед» → Redo.
- fix-3: тест `undoAndRedo` обновлён — «повтори» теперь `Unknown`.

### `5.8.6-5a` (закрыт unit, 🟡 device) — строгий голосовой шлюз
- «семья» → «семь» больше не отмечает пробу.
- Отметка только по явным конструкциям.
- `voiceMarkAll()` через `analyzeMark`: блокирует, если есть холостые/ВК без веса.
- fix-1: составные порядковые «двадцать первая», «тридцать первая».
- fix-2: `num in 30` → `num in 1..30`.

### `5.8.6-4` (закрыт unit, 🟡 device) — грамматика Vosk под текущие команды
- Добавлены: `снять/отложить/пропустить`, русский вес, служебные команды.

### `5.8.6-3` (закрыт unit, 🟡 device) — защита команд от поиска
- `VoiceCommandParser`: параметр `pendingChoice` возвращён.
- `ReconciliationViewModel`: `handleSearchInSession` без авто-отметки.
- fix-1: поддержка `ChoiceRemove/Postpone/Skip` в `when (cmd)`.

### `5.8.6-2` (закрыт unit, 🟡 device) — ноль + anti-echo
- `VoiceNumberParser`: одиночное «ноль» → цифра 0.
- `VoiceController`: TTS 1.10, `RESUME_DELAY_MS = 800`, окно подавления эха.

### `5.8.6-2a` (закрыт unit, 🟡 device) — разбор «15 24 01»
- «четыре ноль один» больше не превращается в «0000 1».

## 5.8.9 серия — голосовой ввод

### `5.8.9f-2b` (закрыт, реализовано ранее) — чип режима у микрофона
- `SearchScreen.kt`: `VoiceModeChip` — ПОИСК/СОРТ, тап переключает
  `VoiceSession.mode`. Отдельного захода не потребовалось.

### `5.8.9i-1/2/3` (закрыты unit, 🟡 device) — русский TTS
- `VoiceSpeaker`: склонения, `spokenWeight()`, `samples()`, `wells()`, `errors()`.
- `VoiceController`: скорость 1.10, глушение Vosk во время TTS.

### `5.8.9d-3c2b1/3c2b2` (закрыты unit, 🟡 device) — pending mark choice
- `VoiceCommand`: `ChoiceRemove`, `ChoicePostpone`, `ChoiceSkip`.
- `VoiceSession`: `pendingMarkChoice`, `startPendingMarkChoice()`, `clearPendingMarkChoice()`.
- `ReconciliationViewModel`: обработка выбора, `voiceChoiceMarkCurrent()`.

### `5.8.9f-1a-fix-1` — `SetMode` (SORT/SEARCH)
### `5.8.9d-2a` — `MarkCurrent` (отметить найденную пробу)
### `5.8.9g-1/3` — `MarkByNumbers`, `MarkAll`, явные слова в грамматике

## Ранее (выборочно)

- `5.8.9h-2` — `UnifiedSearch` в UI и ГП.
- `5.8.9-infra-2d` — CI вручную.
- Базовый голосовой ввод, Vosk-модель, Excel-импорт, фото, заметки.
