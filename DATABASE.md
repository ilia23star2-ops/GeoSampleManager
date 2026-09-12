# Как данные попадают в БД

> Проект использует **Room (SQLite)**. Здесь описано, что хранится, как
> заполняется, что может быть `null`, и что важно учитывать при отображении.

---

## Схема БД

5 таблиц. Все с префиксами и связями через `ForeignKey` с каскадным удалением.
Версия БД — **1** (пока миграций не делали).

### 1. `areas` — участки

| Поле | Тип | Описание |
|---|---|---|
| `id` | Long PK | Автогенерация |
| `area_name` | String UNIQUE | Название, например «Коптеловский» |
| `created_date` | Long | Timestamp создания |

### 2. `orders` — наряды

| Поле | Тип | Описание |
|---|---|---|
| `id` | Long PK | Автогенерация |
| `area_id` | Long FK → areas.id | Каскадное удаление |
| `order_number` | String | Номер, например «27» |
| `created_date` | Long | Timestamp |

**UNIQUE** по паре `(area_id, order_number)`.

### 3. `samples` — пробы

| Поле | Тип | Что реально заполняется |
|---|---|---|
| `id` | Long PK | Автогенерация |
| `order_id` | Long FK → orders.id | Всегда |
| `serial_number` | Int | Порядковый номер в листе (1, 2, 3…) |
| `sample_number` | String | Номер пробы из Excel, например «KPD109003101» |
| `well_number` | String | Номер скважины или выработки |
| `workings` | String? | **Всегда null** (задел на будущее) |
| `interval_from` | Double? | Число или null |
| `interval_to` | Double? | Число или null |
| `weight` | Double? | Основной вес, число или null |
| `control_weight` | Double? | Вес весового контроля |
| `actual_weight` | Double? | **Всегда null** (задел) |
| `sample_type` | String | `auger` / `channel` / `cobra` / `duplicate` |
| `status` | String | `normal` / `blank` / `control` |
| `reserved_type` | String? | **Всегда null** |
| `material_desc` | String? | Характеристика материала или null |
| `found` | Boolean | Всегда `false` при импорте |
| `weight_control` | Boolean | Всегда `false` при импорте |
| `postponed` | Boolean | Всегда `false` при импорте |
| `has_note` | Boolean | Всегда `false` при импорте |

**UNIQUE** по паре `(order_id, sample_number)`.
Повторный импорт — дубликаты **игнорируются** (Room `OnConflictStrategy.IGNORE`).

### 4. `order_wells` — скважины наряда

| Поле | Тип | Описание |
|---|---|---|
| `id` | Long PK | Автогенерация |
| `order_id` | Long FK → orders.id | |
| `well_number` | String | Уникальные номера скважин наряда |

Заполняется автоматически при импорте: собираются все уникальные `well_number`
из проб.

### 5. `sample_notes` — заметки

| Поле | Тип | Описание |
|---|---|---|
| `id` | Long PK | |
| `sample_id` | Long FK → samples.id | ON DELETE CASCADE |
| `note_text` | String? | Текст заметки |
| `image_path` | String? | Путь к фото (одно) |
| `created_date` | Long | |

**Пока не заполняется** — задел под этап 5.5 (заметки и фото).

---

## Полный путь данных: Excel → БД

### Шаг 1. Пользователь выбирает файл
`AddScreen` → `rememberLauncherForActivityResult` → получает `Uri`.

### Шаг 2. Читаем метаданные
`XlsxReader.readMetadata(openStream)` → список `SheetMeta(name, path, rowCount)`.

### Шаг 3. Определяем очередь
Фильтруем листы с `rowCount >= 5`. Остальные пропускаем.

### Шаг 4. Для каждого листа

#### 4.1. Чтение
`XlsxReader.readSheet(openStream, path, name)` → `SheetData(name, rows)`.
`rows` — `List<List<String>>`.

#### 4.2. Анализ шапки и колонок
`ExcelAnalyzer.analyzeSheet(sheet, settings)` → `SheetAnalysis?`:
- `headerRowIndex` — с какой строки шапка.
- `headerRowCount` — 1, 2 или 3 строки шапки.
- `mapping` — `Map<String, Int?>`: роль → индекс колонки.
- Роли: `serial`, `well`, `sample`, `int_from`, `int_to`, `weight`,
  `type`, `material`.

#### 4.3. Сборка наряда
`ExcelImporter.buildOrder(analysis, fileName, totalSheets, settings, fallback)`.

**a) Фильтрация строк** через `SampleFilter.classify()`:
- `KEEP` — оставляем.
- `SKIP_BLANK` — бланк или стандартный образец без данных, пропускаем.
- `SKIP_EMPTY` — пустая строка, пропускаем.

**b) Для каждой оставленной строки** читаются: `well_number`,
`sample_number`, `intervalFrom`, `intervalTo`, `weight`, `materialDesc`.
Затем `SampleFilter.classifyTypeAndStatus()` → `(sampleType, status)`.

**c) Определение типа пробы (`sample_type`):**

Приоритеты:
1. Слово-маркер из колонки `type`:
    - «холост» / «blank» → `sampleType = "auger"`, `status = "blank"`.
    - «борозд» / «канав» → `sampleType = "channel"`.
    - «кобра» → `sampleType = "cobra"`.
    - «шнек» → `sampleType = "auger"`.
2. Если по тексту не сработало — по префиксу номера скважины:
    - `KPD`, `KOP` → `auger`.
    - `KBK`, `KMB` → `channel`.
    - `ACD` → `cobra`.
3. Иначе — `auger` по умолчанию.

**d) Определение статуса (`status`):**
- Если в тексте типа есть «холост» → `status = "blank"`.
- Иначе → `status = "normal"`.

**`status = "control"`** при импорте не выставляется. ВК отмечает пользователь
**вручную** в экране сверки.

**e) Определение участка:**
`AreaResolver.resolve(wellsSet, settings) → String?` — берёт множество
`wellNumber`, извлекает префиксы, сопоставляет с настройками `areaPrefixes`.
Если `null` — пользователь вводит вручную.

**f) Определение наряда:**
`OrderNumberExtractor.extract(...) → String`:
- AUTO: имя файла → имя листа → fallback.
- Правило «последнее число»: `02-КОПТ00027` → `27`.

#### 4.4. Предпросмотр
Диалог: плашки предупреждений, участок, наряд, определённые колонки,
первые 10 проб, кнопки «Пропустить», «Импортировать», «Авто для остальных».

#### 4.5. Импорт в БД
`doImportToDb(order, areaName, orderNumber, settings)`:
1. Проверка конфликта с существующим нарядом → диалог
   «Добавить / Пропустить / Заменить».
2. `getAreaId` / `addArea` → `areaId`.
3. `getOrderId` / `addOrder` → `orderId`.
4. Для каждой `ParsedSample` — `SampleEntity` + `repo.addSample(entity)`.
5. Множество уникальных `wellNumber` → `repo.addWell(orderId, w)`.

**Ключевой момент:** если `sample_number` в рамках `order_id` уже существует —
Room молча пропускает вставку (IGNORE).

---

## Значения полей в БД → UI

### `sample_type`

| Код в БД | Название в UI |
|---|---|
| `auger` | Шнековая |
| `channel` | Бороздовая |
| `cobra` | Кобра |
| `duplicate` | Дубликат |

### `status`

| Код в БД | Название в UI | Когда выставляется |
|---|---|---|
| `normal` | Обычная | При импорте по умолчанию |
| `blank` | Холостая | Если «холост» в типе или нет интервала/веса |
| `control` | Весовой контроль | **Вручную** |

### Гарантированно заполнено в `samples`

`order_id`, `serial_number`, `sample_number`, `well_number`, `sample_type`,
`status`, `found = false`, `weight_control = false`, `postponed = false`,
`has_note = false`.

### Может быть null

`interval_from`, `interval_to`, `weight`, `material_desc`.

### Всегда null на текущем этапе

`workings`, `control_weight`, `actual_weight`, `reserved_type`.

---

## Особенности

1. **Дубликаты номеров проб** — UNIQUE по `(order_id, sample_number)`.
   Повторный импорт с «Добавить» не создаст дубликатов.
2. **Действие «Заменить»** удаляет все пробы наряда и его скважины,
   затем заливает новые.
3. **Скважины наряда** хранятся отдельно от проб.
4. **Порядок сортировки проб** — по `serial_number`, а не по `sample_number`.
5. **Один наряд = один участок.**
6. **Одна проба = один наряд.**

---

## Что НЕ хранится в БД

- Информация о листе Excel, откуда пришла проба.
- Номер строки в Excel.
- Сырые заголовки колонок.
- Дата отбора пробы.
- Примечания из Excel.

Если эти данные нужны — расширять `samples` миграцией.

---

## Как читать данные для «Сверки и поиска»

Примеры запросов (реализовано в `SampleDao`):

- `getSamplesForOrder(orderId)` → Flow-список проб наряда.
- `getSamplesForOrderList(orderId)` → suspend-список.
- `getSampleById(sampleId)` → одна проба.
- `getSamplesByWell(wellNumber)` → все пробы скважины.
- `searchSamples(query)` → поиск по `sample_number` или `well_number`.

**Для отображения строки** берём:
- `sampleNumber` → «№ пробы»
- `wellNumber` → «Скважина / Выработка»
- `intervalFrom`, `intervalTo` → «От / До» (если null — «—»)
- `weight` → «Вес»
- `controlWeight` → «(ВК)»
- `sampleType` + `status` → человекочитаемый тип
- `found` → галка
- `postponed` → «Отложена»
- `weightControl` → «ВК»

**Статистика:** считается в UI (`calculateOverallStats`), SQL-запросы
добавим позже, если появится необходимость.

---

## Расширения, добавленные на этапе 5.9 (Room)

**В `SampleDao`:**
- Атомарные UPDATE без чтения: `setWeight`, `setWeightControl`, `setStatus`,
  `setHasNote`, `setSampleType`, `setMaterialDesc`, `setSampleNumber`,
  `setWellNumber`, `setInterval`.
- Батч-обновление: `updateAll(samples)`.

**В `DatabaseRepository`:**
- `setWeight`, `setWeightControl`, `setSampleStatus`, `setHasNote`.
- `saveRows(rows)` — батч-обновление в одной транзакции.
- `deleteSampleWithRenumber(sampleId, recalc)` — удаление с пересчётом
  номеров в скважине (в одной транзакции).
- `deleteWellsForOrder`, `upsertNote`.

---

## Что планируется изменить (см. `NEXT_STEPS.md`)

**Этап 5.5 — заметки и фото:**
- В `SampleEntity` добавить `has_photo: Boolean = false`.
- В `SampleNoteEntity` убрать `image_path`.
- Новая таблица `sample_images`:
  `id, sample_id, image_path, created_date`.
- Версия БД 1 → 2, миграция или `fallbackToDestructiveMigration`.

**Открытые вопросы:**
- Хранить ли настройки холостых в `OrderEntity` (сейчас они в памяти
  `ReconciliationState`).
- Добавить ли поле `number_in_well` вместо вычисления в UI.
- Добавить ли флаг `is_import_error` вместо эвристики
  «`sample_number == well_number`».