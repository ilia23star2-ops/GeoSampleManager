# База данных GeoSample Manager

> Room (SQLite). Здесь — схема, как данные попадают в БД, что может быть
> `null`, и на что обращать внимание.
>
> **Текущая версия БД: 1.**
> **Запланированная миграция 1 → 2** описана в конце файла и в `NEXT_STEPS.md`
> (заход 5.5.1).

---

## Схема БД (version = 1)

5 таблиц. Все связи — через `ForeignKey` с `ON DELETE CASCADE`.
`exportSchema = false`.

### 1. `areas` — участки

| Поле | Тип | Описание |
|---|---|---|
| `id` | Long PK | Автогенерация |
| `area_name` | String | Название («Коптеловский» и т.п.) |
| `created_date` | Long | Timestamp создания |

### 2. `orders` — наряды

| Поле | Тип | Описание |
|---|---|---|
| `id` | Long PK | Автогенерация |
| `area_id` | Long FK → areas.id | CASCADE |
| `order_number` | String | Номер, например «27» |
| `created_date` | Long | Timestamp |

**UNIQUE** по `(area_id, order_number)`.

### 3. `samples` — пробы

| Поле | Тип | Что заполняется |
|---|---|---|
| `id` | Long PK | Автогенерация |
| `order_id` | Long FK → orders.id | Всегда |
| `serial_number` | Int | Порядковый № в листе (1, 2, 3…) |
| `sample_number` | String | Номер пробы из Excel |
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

**UNIQUE** по `(order_id, sample_number)`. Повторный импорт — IGNORE.

### 4. `order_wells` — скважины наряда

| Поле | Тип | Описание |
|---|---|---|
| `id` | Long PK | |
| `order_id` | Long FK → orders.id | CASCADE |
| `well_number` | String | Уникальные номера скважин наряда |

Заполняется автоматически при импорте.

### 5. `sample_notes` — заметки

| Поле | Тип | Описание |
|---|---|---|
| `id` | Long PK | |
| `sample_id` | Long FK → samples.id | CASCADE |
| `note_text` | String? | Текст |
| `image_path` | String? | ⚠️ **Устарело.** Будет удалено в миграции 1 → 2 |
| `created_date` | Long | |

**Пока не заполняется.** Готовится к 5.5.

---

## Путь данных: Excel → БД

### Шаг 1. Пользователь выбирает файл
`AddScreen` → `OpenDocument` → `Uri`.

### Шаг 2. Метаданные
`XlsxReader.readMetadata(openStream)` → `List<SheetMeta>`.

### Шаг 3. Очередь
Фильтр: `rowCount >= 5`. Остальные пропускаются.

### Шаг 4. Для каждого листа

1. **Чтение** — `XlsxReader.readSheet` → `SheetData(name, rows)`.
2. **Анализ** — `ExcelAnalyzer.analyzeSheet` → `SheetAnalysis?`:
   `headerRowIndex`, `headerRowCount`, `mapping: Map<String, Int?>`.
   Роли: `serial`, `well`, `sample`, `int_from`, `int_to`, `weight`,
   `type`, `material`.
3. **Сборка** — `ExcelImporter.buildOrder`:
   - `SampleFilter.classify` → KEEP / SKIP_BLANK / SKIP_EMPTY.
   - Из каждой строки читаются поля.
   - `SampleFilter.classifyTypeAndStatus` → `(sampleType, status)`.
     Приоритет: текст типа → префикс скважины → `auger`.
     `status = "control"` при импорте **не** ставится.
   - `AreaResolver.resolve(wellsSet, settings)` → участок.
   - `OrderNumberExtractor.extract(...)` → номер наряда.
4. **Предпросмотр** — диалог (`ImportPreviewDialog`).
5. **Запись** — `AddViewModel.doImportToDb`:
   - Конфликт с существующим нарядом → диалог «Добавить / Пропустить / Заменить».
   - `getAreaId` / `addArea`.
   - `getOrderId` / `addOrder`.
   - Для каждой `ParsedSample` → `SampleEntity` + `repo.addSample`.
     Дубликаты по `(order_id, sample_number)` игнорируются.
   - Уникальные `wellNumber` → `order_wells`.

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
| `control` | Весовой контроль | **Вручную** в сверке |

---

## Атомарные UPDATE в `SampleDao`

Используются для одиночных действий на экране сверки (без чтения строки):
setFound, setPostponed, setControlWeight, setWeight,
setWeightControl, setStatus, setHasNote, setSampleType,
setMaterialDesc, setSampleNumber, setWellNumber, setInterval,
toggleFound, updateAll

## Методы `DatabaseRepository` для сверки
setSampleStatus, setHasNote, setWeight, setWeightControl,
saveRows (батч в транзакции),
deleteSampleWithRenumber (удаление + пересчёт номеров в транзакции),
deleteWellsForOrder, upsertNote, getNote

---

## Особенности

1. **Дубликаты номеров** проб игнорируются (UNIQUE + IGNORE).
2. **«Заменить»** при импорте — удаляет все пробы наряда и скважины,
   заливает новые.
3. **Скважины наряда** хранятся отдельно от проб.
4. **Сортировка** — по `serial_number`, а не по `sample_number`.
5. **Один наряд = один участок.** Одна проба = один наряд.
6. **`has_note`** сейчас обновляется вручную (см. `ReconciliationViewModel`).
   После 5.5 — синхронизируется в `DatabaseRepository`.

---

## Что НЕ хранится в БД

- Лист Excel, номер строки, сырые заголовки.
- Дата отбора, примечания из Excel.
- Настройки холостых и шага ВК по наряду (живут в `ReconciliationState`).

---

## ⚠️ Миграция 1 → 2 (запланирована, заход 5.5.1)

**Причина:** добавить фото к пробам.

### Изменения

**`SampleEntity`:** добавить `has_photo: Boolean = false`.

**`SampleNoteEntity`:** убрать `image_path`.
Оставить `id`, `sample_id`, `note_text`, `created_date`.

**Новая таблица `sample_images`:**


CREATE TABLE IF NOT EXISTS sample_images (
    id           INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    sample_id    INTEGER NOT NULL,
    image_path   TEXT    NOT NULL,
    created_date INTEGER NOT NULL,
    FOREIGN KEY(sample_id) REFERENCES samples(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS index_sample_images_sample_id
    ON sample_images(sample_id);
SQL миграции
ALTER TABLE samples ADD COLUMN has_photo INTEGER NOT NULL DEFAULT 0;

-- sample_notes: пересоздание безопаснее, чем DROP COLUMN
CREATE TABLE sample_notes_new (
    id           INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    sample_id    INTEGER NOT NULL,
    note_text    TEXT,
    created_date INTEGER NOT NULL,
    FOREIGN KEY(sample_id) REFERENCES samples(id) ON DELETE CASCADE
);
INSERT INTO sample_notes_new (id, sample_id, note_text, created_date)
    SELECT id, sample_id, note_text, created_date FROM sample_notes;
DROP TABLE sample_notes;
ALTER TABLE sample_notes_new RENAME TO sample_notes;
CREATE INDEX IF NOT EXISTS index_sample_notes_sample_id
    ON sample_notes(sample_id);

CREATE TABLE IF NOT EXISTS sample_images (...);
CREATE INDEX IF NOT EXISTS ...;
Важно: файлы фото на диске
FK CASCADE удалит строки sample_images, но не файлы в
filesDir/sample_photos/. Нужно добавить явную очистку в:

DatabaseRepository.deleteSample,

DatabaseRepository.deleteSampleWithRenumber,

DatabaseRepository.clearOrder.

Перед удалением — собрать пути через getPhotosForSample, затем удалить
файлы через PhotoStorage.delete(path).

Как читать данные для сверки
Примеры (реализовано в SampleDao):

getSamplesForOrder(orderId) → Flow.

getSamplesForOrderList(orderId) → suspend.

getSampleById(sampleId).

getSamplesByWell(wellNumber).

searchSamples(query).

Строка пробы: sampleNumber, wellNumber, intervalFrom/To,
weight, controlWeight, sampleType + status, found, postponed,
weightControl.

Статистика: считается в UI (calculateOverallStats) — SQL пока не нужен.