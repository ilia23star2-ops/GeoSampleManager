# База данных GeoSample Manager

> Room (SQLite). Схема, путь данных, что может быть `null`, бэкапы.
>
> **Текущая версия БД: 2.**

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

**Одна заметка на пробу.** Фото хранятся отдельно.

⚠️ **В v1 было поле `image_path`** — в v2 удалено.

### 6. `sample_images` — фото пробы (**новая в v2**)

| Поле | Тип | Описание |
|---|---|---|
| `id` | Long PK | |
| `sample_id` | Long FK → samples.id | CASCADE |
| `image_path` | String | Абсолютный путь в `filesDir/sample_photos/` |
| `created_date` | Long | Timestamp |

**Индекс** по `sample_id`.

Одна проба — 0..N фото. Заметка и фото независимы.

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
2. **Анализ** — `ExcelAnalyzer.analyzeSheet` → `SheetAnalysis?`.
3. **Сборка** — `ExcelImporter.buildOrder`:
   - `SampleFilter.classify` → KEEP / SKIP_BLANK / SKIP_EMPTY.
   - `SampleFilter.classifyTypeAndStatus`.
   - `AreaResolver.resolve`, `OrderNumberExtractor.extract`.
4. **Предпросмотр** — диалог.
5. **Запись** — `AddViewModel.doImportToDb`:
   - Конфликт → диалог «Добавить / Пропустить / Заменить».
   - Для каждой `ParsedSample` → `SampleEntity` + `repo.addSample`.
   - Уникальные `wellNumber` → `order_wells`.

---

## Путь данных: заметки и фото (v2)

### Заметка
1. `NotePhotoDialog` из строки пробы.
2. `loadNoteWithPhotos(sampleId)`.
3. «Сохранить» → `saveNoteText` → `upsertNote` или `deleteNote` →
   `syncHasNoteAndPhoto`.

### Фото
1. «Сделать фото» / «Из галереи».
2. `PhotoStorage.compressAndSave` — скейл до 1024 px, JPEG 80%.
3. `repo.addPhoto(sampleId, path)` — запись в `sample_images`,
   обновление `has_photo`.
4. Удаление: `repo.deletePhoto` — БД + файл + пересчёт `has_photo`.

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

**Сверка:**
`setSampleStatus`, `setHasNote`, `setWeight`, `setWeightControl`,
`saveRows`, `deleteSampleWithRenumber`, `deleteWellsForOrder`,
`upsertNote`, `getNote`.

**Заметки и фото (v2):**
`getPhotosForSample`, `getImagePathsForSample`, `addPhoto`, `deletePhoto`,
`getNoteWithPhotos`, `syncHasNoteAndPhoto`.

**Бэкапы и восстановление:**
`checkpointWal`, `getDatabaseFile`, `getPhotosDir`,
`getRollbackBackupsDir`, `getDbInfo`, `clearOrder`, `clearAllData`.

---

## Бэкапы и авто-бэкапы

### Формат `.gsmbackup`

Zip-архив:
- `manifest.json` — метаданные.
- `geosamples.db` — сама БД (SQLite).
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
format_version — версия формата. Несовместимая — импорт блокируется.

db_schema_version — версия схемы. Несовпадение — предупреждение.

operation — restore / rollback / clean / export / unknown.

Структура папок в Загрузках
text
Загрузки/GeoSampleManager/
  pre_restore/    — авто-бэкапы перед импортом
  pre_rollback/   — авто-бэкапы перед откатом
  pre_clean/      — авто-бэкапы перед очисткой
  exports/        — пользовательские экспорты
Приватно те же файлы (кроме exports/) лежат в
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

при открытии диалога «Откат» (публичные, вручную).

Импорт
DbImportPickerDialog показывает только файлы из exports/ +
SAF-выбор. Авто-бэкапы pre_* импортируются только через диалог
«Откат».

Откат
DbRollbackDialog объединяет приватные и публичные pre_*.
Дедупликация по имени файла, приоритет приватного. Метка источника
в строке: «Внутренний» / «Загрузки».

Авто-бэкап перед заменой
Перед импортом, откатом, очисткой всегда создаётся авто-бэкап
текущего состояния. Если что-то пойдёт не так — есть точка возврата.

Особенности
Дубликаты номеров проб игнорируются.

«Заменить» при импорте — удаляет все пробы наряда и скважины.

Скважины наряда хранятся отдельно от проб.

Сортировка — по serial_number.

Один наряд = один участок.

Файлы фото лежат в filesDir/sample_photos/, БД хранит
только путь.

FK CASCADE чистит только БД. Файлы фото удаляются вручную
в DatabaseRepository.

.gsmbackup — zip-архив. Открывается любым архиватором.

Что НЕ хранится в БД
Лист Excel, номер строки, сырые заголовки.

Дата отбора, примечания из Excel.

Настройки холостых и шага ВК по наряду (живут в ReconciliationState).

История миграций
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

operation_log — серия db-logs (миграция 2→3).