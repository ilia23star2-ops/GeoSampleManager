
# База данных GeoSample Manager

> Room (SQLite). Схема, путь данных, что может быть `null`, бэкапы.
>
> **Текущая версия основной БД: 2.** Есть отдельная БД журнала
> (`logs.db`) — см. раздел «Журнал аудита» в конце.

---

## Схема БД (version = 2)

6 таблиц. Связи — `ForeignKey` с `ON DELETE CASCADE`.
`exportSchema = false`.

### 1. `areas` — участки

| Поле | Тип | Описание |
|---|---|---|
| `id` | Long PK | Автогенерация |
| `area_name` | String | Название |
| `created_date` | Long | Timestamp |

### 2. `orders` — наряды

| Поле | Тип | Описание |
|---|---|---|
| `id` | Long PK | Автогенерация |
| `area_id` | Long FK → areas.id | CASCADE |
| `order_number` | String | Номер |
| `created_date` | Long | Timestamp |

**UNIQUE** по `(area_id, order_number)`.

### 3. `samples` — пробы

| Поле | Тип | Что заполняется |
|---|---|---|
| `id` | Long PK | Автогенерация |
| `order_id` | Long FK → orders.id | Всегда |
| `serial_number` | Int | Порядковый № в листе |
| `sample_number` | String | Номер пробы |
| `well_number` | String | Номер скважины / выработки |
| `workings` | String? | **Всегда null** (задел) |
| `interval_from` | Double? | Число или null |
| `interval_to` | Double? | Число или null |
| `weight` | Double? | Основной вес |
| `control_weight` | Double? | Вес ВК |
| `actual_weight` | Double? | **Всегда null** (задел) |
| `sample_type` | String | `auger` / `channel` / `cobra` / `duplicate` |
| `status` | String | `normal` / `blank` / `control` |
| `reserved_type` | String? | **Всегда null** |
| `material_desc` | String? | Характеристика |
| `found` | Boolean | При импорте — `false` |
| `weight_control` | Boolean | При импорте — `false` |
| `postponed` | Boolean | При импорте — `false` |
| `has_note` | Boolean | При импорте — `false` |
| `has_photo` | Boolean | **Новое в v2.** При импорте — `false` |

**UNIQUE** по `(order_id, sample_number)`. Повторный импорт — IGNORE.

### 4. `order_wells` — скважины наряда

| Поле | Тип |
|---|---|
| `id` | Long PK |
| `order_id` | Long FK → orders.id (CASCADE) |
| `well_number` | String |

Заполняется автоматически при импорте.

### 5. `sample_notes` — заметки

| Поле | Тип | Описание |
|---|---|---|
| `id` | Long PK | |
| `sample_id` | Long FK → samples.id | CASCADE |
| `note_text` | String? | Текст |
| `created_date` | Long | |

**Одна заметка на пробу.**

⚠️ **В v1 было поле `image_path`** — в v2 удалено.

### 6. `sample_images` — фото пробы (**новая в v2**)

| Поле | Тип | Описание |
|---|---|---|
| `id` | Long PK | |
| `sample_id` | Long FK → samples.id | CASCADE |
| `image_path` | String | Абсолютный путь в `filesDir/sample_photos/` |
| `created_date` | Long | Timestamp |

**Индекс** по `sample_id`.

Одна проба — 0..N фото.

---

## Путь данных: Excel → БД

### Шаг 1. Пользователь выбирает файл
`AddScreen` → `OpenDocument` → `Uri`.

### Шаг 2. Метаданные
`XlsxReader.readMetadata(openStream)` → `List<SheetMeta>`.

### Шаг 3. Очередь
Фильтр: `rowCount >= 5`.

### Шаг 4. Для каждого листа
1. **Чтение** — `XlsxReader.readSheet` → `SheetData`.
2. **Анализ** — `ExcelAnalyzer.analyzeSheet`.
3. **Сборка** — `ExcelImporter.buildOrder`:
   - `SampleFilter.classify` → KEEP / SKIP_BLANK / SKIP_EMPTY.
   - `AreaResolver.resolve`, `OrderNumberExtractor.extract`.
4. **Предпросмотр** — диалог.
5. **Запись** — `AddViewModel.doImportToDb`.

---

## Путь данных: заметки и фото (v2)

### Заметка
1. `NotePhotoDialog` из строки пробы.
2. `loadNoteWithPhotos(sampleId)`.
3. «Сохранить» → `saveNoteText` → `upsertNote` → `syncHasNoteAndPhoto`.

### Фото
1. «Сделать фото» / «Из галереи».
2. `PhotoStorage.compressAndSave` — скейл до 1024 px, JPEG 80%.
3. `repo.addPhoto(sampleId, path)`.

---

## Коды полей → UI

### `sample_type`
| Код | UI |
|---|---|
| `auger` | Шнековая |
| `channel` | Бороздовая |
| `cobra` | Кобра |
| `duplicate` | Дубликат |

### `status`
| Код | UI | Когда |
|---|---|---|
| `normal` | Обычная | При импорте |
| `blank` | Холостая | «Холост» в типе |
| `control` | Весовой контроль | **Вручную** |

---

## Атомарные UPDATE в `SampleDao`

`setFound`, `setPostponed`, `setControlWeight`, `setWeight`,
`setWeightControl`, `setStatus`, `setHasNote`, `setSampleType`,
`setMaterialDesc`, `setSampleNumber`, `setWellNumber`, `setInterval`,
`toggleFound`, `updateAll`.

## Методы `DatabaseRepository`

**Сверка:** `setSampleStatus`, `setHasNote`, `setWeight`,
`setWeightControl`, `saveRows`, `deleteSampleWithRenumber`,
`deleteWellsForOrder`, `upsertNote`, `getNote`.

**Заметки и фото (v2):** `getPhotosForSample`, `getImagePathsForSample`,
`addPhoto`, `deletePhoto`, `getNoteWithPhotos`, `syncHasNoteAndPhoto`.

**Бэкапы и восстановление:** `checkpointWal`, `getDatabaseFile`,
`getPhotosDir`, `getRollbackBackupsDir`, `getDbInfo`, `clearOrder`,
`clearAllData`.

**Диагностика (`db-diagnostics`):** `runDiagnostics`,
`applyDiagnosticsFixes`.

---

## Бэкапы и авто-бэкапы

### Формат `.gsmbackup`

Zip-архив:
- `manifest.json` — метаданные.
- `geosamples.db` — сама БД.
- `sample_photos/` — папка с фото.

### `manifest.json`

```json
{
  "format_version": 1,
  "created_at": 1727800000000,
  "app_version": "1.0",
  "db_schema_version": 2,
  "operation": "restore",
  "counts": {
    "areas": 1, "orders": 2, "samples": 30,
    "photos": 3, "notes": 1
  }
}
format_version — версия формата.

db_schema_version — версия схемы.

operation — restore / rollback / clean / export / diagnostics /
unknown.

Структура папок в Загрузках
Загрузки/GeoSampleManager/
  pre_restore/       — авто-бэкапы перед импортом
  pre_rollback/      — авто-бэкапы перед откатом
  pre_clean/         — авто-бэкапы перед очисткой
  pre_diagnostics/   — авто-бэкапы перед диагностикой
  exports/           — пользовательские экспорты
  .logs/             — файловый архив журнала (скрытая)
Приватно те же файлы (кроме exports/ и .logs/) — в
filesDir/db_backups/.

Имя файла
Авто-бэкап: pre_<operation>_YYYYMMDD_HHmm.gsmbackup.

Пользовательский экспорт: <имя>_YYYYMMDD_HHmm.gsmbackup.

Ротация
Держим 5 последних на каждую операцию. Раздельно для приватных
и публичных. Применяется:

после импорта (pre_restore);

после отката (pre_rollback);

после очистки (pre_clean);

после диагностики (pre_diagnostics);

при открытии диалога «Откат» (публичные, вручную).

Импорт
DbImportPickerDialog показывает только файлы из exports/ +
SAF-выбор. Авто-бэкапы pre_* импортируются только через диалог
«Откат».

Откат
DbRollbackDialog объединяет приватные и публичные pre_*.
Дедупликация по имени файла, приоритет приватного. Метка источника
в строке: «Внутренний» / «Загрузки».

Управление бэкапами
BackupManagerDialog:

сводка (количество, размер, счётчики по источнику/операции);

список всех бэкапов;

удаление одного (стирает обе копии);

«Удалить старые» — ручная ротация;

«Удалить авто» — все авто-бэкапы, экспорты остаются.

Слияние двух БД
MergeWizard:

превью: новые / идентичные / конфликты;

конфликты проб — по полям, массовые стратегии + точечно;

дерево Наряд → Скважина → Проба;

авторазворот при ≤30 конфликтах.

Ключ сопоставления:

участки — по area_name;

наряды — по (area_id, order_number);

пробы — по (order_id, sample_number);

скважины — по (order_id, well_number).

Защита: found / postponed / weightControl — моё true
не сбрасывается при слиянии.

Сравнение двух БД
DbCompareScreen:

4 дерева: inArchive / matched / different / myOnly;

схема дерева: Участок → Наряд → Проба / Скважина;

ключи — те же, что при слиянии;

поиск по активной категории;

авторазворот при ≤30 элементах.

Диагностика БД
DbDiagnosticsDialog — находит четыре типа проблем:

Наряды без участка — orders.area_id → несуществующий
areas.id. Исправление — удалить наряд (каскад уберёт пробы).

Пробы без наряда — samples.order_id → несуществующий
orders.id. Исправление — удалить пробу.

Битые ссылки на фото — запись в sample_images есть, файла
по пути нет. Исправление — удалить запись + сбросить has_photo.

Флаг has_photo = false, но живые файлы на диске есть.
Исправление — установить флаг true.

Перед исправлением — авто-бэкап pre_diagnostics_*.
Файлы фото удаляются после транзакции.
Схема БД не меняется.

Авто-бэкап перед заменой
Перед импортом, откатом, очисткой и диагностикой всегда создаётся
авто-бэкап текущего состояния.

Особенности
Дубликаты номеров проб игнорируются.

«Заменить» при импорте — удаляет все пробы наряда и скважины.

Скважины наряда хранятся отдельно от проб.

Сортировка — по serial_number.

Один наряд = один участок.

Файлы фото лежат в filesDir/sample_photos/, БД хранит путь.

FK CASCADE чистит только БД. Файлы фото удаляются вручную
в DatabaseRepository.

.gsmbackup — zip-архив. Открывается любым архиватором.

Что НЕ хранится в основной БД
Лист Excel, номер строки, сырые заголовки.

Дата отбора, примечания из Excel.

Настройки холостых и шага ВК (живут в ReconciliationState).

Журнал аудита (серия logs)
Отдельная БД logs.db. Файл — filesDir/../databases/logs.db
(через LogsDatabase.getInstance(context)).

Почему отдельная БД: основная БД стирается при clean
и заменяется при restore / rollback / merge — журнал должен
пережить эти операции, включая сам факт их выполнения.

Схема logs.db (version = 1)
Одна таблица — operation_log.

Поле	Тип	Описание
id	Long PK	Автогенерация
created_at	Long	Timestamp
session_id	String	UUID сессии (жизнь процесса)
category	String	app / nav / search / voice / mark / edit / db / error
level	String	info / warn / error
summary	String	Русская фраза для UI
details	String?	JSON с техническими данными
Индексы: created_at, category, level, session_id.

Ротация
10 000 записей. Чистка при вставке (раз в 500 записей).

Файловый архив
Downloads/GeoSampleManager/.logs/YYYY-MM-DD.log — по одному файлу
на день. Дописывается до полуночи. На следующий день — новый файл.
Старые остаются как архив.

Папка .logs (с точкой) скрыта от стандартных файловых менеджеров.
Из UI файлы не удаляются — только через файловый менеджер.

Формат строки:

text
05.10.2026 14:32:05 [ИНФО] [Приложение] Приложение запущено
05.10.2026 14:36:12 [ОШИБКА] [Ошибки] Ошибка чтения бэкапа | {...}
История миграций (основная БД)
v1 → v2 (этап 5.5.1, закрыт)
Причина: заметки и фото.

Изменения:

samples: ADD COLUMN has_photo INTEGER NOT NULL DEFAULT 0.

sample_notes: пересоздана без image_path.

Создана sample_images + индекс по sample_id.

Где: AppDatabase.kt → MIGRATION_1_2.

Будущие миграции (запланированы)
number_in_well в samples — ТД-3.

is_import_error в samples — ТД-4.

Настройки холостых в OrderEntity — ТД-5.

Шаг ВК в OrderEntity — ТД-6.

Примечание: operation_log из плана db-logs реализован
в отдельной БД logs.db (серия logs). Миграция основной БД
2→3 не потребовалась.