package com.example.geosamplemanager.data.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.9-settings-help-2a:
 * Тесты на парсер Markdown. Чистая функция, без Android.
 */
class HelpContentLoaderTest {

    @Test
    fun emptyText_returnsEmptyList() {
        assertEquals(emptyList<HelpBlock>(), HelpContentLoader.parseMarkdown(""))
        assertEquals(emptyList<HelpBlock>(), HelpContentLoader.parseMarkdown("   \n  \n"))
    }

    @Test
    fun h1_parsedAsHeading1() {
        val blocks = HelpContentLoader.parseMarkdown("# Заголовок")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is HelpBlock.Heading1)
        assertEquals("Заголовок", (blocks[0] as HelpBlock.Heading1).text)
    }

    @Test
    fun h2_and_h3_parsed() {
        val blocks = HelpContentLoader.parseMarkdown("## Два\n### Три")
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is HelpBlock.Heading2)
        assertTrue(blocks[1] is HelpBlock.Heading3)
    }

    @Test
    fun paragraph_parsed() {
        val blocks = HelpContentLoader.parseMarkdown("Обычный текст абзаца.")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is HelpBlock.Paragraph)
        assertEquals("Обычный текст абзаца.", (blocks[0] as HelpBlock.Paragraph).text)
    }

    @Test
    fun consecutiveLines_mergedIntoOneParagraph() {
        val blocks = HelpContentLoader.parseMarkdown("Первая строка.\nВторая строка.")
        assertEquals(1, blocks.size)
        val p = blocks[0] as HelpBlock.Paragraph
        assertEquals("Первая строка. Вторая строка.", p.text)
    }

    @Test
    fun blankLine_separatesParagraphs() {
        val blocks = HelpContentLoader.parseMarkdown("Первый.\n\nВторой.")
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is HelpBlock.Paragraph)
        assertTrue(blocks[1] is HelpBlock.Paragraph)
    }

    @Test
    fun bulletList_parsed() {
        val text = "- пункт 1\n- пункт 2\n- пункт 3"
        val blocks = HelpContentLoader.parseMarkdown(text)
        assertEquals(1, blocks.size)
        val list = blocks[0] as HelpBlock.BulletList
        assertEquals(3, list.items.size)
        assertEquals("пункт 1", list.items[0])
        assertEquals("пункт 3", list.items[2])
    }

    @Test
    fun heading_then_paragraph_then_list() {
        val text = """
            # Заголовок
            
            Абзац текста.
            
            - пункт 1
            - пункт 2
        """.trimIndent()
        val blocks = HelpContentLoader.parseMarkdown(text)
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is HelpBlock.Heading1)
        assertTrue(blocks[1] is HelpBlock.Paragraph)
        assertTrue(blocks[2] is HelpBlock.BulletList)
    }

    @Test
    fun paragraph_after_list_breaks_list() {
        val text = "- пункт\n\nТекст после списка."
        val blocks = HelpContentLoader.parseMarkdown(text)
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is HelpBlock.BulletList)
        assertTrue(blocks[1] is HelpBlock.Paragraph)
    }

    @Test
    fun bold_insideParagraph_keptAsText() {
        // Парсер не разбирает ** — это делает рендер. Текст блока
        // сохраняется как есть, включая **.
        val blocks = HelpContentLoader.parseMarkdown("Это **важно**.")
        val p = blocks[0] as HelpBlock.Paragraph
        assertEquals("Это **важно**.", p.text)
    }

    @Test
    fun crlf_normalizedToLf() {
        val text = "# Заголовок\r\n\r\nАбзац."
        val blocks = HelpContentLoader.parseMarkdown(text)
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is HelpBlock.Heading1)
        assertTrue(blocks[1] is HelpBlock.Paragraph)
    }
}