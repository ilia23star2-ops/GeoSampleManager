# База данных GeoSample Manager

> Room (SQLite). Схема, путь данных, что может быть `null`.
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

⚠️ **В v1 было поле `image_path`** — в v2 удалено (миграция пересоздала
таблицу).

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
    - `SampleFilter.classifyTypeAndStatus` → `(sampleType, status)`.
    - `AreaResolver.resolve` → участок.
    - `OrderNumberExtractor.extract` → номер наряда.
4. **Предпросмотр** — диалог.
5. **Запись** — `AddViewModel.doImportToDb`:
    - Конфликт → диалог «Добавить / Пропустить / Заменить».
    - `getAreaId` / `addArea`, `getOrderId` / `addOrder`.
    - Для каждой `ParsedSample` → `SampleEntity` + `repo.addSample`.
    - Уникальные `wellNumber` → `order_wells`.

---

## Путь данных: заметки и фото (v2)

### Заметка
1. Пользователь открывает `NotePhotoDialog` из строки пробы.
2. `ReconciliationViewModel.loadNoteWithPhotos(sampleId)` → `(Note, [Photos])`.
3. Ввод текста → «Сохранить» → `saveNoteText(sampleId, text)`:
    - пустой текст → `repo.deleteNote(sampleId)`;
    - иначе → `repo.upsertNote(note)`;
    - потом `repo.syncHasNoteAndPhoto(sampleId)`.

### Фото
1. «Сделать фото» → `TakePicture` → временный файл в `filesDir/sample_photos/`.
2. «Из галереи» → `PickVisualMedia` → `Uri`.
3. `PhotoStorage.compressAndSave` / `compressAndSaveFromFile`:
    - декодирование, скейл до 1024 px, JPEG 80%, сохранение под финальным
      именем в `filesDir/sample_photos/`.
4. `repo.addPhoto(sampleId, path)` — запись в `sample_images`,
   обновление `has_photo`.
5. Удаление: `repo.deletePhoto(imageId, sampleId)`:
    - удаление записи из БД,
    - удаление файла с диска,
    - пересчёт `has_photo`.

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

---

## Особенности

1. **Дубликаты номеров** проб игнорируются.
2. **«Заменить»** при импорте — удаляет все пробы наряда и скважины.
3. **Скважины наряда** хранятся отдельно от проб.
4. **Сортировка** — по `serial_number`.
5. **Один наряд = один участок.**
6. **Файлы фото** лежат в `filesDir/sample_photos/`, БД хранит только путь.
7. **FK CASCADE чистит только БД.** Файлы фото удаляются вручную в
   `DatabaseRepository` (`clearOrder`, `deleteSampleWithRenumber`,
   `deletePhoto`).

---

## Что НЕ хранится в БД

- Лист Excel, номер строки, сырые заголовки.
- Дата отбора, примечания из Excel.
- Настройки холостых и шага ВК по наряду (живут в `ReconciliationState`).

---

## История миграций

### v1 → v2 (этап 5.5.1, закрыт)

**Причина:** заметки и фото.

**Изменения:**
1. `samples`: `ADD COLUMN has_photo INTEGER NOT NULL DEFAULT 0`.
2. `sample_notes`: пересоздана без `image_path`.
3. Создана `sample_images` + индекс по `sample_id`.

**Где:**
`AppDatabase.kt` → `MIGRATION_1_2`, подключена через
`.addMigrations(MIGRATION_1_2)`.

### Будущие миграции (запланированы)

- `number_in_well` в `samples` — ТД-3.
- `is_import_error` в `samples` — ТД-4.
- Настройки холостых в `OrderEntity` — ТД-5.
- Шаг ВК в `OrderEntity` — ТД-6.