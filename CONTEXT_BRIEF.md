# CONTEXT_BRIEF.md — где мы сейчас

Одна страница «где мы сейчас». Обновляется в конце каждой сессии.
Новый ИИ читает вторым после `AI_RULES.md`.

**Дата обновления:** 2026-09-30 (вечер)

## Где мы

**Дома:** Android Studio. **На работе:** правки через `github.com`.
**Фича в работе:** `feature/5.9-full-project` — серия 5.9, допиливание
проекта (все вкладки кроме сверки). Порядок: **Статистика →
Редактирование → БД → Настройки → Главная.**

**Текущий фокус:** Статистика, пачка `report-xlsx`. Закрыты почти все
подзаходы. Остался **только `multi-report-ui`** — UI выбора нарядов
для мульти-отчёта. После него `report-xlsx` закрывается полностью.

## Что сделано в серии 5.9

**Пачки Статистики** (закрыты):

- `stats-screen`, `stats-reactive`, `stats-layout`, `stats-search`,
  `stats-search-2`, `stats-order-status`, `report-html`,
  `stats-charts`, `stats-fixes`, `stats-compare`, `stats-compare-2`.
- `bulk-confirm-2`, `table-responsive`, `row-highlight` — в сверке.

**Пачка `report-xlsx`** (в фиче):

- ✅ `xlsx-core` — ручной генератор .xlsx (zip + XML).
  Файлы: `XlsxWriter.kt`, `XlsxWriterTest.kt`.
- ✅ `xlsx-cells` — заполнение ячеек из `ReportData`.
- ✅ `xlsx-styles` — цвета строк, жирный.
- ✅ `xlsx-links` — гиперссылки внутри файла.
- ✅ `xlsx-multi` — мультинарядный XLSX (N листов + общий лист).
- ✅ `html-multi` — мультинарядный HTML.
- ✅ `xlsx-ui` — кнопка Excel в одиночном диалоге + доработки:
  - цвета фона строк как в сверке;
  - объединённая шапка A1:I1..A4:I4 с фоном;
  - автоширина колонок (шапка не растягивает);
  - вставка фото (drawing + media);
  - легенда цветов в колонке J;
  - работа в Excel, Online, мобильном, Bree, OfficeSuite.
- ✅ `report-html-tests` — тесты на одиночный HTML-генератор.
- ✅ `fix/5.9-cleanup` — убраны дубликаты в корне.
- ⏳ `multi-report-ui` — экран выбора нарядов. **Следующий.**

**Формат отчёта (согласован):** 1 лист = 1 наряд. Шапка (участок,
№, дата) объединена в 4 строки A1:I1..A4:I4. Таблица проб с цветами.
Заметки — колонка + общий лист «Приложения». Фото — счётчик +
картинки в блоке приложений. Гиперссылки внутри файла.
Легенда цветов в колонке J. Для мульти — титульная страница с
оглавлением.

**Спецификация `multi-report-ui` (согласована):**

- Q1 — экран `MultiReportScreen` (отдельный, не диалог).
- Q2 — дерево (участок → наряды) + строка фильтра, галочки.
- Q3 — при совпадении имён листов диалог: подтвердить (суффикс
  `(2)`) или пропустить.
- Q4 — две кнопки: «Экспорт в Excel» и «Экспорт в HTML».

## Что было в последних сессиях

**Закрыто (в `feature/5.9-full-project`):**

- `stats-compare-2` (пикер с поиском и деревом). Device-check ✅.
- `report-html`, `report-html-tests`.
- `xlsx-core`, `xlsx-cells`, `xlsx-styles`, `xlsx-links`.
- `xlsx-multi`, `html-multi`.
- `xlsx-ui` — 20+ коммитов. Фон, объединённая шапка, фото,
  легенда, работа в Excel Online и мобильном.
- `fix/5.9-cleanup` — убраны дубликаты `GeoSampleApp.kt` (корень),
  `.github/ISSUES.md`.
- `docs/5.9-docs-2` — CONTEXT_BRIEF, PROGRESS, NEXT_STEPS.

## Известные грабли (важно!)

### Git

- **`AS Commit` ломает репо.** Панель Commit в AS может показать
  фантомные «deleted» и удалить файлы. **Инцидент 29.09.2026.**
  Правило: все git-операции — **только через терминал**.
- **Файл легко сохранить не в ту папку** (`.github/app/...`).
  Проверка: `git ls-files | findstr ИмяФайла`.

### Gradle

- **Test-worker'ы падают при многопоточной сборке.**
  Обход: **AS-runner** (Project panel → правый клик по папке
  `data/report` → `Run 'Tests in ...'`) или
  `./gradlew testDebugUnitTest --no-daemon --max-workers=1`.

### XLSX (критичные — 30.09.2026)

- **`styles rel` в `workbook.xml.rels` — обязателен.**
  Без него Excel «восстанавливает» файл и теряет стили (цвета
  пропадают). Bree, OfficeSuite, LibreOffice всё равно находят
  styles.xml по имени — поэтому «у одних работает, у других нет».

- **`theme` в `<fgColor>` не использовать.** Excel Online, увидев
  `theme="N"`, берёт цвет из своей встроенной темы, а не из нашей
  `theme1.xml` — цвета «перепутываются». Оставляем только
  `rgb` + `indexed`.

- **Порядок элементов в `<font>` строго по ECMA-376:**
  `b, i, ..., u, sz, color, name`. Для LINK-шрифта:
  `<u/><sz val="11"/><color rgb="FF1976D2"/><name val="Calibri"/>`.

- **В `workbook.xml`** нужны `fileVersion`, `workbookPr`, `calcPr` —
  Excel их ждёт.

- **В `sheet.xml`** нужны `sheetViews` и `sheetFormatPr`.

- **`bgColor` = `fgColor`** в solid fill — максимальная совместимость.

- **`indexed` + `rgb` одновременно** в `<fgColor>` — Excel возьмёт
  `rgb`, примитивный вьюер — `indexed`.

- **Запись через `ByteArrayOutputStream`** — `zip.finish()` не
  флашит underlying stream, файл мог получаться обрезанным.

- **Порядок секций в sheet.xml:** `dimension` → `sheetViews` →
  `sheetFormatPr` → `cols` → `sheetData` → `mergeCells` →
  `hyperlinks` → `drawing`.

- **Порядок секций в styleSheet:** `numFmts` → `fonts` → `fills` →
  `borders` → `cellStyleXfs` → `cellXfs` → `cellStyles` → `dxfs` →
  `tableStyles`.

## Открытые вопросы

- **Q1–Q3 по `xlsx-multi`** — закрыты.
- **И-24.** Vosk обрывает длинные номера. Отложено до серии e4d.
- **И-35.** Vosk путает «четвёртая» / «четырнадцатая». Отложено до
  `e4d`.

## Не трогать

- Схема БД (version = 2).
- `AI_RULES.md` — актуален.

## Правила текущей сессии

- Сначала спроси: дома или на работе? Если на работе — `github.dev`?
- **Ветки — левый нижний угол AS.**
- Дома пачка → merge локально в фичу.
- **Git — только через терминал** (см. «Известные грабли»).
- **После каждого захода** — обновлять `CONTEXT_BRIEF`, `PROGRESS`,
  `NEXT_STEPS`.
- **Правки в файлах — точечные.**
