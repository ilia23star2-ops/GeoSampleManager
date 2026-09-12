# GeoSample Manager

Android-приложение для управления геохимическими пробами в горнодобывающей
промышленности. Учёт нарядов, импорт описей проб из Excel, сверка фактического
наличия и весовой контроль.

---

## Технологии

- **Kotlin**
- **Jetpack Compose** (Material 3)
- **Room** (SQLite)
- **Navigation Compose**
- **Kotlin Coroutines + Flow**
- Свой парсер `.xlsx` (без Apache POI)

---

## Целевые устройства

- **Планшеты** — основной сценарий.
- Адаптивность под телефон — вторичный сценарий.

**Аудитория — простые рабочие**, поэтому:
- крупные элементы управления,
- понятные формулировки,
- минимум лишнего,
- все настройки автосохраняются, кнопки «Сохранить» нет.

---

## Правила разработки

1. **Полные файлы с полным путём.** Всегда присылать целиком, не фрагменты.
2. **Комментарии и UI — на русском.** Код — стандартный Kotlin.
3. **Не использовать Apache POI** — тяжело для Android.
4. **Room-сущности, DAO и Repository** менять только при необходимости
   и предупреждая об этом.
5. **Настройки автосохраняются.**
6. Целевые устройства — **планшеты**.

---

## Структура проекта
app/src/main/java/com/example/geosamplemanager/
├── MainActivity.kt
├── GeoSampleApp.kt // Application, держит repository
├── data/
│ ├── AppDatabase.kt // Room-БД
│ ├── DatabaseRepository.kt // обёртка над DAO
│ ├── dao/ // AreaDao, OrderDao, SampleDao,
│ │ // OrderWellDao, SampleNoteDao
│ ├── entity/ // AreaEntity, OrderEntity, SampleEntity,
│ │ // OrderWellEntity, SampleNoteEntity
│ ├── excel/ // парсер .xlsx, ExcelAnalyzer,
│ │ // AreaResolver, OrderNumberExtractor,
│ │ // SampleFilter, XlsxReader
│ └── import/ // импорт в БД, история импортов
└── ui/
├── navigation/ // AppScaffold (Drawer), Screen, NavGraph
├── screens/ // все экраны, включая:
│ // ReconciliationModels.kt
│ // ReconciliationState.kt
│ // ReconciliationDialogs.kt
│ // ReconciliationViewModel.kt
│ // ReconciliationMapper.kt
│ // SearchScreen.kt
│ // MainScreen.kt, AddScreen.kt,
│ // SettingsScreen.kt, StatsScreen.kt,
│ // DbScreen.kt, EditScreen.kt
└── theme/ // Color, Theme, Type

---

## Навигация

`AppScaffold` в `ui/navigation/NavGraph.kt`:
- `ModalNavigationDrawer` — боковое меню.
- `TopAppBar` — заголовок текущей вкладки.
- `NavHost` — переходы между экранами.

Вкладки (`Screen`):
1. Главное меню — `MainScreen`
2. Добавить наряд — `AddScreen` (импорт Excel)
3. **Сверка и поиск** — `SearchScreen`
4. Статистика — `StatsScreen`
5. Редактирование — `EditScreen`
6. Управление БД — `DbScreen`
7. Настройки — `SettingsScreen`

---

## Как запускается приложение

1. `MainActivity` → `GeoSampleManagerTheme` → `AppScaffold`.
2. `GeoSampleApp` (Application) инициализирует `DatabaseRepository`
   с `applicationContext`.
3. `DbViewModel`, `ReconciliationViewModel` и др. берут репозиторий
   из `(application as GeoSampleApp).repository`.

---

## Ключевые сценарии

### Импорт Excel
См. `DATABASE.md` и `PROGRESS.md` (этап 4).

### Сверка и поиск
См. `DECISIONS.md` — все решения по UI и логике.

### Голосовой помощник (в разработке)
См. `NEXT_STEPS.md` — этап 5.8.

---

## Файлы документации

- `README.md` — этот файл.
- `PROGRESS.md` — что сделано, что в работе, что впереди.
- `DATABASE.md` — как данные попадают в БД.
- `DECISIONS.md` — все решения по UI и логике.
- `NEXT_STEPS.md` — план следующих заходов и открытые вопросы.

