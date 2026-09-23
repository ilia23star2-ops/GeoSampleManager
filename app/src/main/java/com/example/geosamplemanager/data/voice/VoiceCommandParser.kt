package com.example.geosamplemanager.data.voice

/**
 * Разбор голосовой фразы в VoiceCommand.
 *
 * FIX 5.8.6-5a — строгий голосовой шлюз:
 * - «семья» → «семь» больше не отмечает пробу: количественные числа
 *   сами по себе не становятся MarkOrdinal;
 * - отметка только по явным конструкциям:
 *   «первая», «отметь 7», «отметить седьмую», «отметь эту»;
 * - «снять 7» работает как ClearOrdinal;
 * - сортировка понимает разделители: «и», запятая, точка с запятой, «запятая», «тире»;
 *   но только если части похожи на номера (≥2 цифр), чтобы «два и шесть»
 *   не превращалось в Sort;
 * - служебные слова не уходят в Search;
 * - команды выбора распознаются только при pendingChoice.
 *
 * FIX 5.8.6-5a-fix-1:
 * - починены составные порядковые: «двадцать первая», «тридцать первая»;
 * - при этом «семь», «двадцать», «это первая проба» не становятся отметкой.
 *
 * FIX 5.8.6-5a-fix-2:
 * - исправлена компиляция: `num in 30` → `num in 1..30`.
 *
 * FIX 5.8.6-5g:
 * - дробные/порядковые формы мн.ч. («четвертых», «пятых», «десятых»)
 *   глушатся: Unknown вместо Search. «Пять четвертых» больше не ищет
 *   пробу с номером 5.
 */
class VoiceCommandParser(
    private val numberParser: VoiceNumberParser = VoiceNumberParser()
) {

    private val commandLikeWords = setOf(
        "стоп",
        "хватит",
        "пауза",
        "паузу",
        "продолжить",
        "продолжай",
        "отмена",
        "отменить",
        "верни",
        "назад",
        "повтори",
        "вперёд",
        "вперед",
        "следующая",
        "следующий",
        "следующую",
        "далее",
        "помощь",
        "команда",
        "команды",
        "сколько",
        "осталось",
        "показать",
        "отложенные",
        "найденные",
        "отложить",
        "отложи",
        "пропустить",
        "пропусти"
    )

    private val pendingRemovePhrases = setOf(
        "снять",
        "сними",
        "убрать",
        "убери",
        "удали",
        "удалить",
        "снять отметку",
        "убрать отметку",
        "снять пробу",
        "убрать пробу"
    )

    private val pendingPostponePhrases = setOf(
        "отложить",
        "отложи",
        "перенести",
        "перенеси",
        "отложить пробу",
        "перенести пробу"
    )

    private val pendingSkipPhrases = setOf(
        "пропустить",
        "пропусти",
        "дальше",
        "не надо",
        "ничего",
        "оставить",
        "потом"
    )

    private val pendingMarkCurrentPhrases = setOf(
        "отметить",
        "отметь",
        "отметьте",
        "отметить эту",
        "отметь эту",
        "эту",
        "отметить ее",
        "отметь ее",
        "отметить её",
        "отметь её",
        "ее",
        "её"
    )

    /** Глаголы явной отметки с аргументом: «отметь 7», «отметить первую». */
    private val markVerbPrefixes = listOf(
        "отметь ",
        "отметить ",
        "отметьте "
    )

    /** Глаголы явного снятия с аргументом: «снять 7», «убрать первую». */
    private val removeVerbWords = setOf(
        "снять",
        "убрать",
        "удали",
        "удалить"
    )

    /** Союзы/разделители, допустимые между порядковыми числительными. */
    private val ordinalJoinWords = setOf(
        "и",
        "запятая",
        ",",
        ";"
    )

    fun parse(
        input: String,
        pendingChoice: Boolean = false
    ): VoiceCommand {
        val raw = input.trim()
        if (raw.isEmpty()) return VoiceCommand.Unknown

        val norm = numberParser
            .normalize(raw)
            .trim('.', ',', '!', '?', ';', ':')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

        // ---- Команды выбора (только в состоянии pendingChoice) ----
        if (pendingChoice) {
            when (norm) {
                in pendingRemovePhrases -> return VoiceCommand.ChoiceRemove
                in pendingPostponePhrases -> return VoiceCommand.ChoicePostpone
                in pendingSkipPhrases -> return VoiceCommand.ChoiceSkip
                in pendingMarkCurrentPhrases -> return VoiceCommand.MarkCurrent
            }
        }

        // ---- Управляющие (точные совпадения) ----
        when (norm) {
            "стоп", "хватит" -> return VoiceCommand.Stop
            "пауза", "паузу" -> return VoiceCommand.Pause
            "продолжить", "продолжай" -> return VoiceCommand.Resume
            "отмена", "отменить", "назад", "верни" -> return VoiceCommand.Undo
            "вперёд", "вперед" -> return VoiceCommand.Redo
            "следующая", "далее", "следующую", "следующий" -> return VoiceCommand.Next
            "помощь", "команды", "команда" -> return VoiceCommand.Help
            "сколько осталось" -> return VoiceCommand.HowManyLeft
            "показать отложенные", "отложенные" -> return VoiceCommand.ShowPostponed
            "показать найденные", "найденные" -> return VoiceCommand.ShowFound
            "снять все", "сбросить все", "очистить все" ->
                return VoiceCommand.ClearAll

            "все", "отметь все", "отметить все", "отметьте все" ->
                return VoiceCommand.MarkAll

            // FIX 5.8.9d-2a: отметить пробу, найденную последним поиском.
            "отметь", "отметить", "отметьте",
            "отметь эту", "отметить эту", "эту", "эту отметь",
            "отметь ее", "отметить ее", "отметь её", "отметить её",
            "ее", "её", "ее отметь", "её отметь",
            "отметь найденную", "отметить найденную",
            "отметь найденное", "отметить найденное" ->
                return VoiceCommand.MarkCurrent

            "снять последнюю", "последнюю снять" -> return VoiceCommand.ClearLast
            "снять отложенную", "снять отложенную пробу" -> return VoiceCommand.Unpostpone

            "сортировка", "режим сортировка", "режим сортировки" ->
                return VoiceCommand.SetMode(VoiceSessionMode.SORT)

            "поиск", "режим поиск" ->
                return VoiceCommand.SetMode(VoiceSessionMode.SEARCH)
        }

        // ---- Вес: «вес два и шесть», «вес полтора» ----
        if (norm == "вес" || norm.startsWith("вес ")) {
            val tail = norm.removePrefix("вес").trim()
            val value = parseWeightAnswer(tail)

            return if (value != null) {
                VoiceCommand.SetWeight(value)
            } else {
                VoiceCommand.Unknown
            }
        }

        // ---- Явная отметка с аргументом: «отметь 7», «отметить первую» ----
        for (prefix in markVerbPrefixes) {
            if (norm.startsWith(prefix)) {
                val tail = norm.removePrefix(prefix).trim()

                if (tail.isEmpty()) {
                    return VoiceCommand.MarkCurrent
                }

                val ord = VoiceOrdinals.match(tail)
                if (ord != null) return VoiceCommand.MarkOrdinal(ord)

                val num = numberParser.parse(tail).primary?.toIntOrNull()
                if (num != null && num in 1..30) return VoiceCommand.MarkOrdinal(num)

                return VoiceCommand.Unknown
            }
        }

        // ---- Явное снятие с аргументом: «снять 7», «убрать первую» ----
        val words = norm.split(Regex("\\s+"))

        if (words.size >= 2 && words[0] in removeVerbWords) {
            val tail = words.drop(1).joinToString(" ")
            val ord = VoiceOrdinals.match(tail)

            if (ord != null) return VoiceCommand.ClearOrdinal(ord)

            val num = numberParser.parse(tail).primary?.toIntOrNull()
            if (num != null && num in 1..30) return VoiceCommand.ClearOrdinal(num)

            return VoiceCommand.Unknown
        }

        // ---- Служебное слово внутри фразы → не отметка и не поиск ----
        if (containsCommandWord(norm)) {
            return VoiceCommand.Unknown
        }

        // ---- Голые порядковые: «первая», «двадцать первая», «первая вторая» ----
        // Разрешаем только если фраза состоит из порядковых слов,
        // допустимых кардинальных приставок («двадцать», «тридцать»)
        // и союзов между ними.
        // Иначе «это первая проба» не станет MarkOrdinal(1).
        val tokens = norm.split(Regex("\\s+")).filter { it.isNotBlank() }
        val ordinals = VoiceOrdinals.matchAll(norm)

        if (ordinals.isNotEmpty() && isOrdinalPhrase(tokens)) {
            return when {
                ordinals.size == 1 -> VoiceCommand.MarkOrdinal(ordinals[0])
                else -> VoiceCommand.MarkByNumbers(ordinals)
            }
        }

        // ---- FIX 5.8.6-5g: дробные формы мн.ч. ----
        // «Пять четвертых», «две десятых», «шесть сотых» — это части
        // дробей или артефакты распознавания, а не команды и не номера
        // проб. Раньше такая фраза уходила в Search по оставшейся цифре
        // («пять четвертых» → Search("5")). Теперь — Unknown.
        if (tokens.any { it in VoiceOrdinals.pluralForms }) {
            return VoiceCommand.Unknown
        }

        // ---- Сортировка: «1524 и 1525», «1524 запятая 1525» ----
        val sortNormalized = norm
            .replace(Regex("\\s*запятая\\s*"), ", ")
            .replace(Regex("\\s*тире\\s*"), ", ")
            .replace(Regex("\\s*;\\s*"), ", ")

        val sortParts = sortNormalized
            .split(Regex("\\s*,\\s+|\\s+и\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (sortParts.size in 2..5) {
            val parsed = sortParts.map { part ->
                val cand = numberParser.parse(part).primary?.takeIf { it.isNotBlank() }
                    ?: part.takeIf { it.isNotEmpty() && it.all { ch -> ch.isDigit() } }

                // Требуем номер, а не одиночную цифру веса.
                cand?.takeIf { it.filter { ch -> ch.isDigit() }.length >= 2 }
            }

            if (parsed.all { it != null }) {
                return VoiceCommand.Sort(parsed.filterNotNull())
            }
        }

        // ---- В поиск уходит только то, что похоже на номер/код ----
        return if (looksLikeSearchQuery(norm)) {
            VoiceCommand.Search(raw)
        } else {
            VoiceCommand.Unknown
        }
    }

    private fun containsCommandWord(norm: String): Boolean {
        return norm.split(Regex("\\s+")).any { it in commandLikeWords }
    }

    /**
     * FIX 5.8.6-5a-fix-1:
     * Позволяет составные порядковые:
     * - «первая»
     * - «двадцать первая»
     * - «тридцать первая»
     * - «первая и вторая»
     *
     * Но не позволяет:
     * - «семь»
     * - «двадцать»
     * - «это первая проба»
     * - «семья»
     */
    private fun isOrdinalPhrase(tokens: List<String>): Boolean {
        if (tokens.isEmpty()) return false

        var hasOrdinal = false

        for (token in tokens) {
            when {
                token in ordinalJoinWords -> Unit

                VoiceOrdinals.match(token) != null -> hasOrdinal = true

                token in VoiceOrdinals.map.keys -> hasOrdinal = true

                // Для составных порядковых: «двадцать первая», «тридцать вторая».
                token in VoiceDictionary.tens.keys -> Unit

                else -> return false
            }
        }

        return hasOrdinal
    }

    /**
     * Похожа ли фраза на поисковый запрос: номер скважины, номер пробы, код с буквами.
     */
    private fun looksLikeSearchQuery(norm: String): Boolean {
        if (norm.isEmpty()) return false
        if (norm.any { it.isDigit() }) return true
        if (norm.any { it.isLetter() && it.code < 128 }) return true
        return numberParser.parse(norm).candidates.isNotEmpty()
    }

    /**
     * Парсит свободный ответ на «Вес?».
     *
     * Поддерживается:
     * «два» → 2.0; «два и шесть» → 2.6; «2,6»/«2.6» → 2.6;
     * «две целых шесть десятых» → 2.6; «два целых шесть сотых» → 2.06;
     * «шесть десятых» → 0.6; «шесть сотых» → 0.06;
     * «два с половиной» → 2.5; «два с четвертью» → 2.25;
     * «полтора» → 1.5; «полкило» → 0.5; «два кг»/«два кило» → 2.0.
     */
    fun parseWeightAnswer(input: String): Double? {
        var norm = numberParser
            .normalize(input)
            .trim('.', ',', '!', '?', ';', ':')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

        if (norm.isEmpty()) return null

        when (norm) {
            "полтора", "полторы" -> return 1.5
            "полкило" -> return 0.5
        }

        norm = norm
            .removeSuffix("килограмма")
            .removeSuffix("килограмм")
            .removeSuffix("килограммы")
            .removeSuffix("кг")
            .removeSuffix("кило")
            .trim()

        if (norm.isEmpty()) return null

        tryParseExplicitDecimal(norm)?.let { return it }
        tryParseFractionOnly(norm)?.let { return it }

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

    private fun tryParseExplicitDecimal(text: String): Double? {
        val splitWords = listOf("целых", "целая", "целое")
        val delimiter = splitWords.firstOrNull { text.contains(" $it ") } ?: return null

        val idx = text.indexOf(" $delimiter ")
        if (idx < 0) return null

        val intText = text.substring(0, idx).trim()
        val tailText = text.substring(idx + delimiter.length + 2).trim()

        val intPart = numberParser.parse(intText).primary?.toIntOrNull() ?: return null
        if (intPart < 0) return null

        val tailWords = tailText.split(Regex("\\s+"))
        if (tailWords.size < 2) return null

        val denominator = tailWords.last()
        val numeratorText = tailWords.dropLast(1).joinToString(" ")
        val numerator = numberParser.parse(numeratorText).primary?.toIntOrNull()
            ?: return null

        val digits = when (denominator) {
            "десятых", "десятая", "десятые" -> 1
            "сотых", "сотая", "сотые" -> 2
            "тысячных", "тысячная", "тысячные" -> 3
            else -> return null
        }

        val numeratorStr = numerator.toString().padStart(digits, '0')
        if (numeratorStr.length != digits) return null

        return "$intPart.$numeratorStr".toDoubleOrNull()
    }

    private fun tryParseFractionOnly(text: String): Double? {
        val tailWords = text.split(Regex("\\s+"))
        if (tailWords.size < 2) return null

        val denominator = tailWords.last()
        val numeratorText = tailWords.dropLast(1).joinToString(" ")
        val numerator = numberParser.parse(numeratorText).primary?.toIntOrNull()
            ?: return null

        val digits = when (denominator) {
            "десятых", "десятая", "десятые" -> 1
            "сотых", "сотая", "сотые" -> 2
            "тысячных", "тысячная", "тысячные" -> 3
            else -> return null
        }

        val numeratorStr = numerator.toString().padStart(digits, '0')
        if (numeratorStr.length != digits) return null

        return "0.$numeratorStr".toDoubleOrNull()
    }

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
