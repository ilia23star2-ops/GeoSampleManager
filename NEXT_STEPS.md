# План следующих заходов

> Обновляется после каждого завершённого захода.
> **Актуальный этап — 5.5. Заметки и фото.**

---

## Текущий этап — 5.5. Заметки и фото

### Принятые решения

1. **Несколько фото на пробу.**
2. **Заметка и фото независимы** — текст без фото, фото без текста, оба,
   ни одного.
3. Файлы — **внутреннее хранилище**: `filesDir/sample_photos/`.
4. **Сжатие:** до 1024 px по длинной стороне, JPEG 80%.
5. **Съёмка:** `ActivityResultContracts.TakePicture()` + `FileProvider`.
6. **Галерея:** `ActivityResultContracts.PickVisualMedia()`.
7. **UI:** иконки заметки и фото в строке + пункты в меню 3 точки.
8. **Разрешения:** явные не нужны, но в манифест — `CAMERA` и `FileProvider`.
9. **Один диалог** на заметку и фото.
10. Флаги `has_note` и `has_photo` в `SampleEntity` синхронизируются.

---

## Заход 5.5.1 — Инфраструктура (🟡 в работе)

**Цель:** подготовить всё, что нужно для 5.5.2 — чтобы диалог можно было
писать «поверх готового».

### Файлы

**Новые:**
- `app/src/main/res/xml/file_paths.xml` — пути для FileProvider.
- `app/src/main/java/com/example/geosamplemanager/data/util/PhotoStorage.kt`
  — утилита: временный файл, `Uri` для камеры, сжатие, сохранение,
  удаление.
- `app/src/main/java/com/example/geosamplemanager/data/entity/SampleImageEntity.kt`
  — таблица `sample_images`.

**Изменяются:**
- `app/src/main/AndroidManifest.xml` — `FileProvider` + `CAMERA`.
- `app/src/main/java/com/example/geosamplemanager/data/entity/SampleEntity.kt`
  — `has_photo: Boolean = false`.
- `app/src/main/java/com/example/geosamplemanager/data/entity/SampleNoteEntity.kt`
  — убрать `image_path`.
- `app/src/main/java/com/example/geosamplemanager/data/dao/SampleNoteDao.kt`
  — без изменений в интерфейсе, но добавим `SampleImageDao`.
- `app/src/main/java/com/example/geosamplemanager/data/dao/SampleImageDao.kt`
  — новый DAO.
- `app/src/main/java/com/example/geosamplemanager/data/AppDatabase.kt`
  — version 1 → 2, регистрация `SampleImageEntity`, `Migration(1, 2)`.
- `app/src/main/java/com/example/geosamplemanager/data/DatabaseRepository.kt`
  — методы `addPhoto`, `deletePhoto`, `getPhotosForSample`,
  `getNoteWithPhotos`, синхронизация флагов.

### Изменения БД (миграция 1 → 2)
**`SampleEntity`:**
```kotlin
@ColumnInfo(name = "has_photo")
val hasPhoto: Boolean = false
SampleNoteEntity:
Убрать image_path.
Оставить: id, sample_id, note_text, created_date.
Новая таблица sample_images:
CREATE TABLE sample_images (
    id           INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    sample_id    INTEGER NOT NULL,
    image_path   TEXT    NOT NULL,
    created_date INTEGER NOT NULL,
    FOREIGN KEY(sample_id) REFERENCES samples(id) ON DELETE CASCADE
);
CREATE INDEX index_sample_images_sample_id ON sample_images(sample_id);
Миграция 1 → 2:
ALTER TABLE samples ADD COLUMN has_photo INTEGER NOT NULL DEFAULT 0;
ALTER TABLE sample_notes DROP COLUMN image_path;   -- если SQLite >= 3.35
-- иначе — таблицу sample_notes пересоздать через CREATE/INSERT/DROP/RENAME
CREATE TABLE sample_images (...);                  -- см. выше
CREATE INDEX ...;
Примечание: SQLite в Android 34 поддерживает DROP COLUMN, но для
надёжности лучше пересоздать sample_notes — старая схема имела image_path,
данных там всё равно нет.

Методы репозитория
suspend fun addPhoto(sampleId: Long, imagePath: String): Long
suspend fun deletePhoto(imageId: Long): Boolean  // + удалить файл
suspend fun getPhotosForSample(sampleId: Long): List<SampleImageEntity>
suspend fun getNoteWithPhotos(sampleId: Long): Pair<SampleNoteEntity?, List<SampleImageEntity>>
suspend fun syncHasNoteAndPhoto(sampleId: Long)  // обновить флаги
Проверка захода
□ Сборка без ошибок.
□ Миграция 1 → 2 проходит без потери данных (или с явным подтверждением).
□ PhotoStorage вручную покрыт: создание временного файла, сжатие
существующего фото, удаление.
□ FileProvider в манифесте с корректным authorities.
□ sample_images видна в AppDatabase.
Заход 5.5.2 — Диалог заметки и фото (⬜)
NotePhotoDialog в ReconciliationDialogs.kt.

Кнопки: «Сделать фото», «Из галереи», «Удалить фото», «Сохранить».

Превью — LazyRow с миниатюрами, тап — полноэкранный просмотр.

rememberLauncherForActivityResult для TakePicture и PickVisualMedia.

Подключить к ReconciliationViewModel:

saveNote(sampleId, text)

addPhotoToSample(sampleId, uri)

deletePhoto(imageId)

Заменить заглушку в SearchScreen.kt (onSave → реальное сохранение).

Обновить ReconciliationViewModel + ReconciliationState (методы,
затрагивающие hasNote / hasPhoto).

Заход 5.5.3 — Отображение и синхронизация (⬜)
В SampleRow (UI) — photos: List<String> (пути или Uri).

В ReconciliationMapper.toRow() — читать hasPhoto из SampleEntity,
а не ставить false.

Иконки в строке (EditNote, PhotoCamera) — реальные точки входа
в диалог.

Проверить синхронизацию: сохранение/удаление заметки и фото
корректно меняет has_note / has_photo.

Открытые вопросы (решаются по ходу)
number_in_well — вычислять в UI (сейчас) или хранить в БД?
Решение отложено. При следующей миграции можно добавить.

SQL-статистика — если UI-подсчёт начнёт тормозить на 300+ пробах.

Автоматическая простановка ВК — при импорте, каждая N-я рядовая проба
(холостые пропускаются). Настройка N — на наряд. Отдельный этап после 5.8.

Сохранение настроек холостых в БД — сейчас живут в
ReconciliationState. Нужно ли переживать перезапуск приложения?

Флаги has_note / has_photo — согласованность с таблицами
sample_notes / sample_images (закроется в 5.5.3).

Логика «Ошибка импорта» — сейчас эвристика
sample_number == well_number. Стоит добавить флаг is_import_error
в SampleEntity при следующей миграции (ТД-4).

Файлы фото при удалении — при DELETE FROM samples FK CASCADE
удалит строки в sample_images, но файлы на диске останутся.
Нужно добавить очистку в DatabaseRepository.deleteSample,
deleteSampleWithRenumber, clearOrder. См. DATABASE.md.

Следующие этапы (кратко)
5.8 — Голосовой помощник
Вариант А — микрофон на экране сверки. Компоненты:
SpeechRecognizer, TextToSpeech, Bluetooth-роутинг.
Разрешения: RECORD_AUDIO, BLUETOOTH_CONNECT (Android 12+).

Сценарии:

«Скважина 123» → TTS: «Скважина 123, наряд 5, N проб».

«Первая» / «Один» → отметка первой пробы.

«Взвесьте» → диалог веса ВК.

Отложенная → TTS: «Отложена. Заметка?».

Все отмечены → TTS: «Всё отмечено».

5.10 — Полировка
Тёмная тема (системная + ручное переключение).

Адаптивность под телефон.

Haptic feedback на чек-боксы.

Крупные элементы под перчатки.

Сохранение позиции скролла.

Обработка отсутствия прав на камеру/микрофон.