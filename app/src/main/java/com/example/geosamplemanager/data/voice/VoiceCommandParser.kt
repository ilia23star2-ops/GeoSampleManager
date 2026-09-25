package com.example.geosamplemanager.data.voice

/**
 * Разбор голосовой фразы в VoiceCommand.
 *
 * FIX 5.8.11-e4-markers:
 *  - Глагол без номера → маркер намерения.
 *  - Глагол + номер в одной фразе → сразу команда.
 *  - В AWAITING_CONFIRM: «подтверждаю» → Confirm, «отменяю» → Decline.
 *
 * FIX 5.8.11-e4-markers/8:
 *  - «отметь 7» (голое число цифрами) — теперь парсится через
 *    tail.toIntOrNull(). Раньше numberParser.parse("7").primary
 *    не давал нужный результат.
 *  - В parseForPaused добавлено «хватит» (раньше было только «хатит»).
 *  - В parse() и parseForContinue добавлено «хватит».
 */
class VoiceCommandParser(
    private val numberParser: VoiceNumberParser = VoiceNumberParser()
) {

    private val commandLikeWords = setOf(
        "стоп", "хватит", "пауза", "паузу", "продолжить", "продолжай",
        "отмена", "отменить", "верни", "назад", "повтори", "вперёд", "вперед",
        "следующая", "следующий", "следующую", "далее",
        "помощь", "команда", "команды", "сколько", "осталось",
        "показать", "отложенные", "найденные", "отложить", "отложи",
        "пропустить", "пропусти"
    )

    private val pendingRemovePhrases = setOf(
        "снять", "сними", "убрать", "убери", "удали", "удалить",
        "снять отметку", "убрать отметку", "снять пробу", "убрать пробу"
    )

    private val pendingPostponePhrases = setOf(
        "отложить", "отложи", "перенести", "перенеси",
        "отложить пробу", "перенести пробу"
    )

    private val pendingSkipPhrases = setOf(
        "пропустить", "пропусти", "дальше", "не надо", "ничего",
        "оставить", "потом"
    )

    private val pendingMarkCurrentPhrases = setOf(
        "отметить", "отметь", "отметьте",
        "отметить эту", "отметь эту", "эту",
        "отметить ее", "отметь ее", "отметить её", "отметь её",
        "ее", "её"
    )

    private val markVerbPrefixes = listOf("отметь ", "отметить ", "отметьте ")

    private val removeVerbWords = setOf("снять", "убрать", "удали", "удалить")

    private val findVerbWords = setOf("найди", "найти", "ищи", "искать", "поищи")

    private val nextVerbWords = setOf(
        "следующая", "следующий", "следующую", "далее"
    )

    private val ordinalJoinWords = setOf("и", "запятая", ",", ";")

    private val confirmWords = setOf("подтверждаю", "подтвердить")
    private val declineWords = setOf("отменяю", "отменить", "отмена")
    private val clearIntentWords = setOf("снять", "сними", "убрать", "убери")
    private val postponeIntentWords = setOf(
        "отложить", "отложи", "перенести", "перенеси"
    )
    private val markIntentWords = setOf("отметь", "отметить", "отметьте")

    /** FIX 5.8.11-e4-markers/8: единый набор стоп-слов. */
    private val stopWords = setOf("стоп", "хатит", "хватит")

    private val hundredFormToValue: Map<String, Int> = mapOf(
        "сто" to 1, "двести" to 2, "триста" to 3, "четыреста" to 4,
        "пятьсот" to 5, "шестьсот" to 6, "семьсот" to 7,
        "восемьсот" to 8, "девятьсот" to 9
    )

    private val weightAllowedWords: Set<String> = buildSet {
        addAll(VoiceDictionary.singleDigits.keys)
        addAll(VoiceDictionary.teens.keys)
        addAll(VoiceDictionary.tens.keys)
        addAll(VoiceDictionary.hundreds.keys)
        addAll(VoiceDictionary.thousandWords)
        addAll(VoiceDictionary.millionWords)

        add("и"); add("с")
        add("целых"); add("целая"); add("целое")
        add("десятых"); add("десятая"); add("десятые")
        add("сотых"); add("сотая"); add("сотые")
        add("тысячных"); add("тысячная"); add("тысячные")
        add("полтора"); add("полторы"); add("полкило")
        add("половиной"); add("четвертью")
        add("кг"); add("кило")
        add("килограмма"); add("килограмм"); add("килограммы")
        add("вес"); add("веса"); add("весу"); add("весом"); add("весе")

        addAll(hundredFormToValue.keys)
    }

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

        if (pendingChoice) {
            when (norm) {
                in pendingRemovePhrases -> return VoiceCommand.ChoiceRemove
                in pendingPostponePhrases -> return VoiceCommand.ChoicePostpone
                in pendingSkipPhrases -> return VoiceCommand.ChoiceSkip
                in pendingMarkCurrentPhrases -> return VoiceCommand.MarkCurrent
            }
        }

        run {
            val words = norm.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.size >= 2 && words[0] in nextVerbWords) {
                val tail = words.drop(1).joinToString(" ").trim()
                if (tail.isNotEmpty()) return VoiceCommand.Find(tail)
            }
        }

        when (norm) {
            in stopWords -> return VoiceCommand.Stop
            "пауза", "паузу" -> return VoiceCommand.Pause
            "продолжить", "продолжай" -> return VoiceCommand.Resume
            "отмена", "отменить", "назад", "верни" -> return VoiceCommand.Undo
            "вперёд", "вперед" -> return VoiceCommand.Redo
            "следующая", "далее", "следующую", "следующий" -> return VoiceCommand.Next
            "помощь", "команды", "команда" -> return VoiceCommand.Help
            "сколько осталось" -> return VoiceCommand.HowManyLeft
            "показать отложенные", "отложенные" -> return VoiceCommand.ShowPostponed
            "показать найденные", "найденные" -> return VoiceCommand.ShowFound
            "снять все", "сбросить все", "очистить все" -> return VoiceCommand.ClearAll
            "все", "отметь все", "отметить все", "отметьте все" -> return VoiceCommand.MarkAll

            in markIntentWords -> return VoiceCommand.MarkIntent
            in clearIntentWords -> return VoiceCommand.ClearIntent
            in postponeIntentWords -> return VoiceCommand.PostponeIntent

            "отметь эту", "отметить эту", "эту", "эту отметь",
            "отметь ее", "отметить ее", "отметь её", "отметить её",
            "ее", "её", "ее отметь", "её отметь",
            "отметь найденную", "отметить найденную",
            "отметь найденное", "отметить найденное" -> return VoiceCommand.MarkCurrent

            "снять последнюю", "последнюю снять" -> return VoiceCommand.ClearLast
            "снять отложенную", "снять отложенную пробу" -> return VoiceCommand.Unpostpone

            "сортировка", "режим сортировка", "режим сортировки" ->
                return VoiceCommand.SetMode(VoiceSessionMode.SORT)

            "поиск", "режим поиск" ->
                return VoiceCommand.SetMode(VoiceSessionMode.SEARCH)
        }

        if (norm == "вес" || norm.startsWith("вес ")) {
            val tail = norm.removePrefix("вес").trim()
            val value = parseWeightAnswer(tail)
            return if (value != null) VoiceCommand.SetWeight(value) else VoiceCommand.Unknown
        }

        // FIX 5.8.11-e4-markers/8: глагол + номер.
        // Порядок: порядковое → цифры → числительное словами.
        for (prefix in markVerbPrefixes) {
            if (norm.startsWith(prefix)) {
                val tail = norm.removePrefix(prefix).trim()
                if (tail.isEmpty()) return VoiceCommand.MarkIntent

                val ord = VoiceOrdinals.match(tail)
                if (ord != null) return VoiceCommand.MarkOrdinal(ord)

                tail.toIntOrNull()?.let { n ->
                    if (n in 1..30) return VoiceCommand.MarkOrdinal(n)
                }

                val num = numberParser.parse(tail).primary?.toIntOrNull()
                if (num != null && num in 1..30) return VoiceCommand.MarkOrdinal(num)

                return VoiceCommand.Unknown
            }
        }

        val words = norm.split(Regex("\\s+"))

        if (words.size >= 2 && words[0] in removeVerbWords) {
            val tail = words.drop(1).joinToString(" ")
            val ord = VoiceOrdinals.match(tail)
            if (ord != null) return VoiceCommand.ClearOrdinal(ord)

            tail.toIntOrNull()?.let { n ->
                if (n in 1..30) return VoiceCommand.ClearOrdinal(n)
            }

            val num = numberParser.parse(tail).primary?.toIntOrNull()
            if (num != null && num in 1..30) return VoiceCommand.ClearOrdinal(num)
            return VoiceCommand.Unknown
        }

        val findWords = norm.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (findWords.isNotEmpty() && findWords[0] in findVerbWords) {
            val tail = findWords.drop(1).joinToString(" ").trim()
            return VoiceCommand.Find(tail.ifEmpty { null })
        }

        if (containsCommandWord(norm)) return VoiceCommand.Unknown

        val tokens = norm.split(Regex("\\s+")).filter { it.isNotBlank() }
        val ordinals = VoiceOrdinals.matchAll(norm)

        if (ordinals.isNotEmpty() && isOrdinalPhrase(tokens)) {
            return when {
                ordinals.size == 1 -> VoiceCommand.MarkOrdinal(ordinals[0])
                else -> VoiceCommand.MarkByNumbers(ordinals)
            }
        }

        if (tokens.any { it in VoiceOrdinals.pluralForms }) {
            return VoiceCommand.Unknown
        }

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
                cand?.takeIf { it.filter { ch -> ch.isDigit() }.length >= 2 }
            }
            if (parsed.all { it != null }) return VoiceCommand.Sort(parsed.filterNotNull())
        }

        return if (looksLikeSearchQuery(norm)) VoiceCommand.Search(raw) else VoiceCommand.Unknown
    }

    fun parseWithState(
        input: String,
        state: VoiceState,
        mode: VoiceSessionMode
    ): VoiceCommand {
        return when (state) {
            VoiceState.IDLE -> VoiceCommand.Unknown
            VoiceState.LISTENING -> parse(input, pendingChoice = false)
            VoiceState.FOUND_PINNED -> parseForPinned(input)
            VoiceState.AWAITING_WEIGHT -> parseForWeight(input)
            VoiceState.AWAITING_CHOICE -> parse(input, pendingChoice = true)
            VoiceState.AWAITING_CONTINUE -> parseForContinue(input)
            VoiceState.PAUSED -> parseForPaused(input)
            VoiceState.AWAITING_MARK -> parseForNumberIntent(input)
            VoiceState.AWAITING_CLEAR -> parseForNumberIntent(input)
            VoiceState.AWAITING_POSTPONE -> parseForNumberIntent(input)
            VoiceState.AWAITING_CONFIRM -> parseForConfirm(input)
        }
    }

    private fun parseForNumberIntent(input: String): VoiceCommand {
        val norm = numberParser
            .normalize(input)
            .trim('.', ',', '!', '?', ';', ':')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

        if (norm.isEmpty()) return VoiceCommand.Unknown

        when (norm) {
            in stopWords -> return VoiceCommand.Stop
            "отмена", "отменить" -> return VoiceCommand.Undo
            "пауза", "паузу" -> return VoiceCommand.Pause
        }

        val ordinals = VoiceOrdinals.matchAll(norm)
        if (ordinals.isNotEmpty() && isOrdinalPhrase(norm.split(Regex("\\s+")))) {
            return when {
                ordinals.size == 1 -> VoiceCommand.MarkOrdinal(ordinals[0])
                else -> VoiceCommand.MarkByNumbers(ordinals)
            }
        }

        val tokens = norm.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size in 2..5) {
            val nums = tokens.mapNotNull { t ->
                numberParser.parse(t).primary?.replace("|", "")?.toIntOrNull()
                    ?.takeIf { it in 1..30 }
            }
            if (nums.size == tokens.size && nums.size >= 2) {
                return VoiceCommand.MarkByNumbers(nums)
            }
        }

        norm.toIntOrNull()?.let { n ->
            if (n in 1..30) return VoiceCommand.MarkOrdinal(n)
        }

        val parsed = numberParser.parse(norm)
        val combined = parsed.primary?.replace("|", "")?.toIntOrNull()
        if (combined != null && combined in 1..30) {
            return VoiceCommand.MarkOrdinal(combined)
        }

        return VoiceCommand.Unknown
    }

    private fun parseForConfirm(input: String): VoiceCommand {
        val norm = numberParser.normalize(input).trim()

        return when {
            norm in confirmWords -> VoiceCommand.Confirm
            norm in declineWords -> VoiceCommand.Decline
            norm in stopWords -> VoiceCommand.Stop
            else -> VoiceCommand.Unknown
        }
    }

    private fun parseForPinned(input: String): VoiceCommand {
        val raw = input.trim()
        if (raw.isEmpty()) return VoiceCommand.Unknown

        val norm = numberParser
            .normalize(raw)
            .trim('.', ',', '!', '?', ';', ':')
            .trim()

        if (norm == "дальше") return VoiceCommand.NextInQueue

        run {
            val words = norm.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.size >= 2 && words[0] in nextVerbWords) {
                val tail = words.drop(1).joinToString(" ").trim()
                if (tail.isNotEmpty()) return VoiceCommand.Find(tail)
            }
        }

        if (norm in nextVerbWords) return VoiceCommand.Next

        norm.toIntOrNull()?.let { n ->
            if (n > 0) return VoiceCommand.MarkOrdinal(n)
        }

        run {
            val tokens = numberParser.tokenize(norm)
            if (tokens.isNotEmpty()) {
                val hasNonNumber = tokens.any { t ->
                    when (t) {
                        is VoiceToken.Number -> false
                        is VoiceToken.Separator -> false
                        is VoiceToken.Unknown -> {
                            !(t.raw in VoiceDictionary.thousandWords ||
                                    t.raw in VoiceDictionary.millionWords)
                        }
                        else -> true
                    }
                }
                if (!hasNonNumber) {
                    val parsed = numberParser.parse(norm)
                    val combined = parsed.primary?.replace("|", "")?.toIntOrNull()
                    if (combined != null && combined > 0) {
                        return VoiceCommand.MarkOrdinal(combined)
                    }
                }
            }
        }

        val words = norm.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size == 1) {
            val parsed = numberParser.parse(norm)
            val n = parsed.primary?.toIntOrNull()
            if (n != null && n > 0) return VoiceCommand.MarkOrdinal(n)
        }

        val result = parse(input, pendingChoice = false)
        return if (result is VoiceCommand.Search) VoiceCommand.Unknown else result
    }

    private fun parseForWeight(input: String): VoiceCommand {
        val norm = numberParser.normalize(input).trim()

        when (norm) {
            in stopWords -> return VoiceCommand.Stop
            "отмена", "отменить" -> return VoiceCommand.Undo
            "пауза", "паузу" -> return VoiceCommand.Pause
        }

        val weight = parseWeightAnswer(norm)
        return if (weight != null) VoiceCommand.SetWeight(weight)
        else VoiceCommand.Unknown
    }

    private fun parseForContinue(input: String): VoiceCommand {
        val norm = numberParser.normalize(input).trim()

        return when (norm) {
            "продолжить", "продолжай" -> VoiceCommand.Resume
            "пауза", "паузу" -> VoiceCommand.Pause
            in stopWords -> VoiceCommand.Stop
            else -> parse(input, pendingChoice = false)
        }
    }

    private fun parseForPaused(input: String): VoiceCommand {
        val norm = numberParser.normalize(input).trim()

        return when (norm) {
            "продолжить", "продолжай" -> VoiceCommand.Resume
            in stopWords -> VoiceCommand.Stop
            else -> VoiceCommand.Unknown
        }
    }

    private fun containsCommandWord(norm: String): Boolean {
        return norm.split(Regex("\\s+")).any { it in commandLikeWords }
    }

    private fun isOrdinalPhrase(tokens: List<String>): Boolean {
        if (tokens.isEmpty()) return false

        var hasOrdinal = false

        for (token in tokens) {
            when {
                token in ordinalJoinWords -> Unit
                VoiceOrdinals.match(token) != null -> hasOrdinal = true
                token in VoiceOrdinals.map.keys -> hasOrdinal = true
                token in VoiceDictionary.tens.keys -> Unit
                else -> return false
            }
        }

        return hasOrdinal
    }

    private fun looksLikeSearchQuery(norm: String): Boolean {
        if (norm.isEmpty()) return false
        if (norm.any { it.isDigit() }) return true
        if (norm.any { it.isLetter() && it.code < 128 }) return true
        return numberParser.parse(norm).candidates.isNotEmpty()
    }

    private fun isCleanWeightPhrase(norm: String): Boolean {
        if (norm.isEmpty()) return false

        val words = norm.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return false

        return words.all { word ->
            if (word.all { it.isDigit() }) return@all true
            val decimal = word.replace(',', '.')
            if (decimal.toDoubleOrNull() != null) return@all true
            word in weightAllowedWords
        }
    }

    fun parseWeightAnswer(input: String): Double? {
        var norm = numberParser
            .normalize(input)
            .trim('.', ',', '!', '?', ';', ':')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

        if (norm.isEmpty()) return null
        if (!isCleanWeightPhrase(norm)) return null

        when (norm) {
            "полтора", "полторы" -> return 1.5
            "полкило" -> return 0.5
        }

        run {
            val words = norm.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.size == 2) {
                val first = numberParser.parse(words[0]).primary?.toIntOrNull()
                val second = hundredFormToValue[words[1]]
                if (first != null && first in 0..99 && second != null) {
                    val cents = second * 100
                    return first + cents / 1000.0
                }
            }
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
