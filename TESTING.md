# TESTING.md — что и как тестировать

> JUnit 4.13.2. Тесты в `app/src/test/java/com/example/geosamplemanager/`.
> Имена тестов — **только латиница** (backtick-кириллица ломает Gradle).

---

## Принцип

**Тестируем критичное, что легко сломать молча.**

Не всё подряд. Только:
- Парсеры (Excel, голос, числа).
- Поиск (5 уровней, fuzzy).
- Миграции БД.
- Логику undo/redo.

UI не тестируем — проверяем руками.

---

## Что тестируется

### `data/voice/VoiceNumberParserTest.kt` ✅ (12 тестов)

| Тест | Что проверяет |
|---|---|
| `parse blocks 124` | «сто двадцать четыре» → `124` |
| `parse nulls 00` | «ноль ноль три» → `003` |
| `parse thousand` | «тысяча пятьсот шестьдесят два» → `1562` |
| `parse thousand split` | «тысяча пятьсот сто шестьдесят два» → `1500162` |
| `parse fifty one` | «пятьдесят один» → `51` |
| `parse fifty and one` | «пятьдесят и один» → `50|1` |
| `parse two nulls` | «два нуля» → `00` |
| `parse nine eleven` | «девятьсот одиннадцать» → `911` |

### `data/voice/VoicePrefixResolverTest.kt` ✅ (14 тестов)

| Тест | Что проверяет |
|---|---|
| `resolve KPD` | «капэдэ» → `KPD` |
| `resolve NV` | «энвэ» → `NV` |
| `resolve NVD` | «энвэдэ» → `NVD` |
| `resolve from DB settings` | Префиксы из `ImportSettings` |

### `data/voice/VoiceCommandParserTest.kt` ✅ (12 тестов)

| Тест | Что проверяет |
|---|---|
| `ordinal first` | «первая» → `MarkOrdinal(1)` |
| `ordinal 21` | «двадцать первая» → `MarkOrdinal(21)` |
| `clear first` | «снять первую» → `ClearOrdinal(1)` |
| `clear all` | «снять все» → `ClearAll` |
| `weight 2.5` | «вес два пять» → `SetWeight(2.5)` |
| `search 1524` | «1524» → `Search("1524")` |
| `sort 2 numbers` | «1524 и 1525» → `Sort([...])` |
| `stop` | «стоп» → `Stop` |

---

## Что ДОЛЖНО появиться в тестах

### `data/voice/VoiceSearchTest.kt` (план, 5.8.5)

Проверяет 5 уровней поиска:
- Точное совпадение.
- Совпадение по `well_number`.
- Нормализация нулей.
- Структурное (`well + suffix`).
- Fuzzy (разница ≤1).

### `data/AppDatabaseTest.kt` (план, 5.9)

Проверяет миграции:
- 1→2 проходит без потерь.
- После миграции все таблицы на месте.
- FK CASCADE работает.

### `ui/ReconciliationStateTest.kt` (план, 5.9)

Проверяет undo/redo:
- 20 шагов в стеке.
- Undo восстанавливает состояние.
- Redo после undo.
- Новое действие очищает redo.

---

## Как запускать

### В Android Studio

- ПКМ по папке `app/src/test/` → Run 'Tests in …'.

### В терминале
./gradlew test

text

Отчёт: `app/build/reports/tests/testDebugUnitTest/index.html`.

---

## Правила написания тестов

1. **Имя теста — латиница.** `parseThousand`, не `парситТысячу`.
2. **Ассерты — простые.** `assertEquals(expected, actual)`.
3. **Один тест — одна проверка.** Не смешивать.
4. **Не тестировать UI.** Compose-тесты — отдельная тема, не сейчас.
5. **Не тестировать БД целиком.** Только миграции.
6. **Тест должен проходить за <100 мс.** Если дольше — что-то не так.

---

## Что делать при падении теста

1. **Не удалять тест.** Найти причину.
2. **Если причина — правка в коде**, проверить, что правка правильна.
3. **Если правка неправильна** — откатить (см. `AI_RULES.md` §19).
4. **Если тест устарел** — обновить тест с пояснением.

---

## Приоритет написания

| Приоритет | Что |
|---|---|
| 🔴 Сейчас | `VoiceNumberParser`, `VoicePrefixResolver`, `VoiceCommandParser` (уже есть) |
| 🟡 При 5.8.5 | `VoiceSearchTest` |
| 🟡 При 5.9 | `AppDatabaseTest`, `ReconciliationStateTest` |
| 🟢 При 5.10 | Дополнить покрытие по необходимости |
