# GeoSample Manager — статус проекта

> Обновляется после каждого захода.
> Последнее обновление: **после закрытия 5.8.9-infra-2b-fix-3**.

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
5.8.9e-2 (+fix-1/2/3), 5.8.9e-3, 5.8.9h-2 (a, b-i, b-i-fix-1/2,
b-ii, b-ii-fix-1), 5.8.9-infra-2a (+fix-1/2/3), 5.8.9-infra-2b
(+fix-1/2/3), 5.8.9-infra-2d, 5.11 — ✅.
Следующий заход — 5.8.9f (режимы SORT / SEARCH).**

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

### ✅ Заход 5.11 — Множественный поиск (UI)

### ✅ Заходы 5.8.2–5.8.4 — Парсер, префиксы, команды

### ✅ Заход 5.8.8 — Vosk + грамматика + TTS

### ✅ Заходы 5.8.8d–k — Финальные фиксы ГП

### ✅ Заход 5.8.9-infra — Правила работы с двух машин (базовая версия)

### ✅ Заход 5.8.9a — Модель `AnswerState` + `AnswerReason`

### ✅ Заход 5.8.9a-t — Тесты модели

### ✅ Заход 5.8.9b — UI: индикатор ответа, заголовки, цвета

### ✅ Заход 5.8.9c — Звуковая карта

### ✅ Заход 5.8.9d-1 — TTS окна ответа

### ✅ Заход 5.8.9bug-1 — `VoiceSearch`: суффикс ≥ 3, порог 50

### ✅ Заход 5.8.9bug-2 — Селекторы не перезаписываются

### ✅ Заход 5.8.9bug-3 + fix-1…fix-5 — ВК в настройках наряда

### ✅ Заход 5.8.9e-1 — Свёрнутые группы + участок в заголовке

### ✅ Заход 5.8.9e-2 (+fix-1/2/3) — Индикатор мультипоиска

### ✅ Заход 5.8.9e-3 — `keepScreenOn`

### ✅ Заход 5.8.9e-4 — ГП: другой участок / другой наряд

### ✅ Заход 5.8.9e-5 — `VoiceOrdinals` + spell-out

### ✅ Заход 5.8.9g-1 — Парсер: `MarkByNumbers` + `MarkAll`

### ✅ Заход 5.8.9g-2 — Выполнение множественной отметки

### ✅ Заход 5.8.9g-3 — Грамматика + сброс сессии

### ✅ Заход 5.8.9-infra-2a + fix-1/2/3 — CI (GitHub Actions)

- `.github/workflows/build.yml` — Setup Android SDK, тесты, APK.
- `testOptions.unitTests.returnDefaultValues = true`.
- Обновлены устаревшие `VoiceSearchTest` и `VoiceCommandParserTest`.

### ✅ Заход 5.8.9-infra-2b + fix-1/2/3 — Правила

- §20 — три режима: дома (AS), `github.dev`, `github.com`.
- Дома — работаем прямо в `main`, ветки не создаём.
- Компактная шапка захода: заход, режим, ветка, коммит.
- Git-инструкции в AS — пошаговые. Замены файлов — без пояснений.
- Коммит: общий для AS / github.dev, по файлам для github.com.

### ✅ Заход 5.8.9-infra-2d — CI вручную

- `workflow_dispatch` с чек-боксами `run_tests` и `build_apk`.
- Автозапуск по push / PR отключён.

### ✅ Заход 5.8.9-infra-2e + fix-1 — Vosk в CI

- Скачивание модели из релиза `models-v1` при `build_apk=true`.
- `mkdir -p app/src/main/assets` перед распаковкой.

### ✅ Заход 5.8.9h-1 — `UnifiedSearch` + 18 тестов

Единый алгоритм поиска для UI и ГП.

### ✅ Заход 5.8.9h-2a — ГП на `UnifiedSearch`

`ReconciliationViewModel.voiceSearch` и `voiceSort` переведены на
`UnifiedSearch`. `CancellationException` пробрасывается.

### ✅ Заход 5.8.9h-2b-i + fix-1/2 — UI на `UnifiedMatchKind`

- `ReconciliationModels.kt`: `typealias MatchedKind = UnifiedMatchKind`.
- `analyzeMatch` через `UnifiedSearch`, `filterByQuery` с `selectedArea` /
  `selectedOrder`.
- Правило filterMode: **точное по всей базе, префикс — только в
  выбранном наряде**.
- Из `UnifiedSearch` убран порог `MAX_AMBIGUOUS`.
- `SearchScreen.kt` — импорт `UnifiedMatchKind`.

### ✅ Заход 5.8.9h-2b-ii + fix-1 — Удаление `VoiceSearch`

- Класс `VoiceSearch` и `VoiceSearchResult` удалены.
- `VoiceSampleHit` / `VoiceSampleSource` переехали в `VoiceSampleHit.kt`.
- `VoiceSearchTest.kt` удалён.
- `VoiceModels.kt` восстановлен (`VoiceStatus`, `VoiceExecResult`).

---

## Что в работе

**Пусто.** Серия `5.8.9h-2b` закрыта.

---

## Что впереди

| # | Заход | Заходов | Статус |
|---|---|---|---|
| 5.8.9f | Режимы SORT / SEARCH | 2 | ⬜ |
| 5.8.9d-2 | Отметка короче | 1 | ⬜ |
| 5.8.9d-3 | Переспросы ВК / отложенных | 1 | ⬜ |
| 5.8.9i | Мимикрия произношения | 1 | ⬜ |
| 5.8.6 | Vosk-полировка | 1 | ⬜ |
| 5.9 | Room — доводка | 1 | ⬜ |
| 5.10 | Общая полировка + туториалы | 3–4 | ⬜ |
| 6 | Релиз MVP | 2 | ⬜ |

**Итого: ~11–12 заходов.**

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