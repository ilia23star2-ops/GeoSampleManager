package com.example.geosamplemanager.data.voice

/**
 * Разбор голосовой фразы в VoiceCommand.
 */
class VoiceCommandParser(
    private val numberParser: VoiceNumberParser = VoiceNumberParser()
) {

    fun parse(input: String): VoiceCommand {
        val raw = input.trim()
        if (raw.isEmpty()) return VoiceCommand.Unknown

        val norm = numberParser.normalize(raw)

        // ---- Управляющие (одиночные слова) ----
        when (norm) {
            "стоп", "хватит" -> return VoiceCommand.Stop
            "пауза", "паузу" -> return VoiceCommand.Pause
            "продолжить", "продолжай" -> return VoiceCommand.Resume
            "отмена", "отменить", "верни", "назад" -> return VoiceCommand.Undo
            "повтори", "вперёд", "вперед" -> return VoiceCommand.Redo
            "следующая", "далее", "следующую", "следующий" -> return VoiceCommand.Next
            "помощь", "команды", "команда" -> return VoiceCommand.Help
            "сколько осталось", "сколько осталось?" -> return VoiceCommand.HowManyLeft
            "показать отложенные", "отложенные" -> return VoiceCommand.ShowPostponed
            "показать найденные", "найденные" -> return VoiceCommand.ShowFound

            // FIX 5.8.9g-1: снять все — как было.
            "снять все", "сбросить все", "очистить все" -> return VoiceCommand.ClearAll

            // FIX 5.8.9g-1: отметить все пробы текущей скважины.
            "все", "отметь все", "отметить все", "отметьте все" ->
                return VoiceCommand.MarkAll

            "снять последнюю", "последнюю снять" -> return VoiceCommand.ClearLast
            "снять отложенную", "снять отложенную пробу" -> return VoiceCommand.Unpostpone
        }

        // ---- Вес ----
        if (norm == "вес" || norm.startsWith("вес ")) {
            val tail = norm.removePrefix("вес").trim()
            val value = parseWeight(tail)
            if (value != null) return VoiceCommand.SetWeight(value)
        }

        // ---- Снять <ordinal> ----
        val words = norm.split(Regex("\\s+"))
        if (words.size >= 2 &&
            words[0] in setOf("снять", "убрать", "удали", "удалить")
        ) {
            val tail = words.drop(1).joinToString(" ")
            val ord = VoiceOrdinals.match(tail)
            if (ord != null) return VoiceCommand.ClearOrdinal(ord)
        }

        // ---- Отметить <ordinal> / <ordinal> <ordinal> ... ----
        // FIX 5.8.9g-1: matchAll находит все порядковые подряд.
        val ordinals = VoiceOrdinals.matchAll(norm)
        when {
            ordinals.size == 1 -> return VoiceCommand.MarkOrdinal(ordinals[0])
            ordinals.size > 1 -> return VoiceCommand.MarkByNumbers(ordinals)
        }

        // ---- Сортировка: "<X> и <Y>" ----
        // FIX И-11: Vosk присылает словами, а не цифрами.
        // Проверяем, что каждая часть парсится в число, а не «есть ли цифры».
        val sortParts = norm.split(Regex("\\s+и\\s+"))
        if (sortParts.size in 2..5) {
            val parsed = sortParts.map { part ->
                numberParser.parse(part).primary?.takeIf { it.isNotBlank() }
            }
            if (parsed.all { it != null }) {
                return VoiceCommand.Sort(parsed.filterNotNull())
            }
        }

        // ---- Всё остальное — поиск ----
        return VoiceCommand.Search(raw)
    }

    /**
     * Парсит свободный ответ на «Вес?»: пользователь говорит без слова
     * «вес», просто «два с половиной» или «два пять».
     *
     * Возвращает вес в килограммах или null, если не разобрать.
     */
    fun parseWeightAnswer(input: String): Double? {
        val norm = numberParser.normalize(input).trim()
        if (norm.isEmpty()) return null

        val halfSuffix = "с половиной"
        if (norm.endsWith(halfSuffix)) {
            val baseText = norm.removeSuffix(halfSuffix).trim()
            val base = parseWeightAnswer(baseText) ?: return null
            return base + 0.5
        }

        val quarterSuffix = "с четвертью"
        if (norm.endsWith(quarterSuffix)) {
            val baseText = norm.removeSuffix(quarterSuffix).trim()
            val base = parseWeightAnswer(baseText) ?: return null
            return base + 0.25
        }

        return parseWeight(norm)
    }

    // ================================================================
    // Разбор веса (общая логика)
    // ================================================================

    private fun parseWeight(text: String): Double? {
        if (text.isEmpty()) return null

        text.replace(',', '.').toDoubleOrNull()?.let { return it }

        val r = numberParser.parse(text)
        val c = r.primary ?: return null

        if (!c.contains('|')) {
            c.toDoubleOrNull()?.let {
                if (c.length == 2 && c.all { ch -> ch.isDigit() }) {
                    val a = c[0].digitToInt()
                    val b = c[1].digitToInt()
                    if (b != 0) return "$a.$b".toDoubleOrNull()
                }
                return it
            }
            return null
        }

        val parts = c.split('|')
        if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
            return "${parts[0]}.${parts[1]}".toDoubleOrNull()
        }
        return null
    }
}