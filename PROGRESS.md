# PROGRESS.md — история заходов

## 5.9-full-project — вкладки «Статистика» и «Сверка»

**Дата:** 2026-09-29 (вечер)
**Фича:** `feature/5.9-full-project` (ahead 65 от main)
**Контекст:** серия заходов по допиливанию вкладок «Статистика» и
«Сверка и поиск». 21 пачка.

### `5.9-stats-screen` (закрыт, device ✅) — экран статистики

- Экран `StatsScreen` — сводка сверху, дерево участок → наряд → проба.
- 3 файла: `StatsScreen.kt`, `StatsViewModel.kt`, `StatsModels.kt`.
- Сводка (Всего / Найдено / Не найдено / Холостые / ВК / Отложено / Ошибки).
- Чипы фильтров «Все / Найдено / Не найдено».
- Кнопки «Развернуть всё / Свернуть всё», «Отчёт» (заглушка).

### `5.9-stats-reactive` (закрыт, device ✅) — реактивность

- `SampleDao.getAllSamplesFlow()`, `DatabaseRepository.getAllSamplesFlow()`.
- `StatsViewModel.subscribeToDb()` — `combine(areasFlow, ordersFlow, samplesFlow)`.
- Дерево пересобирается автоматически при изменении БД.

### `5.9-stats-layout` (закрыт, device ✅) — master-detail

- `BoxWithConstraints` → `isWide = maxWidth >= 600.dp`.
- Планшет: дерево слева (35 %), правая панель (65 %).
- Телефон: одна колонка, кнопка «←» возвращает к дереву.
- `selectedOrderId: StateFlow<Long?>` в ViewModel.
- Плашка «Пустой / Не начат / В работе / Готов / Проверить».
- Прогресс-бар наряда, сводка, dropdown «Тип диаграммы».
- Правая панель — целым `LazyColumn`.

### `5.9-stats-search` (закрыт, device ✅) — поиск наряда/участка

- Строка поиска сверху левой панели.
- `withSearch(query)` — подстрока, регистронезависимо.
- Ищем по номеру наряда ИЛИ по имени участка.
- Авто-раскрытие найденных, чип «Скрыть готовые».

### `5.9-stats-search-2` (закрыт, device ✅) — дебаунс + FAB

- `SEARCH_DEBOUNCE_MS = 250` — печать без лагов.
- `applyFilters()` в `Dispatchers.Default`.
- FAB «Наверх» в правой панели (порог `> 8`).

### `5.9-stats-order-status` (закрыт, device ✅) — статусы

- `OrderStatus`: `EMPTY / NOT_STARTED / IN_PROGRESS / NEEDS_REVIEW / READY`.
- `computeOrderStatus(stats)`, `sortOrdersByStatus`.
- Иконка-кружок в дереве, 5 цветов.
- `withHideReady(hideReady)` — фильтр «Скрыть готовые».
- Диалог формата отчёта: HTML + Excel (PDF через браузер).

### `5.9-report-html` (закрыт, device ✅) — HTML-отчёт

- `ReportHtmlGenerator` — самодостаточный `.html` с data-uri фото.
- Кнопка «Сохранить PDF / Печать» → `window.print()`.
- Якорные ссылки `↩ К пробе` для возврата.
- Заметки + фото встроены, интернет не нужен.
- `StatsViewModel.generateHtmlReport(orderId, uri)` — SAF.

### `5.9-stats-charts` (закрыт, device ✅) — Canvas-диаграммы

- 3 типа: круговая / столбцы / по скважинам.
- 5 категорий с приоритетом: found > postponed > control > blank > not_found.
- Круг: цифры на секторах; легенда с числами и %.
- Столбцы: цифра внутри полоски.
- По скважинам: сегментированный бар + легенда X/Y.
- Drill-down: `DrillLevel`, `filterRowsByDrillStack`, хлебные крошки.

### `5.9-sverka-fixes` (закрыт, device ✅) — фиксы сверки

- `ReconciliationState.toggleWeightControl` обнуляет `controlWeight`.
- `PostponedDialog` — 5 действий (Отметить / Снять / Заметка / Редакт.).
- `TableHeader` и `SampleRowItem`: `Скважина` / `№ пробы` через `weight(1f)`.

### `5.9-stats-fixes` (закрыт, device ✅) — скролл + сводка участка

- Левая панель: `LazyListState` + FAB «Наверх» (порог `> 8`).
- `selectedAreaId` в ViewModel, `selectArea(areaId)`.
- `AreaDetailsPanel` — сводка участка: прогресс, цифры, список нарядов.

### `5.9-bulk-confirm` (закрыт, device ✅) — подтверждение массовых

- `markAllData`/`clearAllData` — данные клика фиксируются.
- Диалог «Отметить все N проб?» + «Сбросить N отметок?».
- Кнопка «Сбросить» считает только `found == true`.

### `5.9-bulk-confirm-2` (закрыт, device ✅) — очередь веса в UI

- `BulkDecision.BlankNeedsWeight` — новый тип.
- `BulkActionsDialog` — 3 секции: ВК / Холостые / Отложенные.
- `applyBulkMarkFoundForRows` — `blankWeights` мапа.

### `5.9-table-responsive` (закрыт, device ✅) — горизонтальный скролл

- `BoxWithConstraints` — `isWide = maxWidth >= 600.dp`.
- Узкий экран: общий `ScrollState`, колонки «Скважина» / «№ пробы»
  фиксированные 140 dp.
- Широкий: как было — с `weight(1f)`.
- Синхронный скролл шапки и строк.

### `5.9-row-highlight` (закрыт, device ✅) — подсветка строки

- `selectedRowId` в `SearchScreen` (`rememberSaveable`).
- Тап по пустому месту строки — рамка 2 dp primary + фон primary α12 %.
- Повторный тап — снять. Дочерние клики не сбрасывают.

### `5.9-stats-compare` (закрыт, device ✅) — сравнение

- Кнопка «Сравнить» открывает `CompareDialog`.
- 7 метрик: Всего / Найдено / Не найдено / Холостые / ВК / Отложено / Ошибки.
- Любые комбинации: наряд / участок, участок / наряд.

### `5.9-stats-compare-2` (закрыт, device ✅) — пикер с деревом

- `TargetPickerDialog` — пикер с поиском и деревом.
- Дерево: участок → его наряды, статус-кружок.
- Поиск сохраняет иерархию: по участку или по номеру наряда.
- `CompareTarget.Order(status)` — статус теперь обязателен.
- Защита от выбора одного и того же объекта дважды.

---

## 5.8.11-e4 серия — рефакторинг ГП

### `5.8.11-sort-fix` (закрыт) — SORT и голосовая навигация

- `sort-fix` (bab0a01) — SORT flat, `trySplitByNumberBlocks` (позже удалён),
  `advanceToNext` не сбрасывает mode.
- `sort-fix-3` (8769657) — удалён `trySplitByNumberBlocks`,
  `Message.spoken`, `voiceNext()` в SORT → «не используется».
- `sort-ui` (405d6f1) — `voiceSortFlat` пишет канонику в `state.query`.
- `sort-fix-4` (ad26841) — фикс задвоения в `QueryTokenizer`,
  `Message.display`.
- `sort-fix-5` (eb4d1f5) — `NextInQueue` удалён, `voiceNext()` — проверка
  `hasQueue`, `voiceSortFlat` — каноника + фонетика параллельно.
- `voice-24-debounce` — склейка фраз Vosk, debounce.
- `voice-24-debounce-2` — `DEBOUNCE_MS = 1200`.
- Docs-пачка `docs/5.8.11-sort-fix-close`.

**Закрыто в предыдущих сессиях `e4`:**

- `e4e-bundle`, `e4-tests`, `e4-pin-1…7`, `e4-markers`, `e4-markers-2`,
  `e4-speak-1`, `e4-prefix-1`, `e4-weight-queue`, `e4-ui-1`,
  `e4-fix-voice-1` — 15 пачек.

**Закрыто в `main`:**

- `e4-hotfix`, `e4-fix-1`, `e4-fix-2`.
- `docs/5.8.11-rules-fix-merge`, `docs/5.8.11-git-rules`,
  `docs/5.8.11-tests-rules`, `docs/5.8.11-rules-cleanup`,
  `docs/5.8.11-e4-pin-close`, `docs/5.8.11-sort-fix-close`.
- `chore/5.8.11-ci-automation`.

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