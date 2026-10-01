# CONTEXT_BRIEF.md — где мы сейчас

Одна страница «где мы сейчас». Обновляется в конце каждой сессии.
Новый ИИ читает вторым после `AI_RULES.md`.

**Дата обновления:** 2026-10-01 (день)

## Где мы

**Дома:** Android Studio. **На работе:** тоже Android Studio + git
в терминале (настроено 01.10.2026).
**Фича в работе:** `feature/5.9-full-project` — серия 5.9, допиливание
проекта (все вкладки кроме сверки). Порядок: **Статистика →
Редактирование → БД → Настройки → Главная.**

**Текущий фокус:** **пачка `report-xlsx` закрыта полностью** —
включая `multi-report-ui`. Все отчёты (одиночные и мульти, XLSX и
HTML) работают. **Следующая вкладка — Редактирование.**

## Что сделано в серии 5.9

### Статистика — закрыта

**Пачки** (все закрыты):
`stats-screen`, `stats-reactive`, `stats-layout`, `stats-search`,
`stats-search-2`, `stats-order-status`, `report-html`,
`stats-charts`, `stats-fixes`, `stats-compare`, `stats-compare-2`.
`bulk-confirm-2`, `table-responsive`, `row-highlight` — в сверке.

### Пачка `report-xlsx` — закрыта

- ✅ `xlsx-core` — ручной генератор .xlsx (zip + XML).
- ✅ `xlsx-cells` — заполнение ячеек из `ReportData`.
- ✅ `xlsx-styles` — цвета строк, жирный.
- ✅ `xlsx-links` — гиперссылки.
- ✅ `xlsx-multi` — N нарядов → N листов + общий лист «Приложения».
- ✅ `html-multi` — мультинарядный HTML.
- ✅ `xlsx-ui` — Excel в одиночном диалоге + все доработки.
- ✅ `report-html-tests` — тесты на одиночный HTML-генератор.
- ✅ `multi-report-ui` — экран выбора нарядов для мультиотчёта.

**Возможности отчёта:**

- 1 лист = 1 наряд. Шапка (участок, №, дата) объединена
  A1:I1..A4:I4 с фоном. Таблица проб с цветами как в сверке.
- Заметки и фото — в листе «Приложения» (общий для мульти).
- Картинки в XLSX (drawing + media).
- Гиперссылки внутри файла (Наряд ↔ Приложения).
- Легенда цветов в колонке J.
- **Мультиотчёт** — экран `MultiReportScreen`: дерево участок →
  наряды с чекбоксами, фильтр, две кнопки (Excel / HTML),
  диалог при совпадении имён листов (суффикс `(2)` или пропуск).
- Работает в Excel, Online, мобильном, Bree, OfficeSuite.

### Пачки инфраструктуры

- ✅ `docs/5.9-docs-2` — доки после `xlsx-multi` и `html-multi`.
- ✅ `fix/5.9-cleanup` — убраны дубликаты в корне.
- ✅ `5.9-cleanup-2` — warnings компилятора в `XlsxWriter`.
- ✅ `docs/5.9-docs-3` — доки после `xlsx-ui`.

## Что делать дальше

**Следующая вкладка — Редактирование.**

**Первый заход — аудит вкладки.** Посмотреть `EditScreen.kt` и
`EditViewModel.kt` (если есть), понять текущее состояние, составить
список задач. Не начинать код, пока не понятна картина.

**Что нужно для аудита:**

- `ui/screens/EditScreen.kt`
- `ui/screens/EditViewModel.kt` (если существует)
- связанные модели/диалоги в `ui/screens/`

## Известные грабли (важно!)

### Git

- **`AS Commit` ломает репо.** Все git-операции — **только терминал**.
- **Файл легко сохранить не в ту папку** (`.github/app/...`).
  Проверка: `git ls-files | findstr ИмяФайла`.
- **`git checkout`/`New Branch` — левый нижний угол AS.**
- **На рабочей машине git настроен 01.10.2026**: настроен user.name,
  user.email, работает push.

### Gradle

- **Test-worker'ы падают при многопоточной сборке.**
  Обход: **AS-runner** или
  `./gradlew testDebugUnitTest --no-daemon --max-workers=1`.

### XLSX (критичные — все из 30.09.2026, не забывать!)

- **`styles rel` в `workbook.xml.rels` — обязателен.** Без него Excel
  «восстанавливает» файл и теряет стили (цвета пропадают). Bree,
  OfficeSuite, LibreOffice всё равно находят styles.xml по имени.
- **`theme` в `<fgColor>` не использовать.** Excel Online, увидев
  `theme="N"`, берёт цвет из своей встроенной темы → цвета
  «перепутываются». Оставляем только `rgb` + `indexed`.
- **Порядок элементов в `<font>` строго по ECMA-376:**
  `b, i, ..., u, sz, color, name`. Для LINK:
  `<u/><sz val="11"/><color rgb="FF1976D2"/><name val="Calibri"/>`.
- **В `workbook.xml`** нужны `fileVersion`, `workbookPr`, `calcPr`.
- **В `sheet.xml`** нужны `sheetViews`, `sheetFormatPr`.
- **`bgColor` = `fgColor`** в solid fill.
- **`indexed` + `rgb` одновременно** в `<fgColor>`.
- **Запись через `ByteArrayOutputStream`** (не `zip.finish()`).
- **Порядок в sheet.xml:** `dimension` → `sheetViews` →
  `sheetFormatPr` → `cols` → `sheetData` → `mergeCells` →
  `hyperlinks` → `drawing`.
- **Порядок в styleSheet:** `numFmts` → `fonts` → `fills` →
  `borders` → `cellStyleXfs` → `cellXfs` → `cellStyles` → `dxfs` →
  `tableStyles`.

## Отложенные вопросы

- **И-24.** Vosk обрывает длинные номера. Отложено до серии `e4d`.
- **И-35.** Vosk путает «четвёртая» / «четырнадцатая». До `e4d`.

## Не трогать

- Схема БД (version = 2).
- `AI_RULES.md` — актуален.

## Правила текущей сессии

- Сначала спроси: дома или на работе? (Оба — AS.)
- **Ветки — левый нижний угол AS.**
- Дома/на работе пачка → merge локально в фичу.
- **Git — только через терминал** (см. «Известные грабли»).
- **После каждого захода** — обновлять `CONTEXT_BRIEF`, `PROGRESS`,
  `NEXT_STEPS`.
- **Правки в файлах — точечные.**