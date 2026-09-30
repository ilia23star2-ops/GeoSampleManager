# NEXT_STEPS.md — что делаем дальше

## Текущий фокус

**Фича `feature/5.9-full-project`, серия 5.9.**
Пачка `report-xlsx` в фиче. Генераторы (XLSX и HTML, одиночные и
мульти) готовы. Осталось подключить UI и кнопку Excel.

## Ближайший заход — `multi-report-ui`

**Что делаем:** экран выбора нарядов для мульти-отчёта. Дерево
(участок → наряды), строка фильтра сверху, галочки. Две кнопки:
«Экспорт в Excel» и «Экспорт в HTML». Диалог при совпадении имён
листов.

**Спецификация (согласована):**

1. **Экран — отдельный** `MultiReportScreen` (не диалог).
2. **Список — дерево** участок → наряды, сверху строка фильтра.
3. **При совпадении имён** — диалог: подтвердить (суффикс « (2)») или
   пропустить.
4. **Две кнопки:** «Экспорт в Excel» и «Экспорт в HTML».

**Подключает:**

- `XlsxMultiReportBuilder` → `XlsxWriter.write` (уже готовы).
- `MultiHtmlReportGenerator` → запись в файл (по аналогии
  с `StatsViewModel.generateHtmlReport`).

**Файлов:** ~5–6.

- `MultiReportScreen.kt` — новый экран.
- `MultiReportViewModel.kt` — новый ViewModel.
- `NavGraph.kt` / `Screen.kt` — навигация.
- `StatsScreen.kt` — кнопка входа (в `AreaDetailsPanel` — «Отчёт по
  участку» или в `OrderDetailsPanel` — «Мультиотчёт»).
- Тесты — на ViewModel или чистую логику (если получится оторвать
  от Android).

**Ветка:** `fix/5.9-multi-report-ui` от `feature/5.9-full-project`.
**Где:** дома (нужна сборка + device-check).

**Файлы для запроса в начале захода:**

- `ui/navigation/NavGraph.kt`
- `ui/navigation/Screen.kt`
- `ui/screens/StatsModels.kt` (уже есть в чате)
- `ui/screens/StatsDialogs.kt`
- `ui/screens/StatsViewModel.kt` (уже есть в чате)
- `ui/screens/StatsScreen.kt` (уже есть в чате)

## После `multi-report-ui` — `xlsx-ui`

**Что делаем:** кнопка Excel в одиночном `ReportFormatDialog`
(в `StatsScreen.kt`).

Сейчас там:

```kotlin
ReportFormat.EXCEL -> scope.launch {
    snackbarHostState.showSnackbar("Excel-отчёт — в разработке")
}
