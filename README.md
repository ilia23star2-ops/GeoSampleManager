# GeoSample Manager (Android)

Мобильное приложение для управления геохимическими пробами в горнодобывающей
промышленности. Android-версия десктопного приложения GeoSample Manager v12.1.0.

## Что умеет

- Импорт проб из Excel (.xlsx) с автодетектом структуры таблицы
- Автоопределение участка и наряда по префиксам и имени файла/листа
- Работа с многослойными книгами: очередь, авто-импорт, отчёт
- Ручной маппинг колонок при необходимости
- Обучение словаря на лету (запоминание заголовков, префиксов)
- Настройки импорта (редактируемые, экспорт/импорт JSON)
- История импортов с причинами пропусков
- Проверка существующих нарядов (добавить/пропустить/заменить)
- Фильтрация бланков и стандартных образцов
- Учёт проб со статусами: обычная, холостая, контрольная (ВК), отложенная

## Стек

- **Kotlin** + **Jetpack Compose** + **Material 3**
- **Room** — локальная база (SQLite)
- **Gson** — JSON для настроек и истории
- **Собственный парсер .xlsx** (без Apache POI)
- **Target SDK 34**, minSdk 24

## Сборка

Через GitHub Actions автоматически при пуше в `main`. APK — в разделе **Actions → Artifacts**.

## Структура пакетов
com.example.geosamplemanager/
├── data/
│ ├── entity/ — Room-сущности (Area, Order, Sample, OrderWell, SampleNote)
│ ├── dao/ — DAO-интерфейсы
│ ├── AppDatabase.kt
│ ├── DatabaseRepository.kt
│ ├── excel/ — парсер XlsxReader, анализатор, импортёр
│ ├── settings/ — ImportSettings, SettingsRepository
│ └── history/ — ImportHistory, ImportHistoryRepository
├── ui/
│ ├── theme/ — Material 3
│ ├── navigation/ — NavGraph, Screen
│ └── screens/ — экраны (Main, Add, Search, Stats, Edit, Db, Settings)
└── GeoSampleApp.kt — Application с репозиториями

## Статус

См. `PROGRESS.md`.