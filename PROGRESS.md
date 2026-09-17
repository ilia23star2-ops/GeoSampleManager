# GeoSample Manager — статус проекта

> Обновляется после каждого захода.
> Последнее обновление: **после закрытия 5.8.9-infra-2d**.

---

## Как продолжить работу в новом чате

1. Прикрепи файлы:
    - `AI_RULES.md`
    - `CONTEXT_BRIEF.md` (в первую очередь!)
    - `README.md`
    - `PROGRESS.md` (этот)
    - `NEXT_STEPS.md`
    - `DECISIONS.md`
    - `DATABASE.md`
    - `ROADMAP.md`
    - `VOICE.md`
    - `GLOSSARY.md`
    - `ISSUES.md`
    - `TESTING.md`
2. Скажи: «Изучи файлы, продолжаем с этапа N, заход N.M».

---

## Статус одной строкой

**Этапы 1–4, 5.1, 5.5, 5.8.1–5.8.4, 5.8.8 (d–k), 5.8.9 (a–g-3),
5.8.9e-2 (+fix-1/2/3), 5.8.9e-3, 5.8.9-infra-2a (+fix-1/2/3),
5.8.9-infra-2b (+fix-1/2), 5.8.9-infra-2d, 5.11 — ✅.
5.8.9h-1 (UnifiedSearch) — в ветке, ждёт merge.
Следующий заход — 5.8.9h-2 (переключение UI и ГП на UnifiedSearch).**

---

## Что сделано

### ✅ Этапы 1–4 — База проекта
Скелет, Drawer, 7 вкладок, навигация, Room-БД (6 таблиц, version=2),
настройки импорта, Excel-импорт.

### ✅ Этап 5.1 — Каркас сверки
Полный UI-экран, модели, состояние, undo/redo, 11 диалогов.

### ✅ Этап 5.5 — Заметки и фото
`PhotoStorage`, `SampleImageEntity`, миграция 1→2, `NotePhotoDialog`,
флаги `has_note` / `has_photo`.

### ✅ Этап 5.8.1 — Каркас голоса
Разрешения, `VoiceController`, `VoiceDialog`, кнопка микрофона.

### ✅ Спецификация ГП (`VOICE.md`)
Полная спецификация.

### ✅ Заход 5.11 — Множественный поиск (UI)
Группировка, подсветка, дебаунс, режимы поиска, все баги закрыты.

### ✅ Заходы 5.8.2–5.8.4 — Парсер, префиксы, команды
`VoiceDictionary`, `VoiceNumberParser`, `VoiceSegmenter`,
`VoicePrefixResolver`, `VoiceSearch`, `VoiceOrdinals`,
`VoiceCommand`, `VoiceCommandParser`. ~38 юнит-тестов зелёные.

### ✅ Заход 5.8.8 — Vosk + грамматика + TTS

Vosk-small-ru, грамматика, авто-режим отметок, TTS по парам цифр,
пауза Vosk во время речи, разрешения runtime.

### ✅ Заходы 5.8.8d–k — Финальные фиксы ГП

- `spellNumber`, `awaitingWeight`, вес для холостых.
- `VoiceCommandParser`: явная проверка `Sort` по «и».
- `VoiceNumberParser`: `distinct()` — без дубликатов.
- `VoiceSearch`: `FoundMany` при разных нарядах; суффикс ≥ 3 символов; порог 50.
- UI: неоднозначный ответ (жёлтый ⚠, баннер, подписи групп).
- Автопауза `awaitingContinue`; перечисление по «и»; TTS по парам.
- `analyzeMatch`: 1 группа → всегда `Unique`.

### ✅ Заход 5.8.9-infra — Правила работы с двух машин (базовая версия)

`AI_RULES.md` §20 и `README.md` — работа через `github.dev` без IDE.

### ✅ Заход 5.8.9a — Модель `AnswerState` + `AnswerReason`

`data/voice/AnswerState.kt` — 4 состояния, 8 причин, единый маппер.

### ✅ Заход 5.8.9a-t — Тесты модели

`AnswerStateTest` (21), `AnalyzeMatchTest` (15).

### ✅ Заход 5.8.9b — UI: индикатор ответа, заголовки, цвета

`AnswerStateColors.kt`. Индикатор — 3 строки. Заголовки — трёхстрочная
структура. Строгий цвет.

### ✅ Заход 5.8.9c — Звуковая карта

`VoiceFeedback.kt`: `soundOk`, `soundAttention`, `soundError`.

### ✅ Заход 5.8.9d-1 — TTS окна ответа

`FoundOne` расширен полями `blanks` / `weightControls` / `postponed`.

### ✅ Заход 5.8.9bug-1 — `VoiceSearch`: суффикс ≥ 3, порог 50

### ✅ Заход 5.8.9bug-2 — Селекторы не перезаписываются

### ✅ Заход 5.8.9bug-3 + fix-1…fix-5 — ВК в настройках наряда

- Полная пересборка ВК при сохранении.
- `SampleRow.serialNumber` — п/п из БД.
- Кнопка «Сбросить ВК» + undo.
- **Раздельные «Применить»**: холостые и ВК.

### ✅ Заход 5.8.9e-1 — Свёрнутые группы + участок в заголовке

### ✅ Заход 5.8.9e-2 (+fix-1/2/3) — Индикатор мультипоиска

- Строки по каждому запросу в одиночном индикаторе.
- Значки 🟢/🟡/🔴 на каждый запрос (до 5).
- `MatchedKind` (WELL / SAMPLE / NONE) — показываем то, что искали.
- Одиночный — «Наряд 7 · скв. NV1526». Мульти — «7 · 1524».
- `CancellationException` — больше нет ложных Snackbar при быстром вводе.

### ✅ Заход 5.8.9e-3 — `keepScreenOn`

Экран не гаснет, пока сессия ГП активна. `view.keepScreenOn` через
`DisposableEffect` по `voiceState`.

### ✅ Заход 5.8.9e-4 — ГП: другой участок / другой наряд

`FoundOne.attentionReason`. `soundAttention` + автопауза.

### ✅ Заход 5.8.9e-5 — `VoiceOrdinals` + spell-out

Формы «третья/третий/третье/третью» добавлены явно. `spellOut` в
`Message`.

### ✅ Заход 5.8.9g-1 — Парсер: `MarkByNumbers` + `MarkAll`

### ✅ Заход 5.8.9g-2 — Выполнение множественной отметки

`MarkedMultiple`, `MarkedAll`.

### ✅ Заход 5.8.9g-3 — Грамматика + сброс сессии

### ✅ Заход 5.8.9-infra-2a + fix-1/2/3 — CI (GitHub Actions)

- `.github/workflows/build.yml` — тесты + APK.
- `Setup Android SDK@v4` + `packages: platform-tools`.
- `testOptions.unitTests.returnDefaultValues = true` — фикс `Log not mocked`.
- Обновлены устаревшие `VoiceSearchTest` и `VoiceCommandParserTest`.

### ✅ Заход 5.8.9-infra-2b + fix-1/2 — Правила (три режима)

- `AI_RULES.md` §20 — три режима: дома (AS), `github.dev`, `github.com`.
- §21 — сводка коммитов.
- §22 — переход «работа → дом».
- Компактная шапка захода (§2.2): заход, режим, ветка, коммит.
- Коммит: общий для AS / github.dev, по файлам для github.com.
- §0 — ИИ задаёт вопрос про режим первым делом.

### ✅ Заход 5.8.9-infra-2d — CI вручную

- Убран автозапуск по push / PR.
- `workflow_dispatch` с двумя чек-боксами: `run_tests` (✅ по умолчанию),
  `build_apk` (⬜ по умолчанию).

### 🟡 Заход 5.8.9h-1 — `UnifiedSearch` (в ветке, ждёт merge)

- `data/voice/UnifiedSearch.kt` — единый алгоритм поиска.
- `UnifiedSearchTest` — 18 тестов.
- Ветка: `feature/5.8.9h-1-unified-search`.
- **Не смержено** — переключение UI и ГП на `UnifiedSearch` будет в
  заходе `5.8.9h-2`.

---

## Что в работе

**Пусто.** Все заходы до `5.8.9h-1` (включительно) закрыты или в ветке.

Ветка `feature/5.8.9h-1-unified-search` — ждёт merge, если CI зелёный.

---

## Что впереди

| # | Заход | Заходов | Статус |
|---|---|---|---|
| 5.8.9h-1 | Merge `UnifiedSearch` | — | 🟡 в ветке |
| 5.8.9h-2 | Переключение UI и ГП на `UnifiedSearch` | 2 | ⬜ |
| 5.8.9-infra-2e | Vosk в CI (выкачка модели из релиза) | 1 | ⬜ |
| 5.8.9f | Режимы SORT / SEARCH | 2 | ⬜ |
| 5.8.9d-2 | Отметка короче | 1 | ⬜ |
| 5.8.9d-3 | Переспросы ВК / отложенных | 1 | ⬜ |
| 5.8.9i | Мимикрия произношения | 1 | ⬜ |
| 5.8.6 | Vosk-полировка | 1 | ⬜ |
| 5.9 | Room — доводка | 1 | ⬜ |
| 5.10 | Общая полировка + туториалы | 3–4 | ⬜ |
| 6 | Релиз MVP | 2 | ⬜ |

**Итого: ~13 заходов.**

---

## Известные проблемы

См. `ISSUES.md`.

---

## Полезные ссылки

- `AI_RULES.md` — правила работы ИИ.
- `CONTEXT_BRIEF.md` — где мы сейчас.
- `README.md` — структура.
- `DATABASE.md` — схема БД.
- `DECISIONS.md` — решения.
- `NEXT_STEPS.md` — план заходов.
- `ROADMAP.md` — общий план.
- `VOICE.md` — спецификация ГП.
- `GLOSSARY.md` — термины.
- `ISSUES.md` — проблемы.
- `TESTING.md` — тесты.