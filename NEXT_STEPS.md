# План следующих заходов

> Обновляется после каждого захода.
> **Текущий — 5.8.9h-2 (переключение UI и ГП на `UnifiedSearch`).**
> Следующий — 5.8.9-infra-2e (Vosk в CI).

---

## Порядок заходов

| # | Заход | Заходов | Статус |
|---|---|---|---|
| — | Merge `feature/5.8.9h-1-unified-search` | — | 🟡 в ветке |
| 5.8.9h-2 | Переключение UI и ГП на `UnifiedSearch` | 2 | 🟡 в работе |
| 5.8.9-infra-2e | Vosk в CI (выкачка модели из релиза) | 1 | ⬜ |
| 5.8.9f | Режимы SORT / SEARCH | 2 | ⬜ |
| 5.8.9d-2 | Отметка короче (порядковый + счётчик) | 1 | ⬜ |
| 5.8.9d-3 | Переспросы ВК / отложенных | 1 | ⬜ |
| 5.8.9i | Мимикрия произношения | 1 | ⬜ |
| 5.8.6 | Vosk-полировка | 1 | ⬜ |
| 5.9 | Room — доводка | 1 | ⬜ |
| 5.10 | Общая полировка | 3–4 | ⬜ |
| 6 | Релиз MVP | 2 | ⬜ |

---

## Текущий заход — 5.8.9h-2

**Цель:** один алгоритм поиска для UI и ГП. Переключить обе стороны на
`UnifiedSearch` (создан в `5.8.9h-1`).

### Что делаем

1. **`ReconciliationViewModel.voiceSearch`:**
    - Заменить `VoiceSearch(source).search(candidates)` на
      `UnifiedSearch.search(all, candidates, filterMode=…)`.
    - `all` = `source.loadAll()`.

2. **`ReconciliationModels.analyzeMatch` / `filterByQuery`:**
    - Переписать через `UnifiedSearch.search(...)`.
    - Смапить `SampleRow` ↔ `VoiceSampleHit`.

3. **`ReconciliationModels.MatchedKind`** — заменить на
   `UnifiedMatchKind` из `UnifiedSearch.kt` (или смапить).

4. **`VoiceSearch.kt`** — удалить или превратить в тонкую обёртку над
   `UnifiedSearch`.

5. **Тесты:** прогнать `VoiceSearchTest`, `VoiceCommandParserTest`,
   `UnifiedSearchTest`. Все зелёные.

### Файлы (план)

- `ui/screens/ReconciliationViewModel.kt`.
- `ui/screens/ReconciliationModels.kt`.
- `data/voice/VoiceSearch.kt` — обёртка или удаление.

### Разбить на 2

**5.8.9h-2a** — ГП (`ReconciliationViewModel.voiceSearch`).
**5.8.9h-2b** — UI (`analyzeMatch` / `filterByQuery`).

### Стоп-сигнал

>3 файлов, меняются публичные сигнатуры — сначала аудит, потом код.

---

## Заход 5.8.9-infra-2e — Vosk в CI

**Цель:** CI собирает APK с моделью Vosk.

### Что делаем

В `.github/workflows/build.yml` добавляем шаг перед сборкой APK:
- Скачать архив с моделью Vosk из релиза GitHub.
- Распаковать в `app/src/main/assets/vosk/`.

### Файлы (план)

- `.github/workflows/build.yml`.

### Что нужно от пользователя

- URL релиза с моделью (например,
  `https://github.com/ilia23star2-ops/GeoSampleManager/releases/download/models-v1/vosk-small-ru.zip`).
- Имя архива, путь распаковки.

---

## Заход 5.8.9f — Режимы SORT / SEARCH

**Цель:** два голосовых режима.

- SEARCH: ответ + статистика, отметки работают.
- SORT: только «X — наряд Y», без отметок.

### Файлы (план)

- `data/voice/VoiceSession.kt` — `VoiceMode`.
- `ui/screens/ReconciliationViewModel.kt`.
- `ui/screens/SearchScreen.kt`.
- `ui/screens/VoiceDialog.kt`.

**Разбить на 2.**

---

## Заход 5.8.9d-2 — Отметка короче

### Файлы (план)

- `data/voice/VoiceModels.kt` — `Marked.count`.
- `ui/screens/ReconciliationViewModel.kt` — счётчик по скважине.
- `ui/screens/VoiceDialog.kt`.

---

## Заход 5.8.9d-3 — Переспросы

### Файлы (план)

- `ui/screens/VoiceSession.kt`.
- `ui/screens/ReconciliationViewModel.kt`.
- `ui/screens/VoiceDialog.kt`.

---

## Заход 5.8.9i — Мимикрия произношения

### Файлы (план)

- `data/voice/VoiceSession.kt`.
- `ui/screens/VoiceDialog.kt`.
- `data/voice/VoiceSpeaker.kt`.

---

## Заход 5.8.6 — Vosk-полировка

### Файлы (план)

- `data/util/VoiceController.kt`.
- `data/voice/VoiceGrammar.kt`.

---

## Готовые заходы

- ✅ 1–4 (база)
- ✅ 5.1 (каркас сверки)
- ✅ 5.5.1–5.5.3 (заметки и фото)
- ✅ 5.8.1 (каркас голоса)
- ✅ Спецификация ГП
- ✅ 5.11 (множественный поиск UI)
- ✅ 5.8.2 (парсер)
- ✅ 5.8.3 (префиксы + поиск)
- ✅ 5.8.4 (команды отметок)
- ✅ 5.8.8 (Vosk + TTS)
- ✅ 5.8.8d–k (финальные фиксы ГП)
- ✅ 5.8.9-infra (правила двух машин — базовая версия)
- ✅ 5.8.9a (модель AnswerState)
- ✅ 5.8.9a-t (тесты модели)
- ✅ 5.8.9b (UI индикатора)
- ✅ 5.8.9c (звуковая карта)
- ✅ 5.8.9d-1 (TTS окна ответа)
- ✅ 5.8.9bug-1…bug-3-fix-5 (серия багов ВК + п/п)
- ✅ 5.8.9e-1 (свёрнутые группы + участок в заголовке)
- ✅ 5.8.9e-2 (+fix-1/2/3) — индикатор мультипоиска
- ✅ 5.8.9e-3 (keepScreenOn)
- ✅ 5.8.9e-4 (другой участок / другой наряд)
- ✅ 5.8.9e-5 (третья + spell-out)
- ✅ 5.8.9g-1 (парсер MarkByNumbers / MarkAll)
- ✅ 5.8.9g-2 (множественная отметка + все)
- ✅ 5.8.9g-3 (грамматика + сброс сессии)
- ✅ 5.8.9-infra-2a + fix-1/2/3 (CI — GitHub Actions)
- ✅ 5.8.9-infra-2b + fix-1/2 (правила: три режима)
- ✅ 5.8.9-infra-2d (CI вручную)
- 🟡 5.8.9h-1 (UnifiedSearch + 18 тестов) — в ветке

---

## Заметки по окружению (не удалять)

- **Gradle user home (дома):** `C:\gradle_home` — кириллица в пути
  ломает `GradleWorkerMain`.
- **Gradle JDK:** Oracle OpenJDK 17.0.12.
- **Имена тестов:** только латиница (backtick-кириллица ломает
  Gradle Test Executor).
- **Две машины:** см. `AI_RULES.md` §20. На работе — `github.dev`
  (приоритет) или `github.com`. Git на машине не установлен.