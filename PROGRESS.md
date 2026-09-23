# PROGRESS.md — история заходов

## 5.8.11 серия — унификация поиска и ответа (SEARCH_MODEL)

Спецификация — `SEARCH_MODEL.md`. Серия строит закладки под единый путь:
ручной и голосовой ввод идут одним путём через `QueryNormalizer` →
`SearchService` → `Response` → `Presenter`.

### `5.8.11-d2` (закрыт unit) — Response + Presenter
- `Response.kt` — единая модель ответа: `kind`, `primary`, `details`,
  `reason`, `sound`, `pause`, `groups`, `context`.
- `ResponseMapper` — маппинг `SearchResult → Response`.
- `Presenter.kt` — единая точка рендера: `Response → VisualRender + spoken`.
- Молчание при `UNKNOWN` в `LISTENING` (`SEARCH_MODEL §8.5`).

### `5.8.11-d1` (закрыт unit) — SearchResult + SearchService
- `SearchResult.kt` — единый результат поиска, хранит и попадания,
  и структуру запроса (`groups`, `queryTokens`).
- `SearchService.kt` — in-memory обёртка над `UnifiedSearch` (SQL-путь
  не используется, `SEARCH_MODEL §6.1`).

### `5.8.11-c2` (закрыт unit) — групповые кандидаты + озвучка
- `GroupToCandidates.kt` — построение кандидатов из `DigitGroup`:
  слитно, через `|`, по парам справа.
- `VoiceSpeaker.spellOut(groups: List<DigitGroup>)` — озвучка по группам
  ввода («109 00 31» → «сто девять, ноль ноль, тридцать один»).
- Старый `spellOut(String)` сохранён — обратная совместимость.

### `5.8.11-c1` (закрыт unit) — DigitGroup + DigitGrouper
- `DigitGroup.kt` — группа цифр: `PLAIN` / `LEADING_ZERO` / `PREFIX` / `SINGLE`.
- `DigitGrouper.kt` — группировка токенов: единицы сливаются, нули
  выделяются в `LEADING_ZERO`, префикс — отдельная группа.

### `5.8.11-b` (закрыт unit) — единый ввод
- `QueryToken.kt` — типы токенов: `Prefix`, `Number`, `Ordinal`,
  `CommandWord`, `Separator`, `Unknown`.
- `QueryNormalizer.kt` — нормализация строки (lowercase, ё→е, дефисы,
  пунктуация, сжатие пробелов).
- `QueryTokenizer.kt` — разбиение строки на типизированные токены.
  Слитные префиксы разделяются: `KPD1090031` → `Prefix + Number`.

### `5.8.11-a` (закрыт unit) — состояния ГП
- `VoiceState.kt` — enum из 6 состояний: `IDLE`, `LISTENING`,
  `AWAITING_WEIGHT`, `AWAITING_CONTINUE`, `AWAITING_CHOICE`, `PAUSED`.
- `VoiceSession.state` — вычисляемое свойство из существующих флагов.
- `VoiceSessionMode` (SEARCH/SORT) остаётся **ортогональным** состоянию
  (`SEARCH_MODEL §3`).

### План интеграции — `5.8.11-e` (дома, с device-check)

Разбит на три подзахода — каждый 1 файл + проверка на устройстве:

- **`5.8.11-e1`** — `ReconciliationViewModel.setQuery` → новый путь
  (`QueryNormalizer` + `QueryTokenizer` + `SearchService`).
- **`5.8.11-e2`** — `VoiceCommandParser.parse(text, state, mode)` —
  принимает состояние и режим (`SEARCH_MODEL §3`).
- **`5.8.11-e3`** — `VoiceDialog` через `Response` + `Presenter`.

## 5.8.10 серия — настройки UI, онбординг, импорт, голос

### `5.8.10-g1/g2` (закрыт, ✅ device) — И-3: панель ГП
- `VoicePanel.kt` — немодальная панель внизу экрана.
- `VoiceDialog.kt` — `AlertDialog` → `VoicePanel`.
- `SearchScreen.kt` — интеграция: `VoiceDialog` внутрь `Box`, удалён
  `VoiceStatusBar`.

### `5.8.10-f` (закрыт, ✅ device) — приоритет имени листа
- `OrderNumberExtractor.kt`: в `AUTO` сначала пробуем осмысленное имя
  листа, потом имя файла. «Опись проб 13.xlsx» + «НЗ №97..102» → 6 нарядов.

### `5.8.10-e` (закрыт unit) — коллизия по orderTitle
- `ReconciliationViewModel.kt`: композитный индекс «area|order».

### `5.8.10-d` (закрыт unit) — проверка импорта по имени участка
- `OrderDao.getOrderIdsByName`, `DatabaseRepository.getExistingOrderStats`.

### `5.8.10-c` (закрыт unit) — озвучка списка проб
- `VoiceDialog.kt`: `MarkedMultiple` перечисляет порядковые.

### `5.8.10-b` (закрыт unit) — И-9: онбординг
- `VoiceSettings.kt`: защита `showOnboarding = true`, если поля нет в JSON.

### `5.8.10-a` (закрыт unit) — И-10: showCharacteristic
- `VoiceSettings.kt`, `ReconciliationViewModel.kt`, `SearchScreen.kt`.

## 5.8.6 серия — Vosk-полировка

- `5.8.6-5g` — «четвертых» → Unknown (`VoiceOrdinals.pluralForms`).
- `5.8.6-5f` — Undo UI (`derivedStateOf` с явными State-чтениями).
- `5.8.6-5c` — debounce 600 мс + звуки снятия/отмены.
- `5.8.6-5a` — строгий голосовой шлюз.
- `5.8.6-4` — грамматика Vosk.
- `5.8.6-3` — защита команд от поиска.
- `5.8.6-2` — ноль + anti-echo.
- `5.8.6-2a` — разбор «15 24 01».

## 5.8.9 серия — голосовой ввод

- `5.8.9f-2b` — чип режима у микрофона (реализован ранее).
- `5.8.9i-1/2/3` — русский TTS.
- `5.8.9d-3c2b1/3c2b2` — pending mark choice.
- `5.8.9f-1a-fix-1` — `SetMode`.
- `5.8.9d-2a` — `MarkCurrent`.
- `5.8.9g-1/3` — `MarkByNumbers`, `MarkAll`.

## Ранее (выборочно)

- `5.8.9h-2` — `UnifiedSearch` в UI и ГП.
- `5.8.9-infra-2d` — CI вручную.
- Базовый голосовой ввод, Vosk-модель, Excel-импорт, фото, заметки.
