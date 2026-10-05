package com.example.geosamplemanager.data.help

import android.content.Context

/**
 * FIX 5.9-settings-help-2a:
 * Загрузка и парсинг Markdown-контента справки из assets.
 *
 * Формат — минимальный:
 *   # Заголовок 1
 *   ## Заголовок 2
 *   ### Заголовок 3
 *   - пункт списка
 *   обычный абзац
 *   **жирный** внутри текста
 *
 * Пустая строка разделяет абзацы и списки.
 *
 * Кэш в памяти: Map<assetPath, List<HelpBlock>>.
 * Контент читается один раз, потом отдаётся из кэша.
 */
sealed interface HelpBlock {
    data class Heading1(val text: String) : HelpBlock
    data class Heading2(val text: String) : HelpBlock
    data class Heading3(val text: String) : HelpBlock
    data class Paragraph(val text: String) : HelpBlock
    data class BulletList(val items: List<String>) : HelpBlock
}

object HelpContentLoader {

    private val cache = HashMap<String, List<HelpBlock>>()

    /**
     * Загрузить и распарсить файл из assets/help/<name>.md.
     * Если файл не найден — пустой список.
     */
    fun load(context: Context, fileName: String): List<HelpBlock> {
        cache[fileName]?.let { return it }

        val text = try {
            context.assets.open("help/$fileName").use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            ""
        }

        val blocks = parseMarkdown(text)
        cache[fileName] = blocks
        return blocks
    }

    /**
     * Очистить кэш. Вызывать при обновлении assets в dev-режиме
     * или при смене языка (пока не используется).
     */
    fun clearCache() {
        cache.clear()
    }

    /**
     * Парсер Markdown → список блоков.
     * Чистая функция, тестируется без Android.
     */
    fun parseMarkdown(text: String): List<HelpBlock> {
        if (text.isBlank()) return emptyList()

        val result = mutableListOf<HelpBlock>()
        val lines = text.replace("\r\n", "\n").split('\n')

        var paragraphBuffer = StringBuilder()
        val listBuffer = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraphBuffer.isNotEmpty()) {
                result.add(HelpBlock.Paragraph(paragraphBuffer.toString().trim()))
                paragraphBuffer = StringBuilder()
            }
        }

        fun flushList() {
            if (listBuffer.isNotEmpty()) {
                result.add(HelpBlock.BulletList(listBuffer.toList()))
                listBuffer.clear()
            }
        }

        fun flushAll() {
            flushParagraph()
            flushList()
        }

        for (raw in lines) {
            val line = raw.trimEnd()

            when {
                line.isBlank() -> flushAll()

                line.startsWith("### ") -> {
                    flushAll()
                    result.add(HelpBlock.Heading3(line.removePrefix("### ").trim()))
                }

                line.startsWith("## ") -> {
                    flushAll()
                    result.add(HelpBlock.Heading2(line.removePrefix("## ").trim()))
                }

                line.startsWith("# ") -> {
                    flushAll()
                    result.add(HelpBlock.Heading1(line.removePrefix("# ").trim()))
                }

                line.startsWith("- ") -> {
                    flushParagraph()
                    listBuffer.add(line.removePrefix("- ").trim())
                }

                else -> {
                    flushList()
                    if (paragraphBuffer.isNotEmpty()) paragraphBuffer.append(' ')
                    paragraphBuffer.append(line.trim())
                }
            }
        }

        flushAll()
        return result
    }
}