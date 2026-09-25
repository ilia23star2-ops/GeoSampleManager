package com.example.geosamplemanager.data.voice

/**
 * Произношения латинских букв для распознавания буквенных префиксов.
 *
 * Пример: «капэдэ» → K + P + D → «KPD».
 *
 * Разные варианты одной буквы — отдельные записи. При парсинге берётся
 * самое длинное совпадение с начала строки.
 *
 * FIX 5.8.11-e4-prefix-1:
 * W: «дабл-ю» → «даблю» — синхронизация с VoiceSpeaker (TTS).
 * TTS произносит «даблю», Vosk учит «даблю» — совпадает.
 */
object VoiceLetterSounds {

    val sounds: Map<String, Char> = buildMap {
        // A
        put("эй", 'A')
        put("а", 'A')
        // B
        put("би", 'B')
        put("бэ", 'B')
        // C
        put("си", 'C')
        put("цэ", 'C')
        // D
        put("ди", 'D')
        put("дэ", 'D')
        // E
        put("и", 'E')
        put("е", 'E')
        // F
        put("эф", 'F')
        // G
        put("джи", 'G')
        put("жи", 'G')
        put("гэ", 'G')
        // H
        put("эйч", 'H')
        put("аш", 'H')
        // I
        put("ай", 'I')
        // J
        put("джей", 'J')
        put("жэй", 'J')
        // K
        put("кей", 'K')
        put("ка", 'K')
        // L
        put("эль", 'L')
        put("эл", 'L')
        put("лэ", 'L')
        // M
        put("эм", 'M')
        put("мэ", 'M')
        // N
        put("эн", 'N')
        put("нэ", 'N')
        // O
        put("оу", 'O')
        put("о", 'O')
        // P
        put("пи", 'P')
        put("пэ", 'P')
        // Q
        put("кью", 'Q')
        put("ку", 'Q')
        // R
        put("эр", 'R')
        put("ар", 'R')
        put("рэ", 'R')
        // S
        put("эс", 'S')
        put("сэ", 'S')
        // T
        put("ти", 'T')
        put("тэ", 'T')
        // U
        put("ю", 'U')
        put("у", 'U')
        // V
        put("ви", 'V')
        put("вэ", 'V')
        // W
        put("даблю", 'W')
        // X
        put("икс", 'X')
        // Y
        put("игрек", 'Y')
        put("уай", 'Y')
        // Z
        put("зед", 'Z')
        put("зэт", 'Z')
    }
}
