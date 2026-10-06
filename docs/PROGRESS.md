# PROGRESS.md — история заходов

## 5.10 серия — теневая статистика

**Дата:** 2026-10-06
**Ветка:** `feature/5.10-shadow-stats` (архив, merge в `main` через PR #124)
**Контекст:** скрытый от ОП инструмент админа. Запись сессий,
визитов вкладок, работы с нарядами и событий в отдельную `stats.db`.
UI — «панель администратора» по долгому тапу на версии + пароль.

Спецификация: `docs/SHADOW_STATS.md`.

### Что закрыто в 5.10

**Модель и запись:**
- ✅ **`5.10-stat-model`** — `StatsDatabase`, 5 сущностей, `StatsDao`.
- ✅ **`5.10-stat-session`** — `SessionTracker`, `SessionTimeAccumulator`,
  fg/bg/active/idle.
- ✅ **`5.10-stat-activity-a`** — `ActivityAccumulator`, batch insert
  в `events` через `LogWriter`.
- ✅ **`5.10-stat-activity-b`** — `OrderWorkTracker`, `OrderWorkPhaseLogic`,
  фазы «Поиск / Сверка», интеграция в `ReconciliationViewModel`.
- ✅ **`5.10-stat-activity-b-2`** — фазы v2 (модель «последнее событие»),
  учёт ручного поиска, миллисекундная арифметика.
- ✅ **`5.10-stat-tabs`** — `TabVisitTracker`, правки `SessionTracker`
  и `NavGraph`, `Mutex` от гонок.
- ✅ **`5.10-stat-errors-a`** — `AutoWarnRules` (5+ неудачных поисков,
  3+ одинаковых ошибки).
- ✅ **`5.10-stat-errors-b`** — интеграция `AutoWarnRules` в
  `voiceSearch` и `LogWriter`.

**Панель администратора:**
- ✅ **`5.10-stat-admin-ui-1`** — `AdminPanelAggregator`, модели, агрегаты.
- ✅ **`5.10-stat-admin-ui-2`** — `AdminPanelScreen` (TopAppBar + Column
  без вложенного Scaffold).
- ✅ **`5.10-stat-admin-ui-3`** — долгий тап на версии в «О приложении».
  Фикс `return@Column` (Compose Stack.pop).
- ✅ **`5.10-stat-admin-ui-4`** — выбор дня (`ExposedDropdownMenuBox`),
  `getDistinctSessionDates`.
- ✅ **`5.10-stat-admin-ui-5a`** — блок «Незавершённые».
- ✅ **`5.10-stat-admin-v2-nav`** — табы День / Наряды / Ошибки,
  таймлайн дня, BackHandler.
- ✅ **`5.10-stat-admin-v2-time-filter`** — фильтр времени на таймлайне
  (`12`, `12:30`, `12-13`, `12:00-13:30`), пресеты масштаба,
  автоскролл, растяжка.
- ✅ **`5.10-stat-admin-v2-orders-a`** — `OrderSampleCounts`,
  `AdminOrderSummary`, `buildOrdersSummary`.
- ✅ **`5.10-stat-admin-v2-orders-b`** — таб «Наряды» (свёрнутые секции
  по участку, прогресс-бар, статусы, фильтр).
- ✅ **`5.10-stat-admin-v2-details-a`** — провал в сессию и визит.
- ✅ **`5.10-stat-admin-v2-details-b`** — `computeEventsAround` (±2 мин).
- ✅ **`5.10-stat-admin-v2-details-c`** — таб «Ошибки», `EventDetailScreen`,
  раскрытие `detailsJson`.

**Экспорт, ротация, пароль:**
- ✅ **`5.10-stat-admin-password`** — `AdminPanelAuth` (пароль `0000`),
  `AdminPanelUnlockScreen`, `lock()` при закрытии.
- ✅ **`5.10-stat-daily-file-1`** — `StatsExporter`, `.stats/YYYY-MM-DD.json`,
  кнопка «Экспорт дня».
- ✅ **`5.10-stat-daily-file-2`** — `StatsRotator` (месячная ротация
  `active.db` → `archive/YYYY-MM.db` через renameTo).

**Вырезание logs.db:**
- ✅ **`5.10-logs-cleanup-a`** — убраны `LogsScreen`, `LogsViewModel`,
  категория `SYSTEM` в Настройках.
- ✅ **`5.10-logs-cleanup-b`** — `LogsDatabase`, `LogDao`, `LogFileWriter`,
  `LogsFilter` удалены; `LogEntry` без Room; `LogWriter` пишет только
  в `stats.db`; `LogsDbCleanup` при апдейте.

### Отложено после 5.10

- ⬜ **`5.10-stat-admin-ui-5a-2`** — агрегация дублей «Незавершённые»
  по `orderId`.
- ⬜ **`5.10-stat-admin-ui-5b`** — реальные проблемы БД в `ProblemsBlock`
  (сейчас нули).
- ⬜ **`5.10-logs-cleanup-c`** — удалить `LogFormatter` + тест.
- ⬜ **`5.10-stat-admin-pinch`** — пинч-масштаб таймлайна.
- ⬜ **Device-check ротации** `stats.db` (обещано прогнать).
- ⬜ **Immutability** (хеш `stats.db`, метка старта).
- ⬜ **«Краш + удаление БД за 5 мин» → warn**.

---

## 5.9 серия — допиливание вкладок

**Дата:** 2026-09-29 … 2026-10-05
**Ветка:** `feature/5.9-full-project` (архив, merge в `main`)

Полностью закрыта и влита в `main`.

### Что закрыто в 5.9

**Вкладки:**
- ✅ **Статистика.**
- ✅ **Редактирование** (включая управление участками и нарядами).
- ✅ **БД** (19 пачек: бэкапы, откат, очистка, merge, compare, diagnostics, restructure).
- ✅ **Настройки** (внешний вид, тема, справка, звук, голос, Bluetooth).
- ✅ **Главная** (сводка, продолжить, незавершённые, проблемы, отчёт).

**Серия `logs`** — журнал аудита (9 подзаходов; впоследствии удалён в 5.10).

**Отдельные фиксы:**
- ✅ `sort-normalize` — SORT пишет цифры, не фонетику.
- ✅ `tts-audio-mode` — TTS через USAGE_MEDIA + STREAM_MUSIC.
- ✅ `tts-audio-mode-fix-normal` — TTS через MODE_NORMAL, NORMAL/LOUD=1.0.
- ✅ `settings-sound-3` — TTS-скорость ползунком 0.5–2.0.
- ✅ `settings-scale` — масштаб интерфейса через LocalDensity.
- ✅ `settings-scale-2` — textFactor/densityFactor, скроллы чипов и легенды.
- ✅ `settings-theme` — светлая/тёмная/системная тема.
- ✅ `settings-help-1` / `help-2a` / `help-2b` — справка.
- ✅ `main-a` — сводка, продолжить, незавершённые, проблемы БД.
- ✅ `main-b` — кнопка «Сделать отчёт» на Главной.
- ✅ `main-fix` — убран вложенный Scaffold.
- ✅ `exit` — выход с авто-бэкапом, back на Главной.

### Осталось в 5.9

- **`5.9-mass-add`** — в долгом ящике (по решению пользователя).

---

## 5.8.11 серия — рефакторинг голосового помощника

**Дата:** 2026-09-24 … 2026-09-28
**Ветка:** `feature/5.8.11-e4-voice-v2` (архив)

Полный рефакторинг ГП: pin скважины, очередь мультизапроса, мимикрия
TTS, честная ошибка вместо fallback, маркеры намерения,
подтверждение массовых, очередь веса, префиксы по буквам, SORT flat.

### Закрытые пачки серии

- `5.8.11-e4-fix-voice-1` (device ✅).
- `5.8.11-e4-ui-1` — кнопка «Наверх».
- `5.8.11-e4-weight-queue`.
- `5.8.11-e4-prefix-1` (device ✅).
- `5.8.11-e4-speak-1` (device ✅).
- `5.8.11-e4-markers-2` (device ✅).
- `5.8.11-e4-markers` (device ✅).
- `5.8.11-e4-pin-7` (device ✅).
- `5.8.11-e4-pin-6` (откачен в pin-7).
- `5.8.11-e4-pin-5`, `-pin-4`, `-pin-3`, `-pin-2`, `-pin-1`.
- `5.8.11-e4e-bundle` — мимикрия + единый путь.
- `5.8.11-e4-tests`.

### SORT-fix серия

- `5.8.11-sort-fix` (bab0a01) — SORT flat + авто-split.
- `5.8.11-sort-fix-3` (8769657) — фикс SORT.
- `5.8.11-sort-ui` (405d6f1) — SORT пишет в UI.
- `5.8.11-sort-fix-4` (ad26841) — задвоение токенов.
- `5.8.11-sort-fix-5` (eb4d1f5) — унификация Next.

### Что осталось по 5.8.11

- **И-24** — Vosk обрывает по короткой паузе.
- **И-35** — Vosk путает «четвёртая» / «четырнадцатая».

---

## Ранее (сводка)

- **5.8.11-a … -d2** — унификация поиска и ответа (SEARCH_MODEL).
- **5.8.10-a … -g2** — настройки UI, онбординг, импорт, голос.
- **5.8.6-2 … -5g** — Vosk-полировка.
- **5.8.9-f … -i** — голосовой ввод.
- **5.8.9-infra-2d** — CI вручную.
- **5.5** — заметки и фото.
- **5.1** — каркас сверки.
- **Этапы 1–4** — база: скелет, Room, настройки, Excel-импорт.