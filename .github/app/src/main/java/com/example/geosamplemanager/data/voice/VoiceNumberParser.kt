package com.example.geosamplemanager.data.voice

/**
 * Парсер числовой части голосовой команды.
 *
 * Полный пайплайн (без Vosk и без поиска в БД):
 *   1. Нормализация (регистр, ё → е).
 *   2. Токенизация + NULLS-детекция («два нуля» → [[NULLS:2]]).
 *   3. Склейка множителей («сто» + «тысяч» → K(100000)).
 *   4. Разбивка на блоки по таблице границ (§4.4 VOICE.md).
 *   5. Кандидаты по приоритету (§5 VOICE.md).
 *
 * Префиксы (KPD, NV) НЕ обрабатываются — это заход 5.8.3.
 */

// ====================================================================
// Типы токенов
// ====================================================================

enum class TokenKind { D, T, X, H, K, M }

sealed class VoiceToken {
    abstract val raw: String

    data class Number(
        val value: Int,
        val kind: TokenKind,
        override val raw: String
    ) : VoiceToken()

    data class Nulls(val count: Int, override val raw: String) : VoiceToken()

    data class Separator(override val raw: String) : VoiceToken()

    data class StopWord(override val raw: String) : VoiceToken()

    data class Unknown(override val raw: String) : VoiceToken()
}

/**
 * Результат разбора.
 *
 * @property candidates список кандидатов в порядке приоритета (первый —
 *   самый вероятный). Пустой список — разобрать не удалось.
 * @property normalizedText нормализованный вход — для логов и отладки.
 */
data class VoiceParseResult(
    val candidates: List<String>,
    val normalizedText: String
) {
    val primary: String? get() = candidates.firstOrNull()
}

// ====================================================================
// Парсер
// ====================================================================

class VoiceNumberParser {

    /**
     * Полный разбор строки.
     */
    fun parse(input: String): VoiceParseResult {
        val normalized = normalize(input)
        val rawTokens = tokenize(normalized)
        val merged = mergeMultipliers(rawTokens)
        val blocks = splitIntoBlocks(merged)
        val candidates = buildCandidates(blocks)
        return VoiceParseResult(candidates, normalized)
    }

    // ================================================================
    // 1. Нормализация
    // ================================================================

    fun normalize(input: String): String =
        input
            .lowercase()
            .replace('ё', 'е')
            .trim()

    // ================================================================
    // 2. Токенизация + NULLS-детекция
    // ================================================================

    fun tokenize(text: String): List<VoiceToken> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val result = ArrayList<VoiceToken>(words.size)

        var i = 0
        while (i < words.size) {
            val w = words[i]
            val wClean = w.trim(',', '.', '!', '?', ';', ':', '-')

            // NULLS: <число> <слово «нул…»> → строка нулей
            if (i + 1 < words.size) {
                val nextClean = words[i + 1]
                    .trim(',', '.', '!', '?', ';', ':', '-')
                if (nextClean in VoiceDictionary.zeroWords) {
                    val count = VoiceDictionary.singleDigits[wClean]
                    if (count != null) {
                        result.add(VoiceToken.Nulls(count, "$wClean $nextClean"))
                        i += 2
                        continue
                    }
                }
            }

            val token = classifySingle(wClean)
            if (token != null) {
                result.add(token)
            } else {
                result.add(VoiceToken.Unknown(wClean))
            }
            i++
        }
        return result
    }

    private fun classifySingle(word: String): VoiceToken? {
        VoiceDictionary.singleDigits[word]?.let {
            return VoiceToken.Number(it, TokenKind.D, word)
        }
        VoiceDictionary.teens[word]?.let {
            return VoiceToken.Number(it, TokenKind.T, word)
        }
        VoiceDictionary.tens[word]?.let {
            return VoiceToken.Number(it, TokenKind.X, word)
        }
        VoiceDictionary.hundreds[word]?.let {
            return VoiceToken.Number(it, TokenKind.H, word)
        }
        if (word in VoiceDictionary.separators) return VoiceToken.Separator(word)
        if (word in VoiceDictionary.stopWords) return VoiceToken.StopWord(word)
        return null
    }

    // ================================================================
    // 3. Склейка множителей
    // ================================================================

    /**
     * «Сто» + «тысяч» → K(100000).
     * «Две» + «тысячи» → K(2000).
     * «Пять» + «миллионов» → M(5000000).
     * Одиночная «тысяча» → K(1000). Одиночный «миллион» → M(1000000).
     */
    fun mergeMultipliers(tokens: List<VoiceToken>): List<VoiceToken> {
        val result = ArrayList<VoiceToken>(tokens.size)

        for (token in tokens) {
            if (token is VoiceToken.Unknown && token.raw in VoiceDictionary.thousandWords) {
                val prev = result.lastOrNull()
                if (prev is VoiceToken.Number) {
                    result[result.size - 1] = VoiceToken.Number(
                        value = prev.value * 1000,
                        kind = TokenKind.K,
                        raw = "${prev.raw} ${token.raw}"
                    )
                } else {
                    result.add(VoiceToken.Number(1000, TokenKind.K, token.raw))
                }
                continue
            }

            if (token is VoiceToken.Unknown && token.raw in VoiceDictionary.millionWords) {
                val prev = result.lastOrNull()
                if (prev is VoiceToken.Number) {
                    result[result.size - 1] = VoiceToken.Number(
                        value = prev.value * 1_000_000,
                        kind = TokenKind.M,
                        raw = "${prev.raw} ${token.raw}"
                    )
                } else {
                    result.add(VoiceToken.Number(1_000_000, TokenKind.M, token.raw))
                }
                continue
            }

            result.add(token)
        }
        return result
    }

    // ================================================================
    // 4. Разбивка на блоки
    // ================================================================

    /**
     * Один блок. Либо суммарное число (R), либо цифровая строка (G),
     * либо строка нулей (NULLS).
     */
    internal data class RawBlock(
        var sum: Int = 0,
        var digits: StringBuilder? = null,
        var nulls: Int = 0,
        var isNulls: Boolean = false,
        var lastToken: VoiceToken? = null
    ) {
        val isDigitwise: Boolean get() = digits != null

        fun appendNumber(value: Int) {
            if (digits != null) digits!!.append(value)
            else sum += value
        }

        fun switchToDigitwise() {
            if (digits == null) {
                digits = StringBuilder(sum.toString())
                sum = 0
            }
        }

        fun asString(): String {
            if (isNulls) return "0".repeat(nulls)
            return digits?.toString() ?: sum.toString()
        }

        fun isEmpty(): Boolean =
            digits == null && sum == 0 && !isNulls && nulls == 0
    }

    internal enum class Behavior { CONTINUE, BOUNDARY, TO_G, SKIP }

    fun splitIntoBlocks(tokens: List<VoiceToken>): List<RawBlock> {
        val blocks = ArrayList<RawBlock>()
        var current: RawBlock? = null

        for (token in tokens) {
            when (token) {
                is VoiceToken.StopWord -> continue
                is VoiceToken.Unknown -> continue
                is VoiceToken.Separator -> {
                    current?.let { if (!it.isEmpty()) blocks.add(it) }
                    current = null
                }
                is VoiceToken.Nulls -> {
                    current?.let { if (!it.isEmpty()) blocks.add(it) }
                    blocks.add(RawBlock(isNulls = true, nulls = token.count))
                    current = null
                }
                is VoiceToken.Number -> {
                    val cur = current
                    if (cur == null) {
                        val nb = RawBlock(lastToken = token)
                        if (token.kind == TokenKind.D) {
                            nb.switchToDigitwise()
                        }
                        nb.appendNumber(token.value)
                        current = nb
                        continue
                    }
                    when (behavior(cur, token)) {
                        Behavior.SKIP -> {}
                        Behavior.BOUNDARY -> {
                            if (!cur.isEmpty()) blocks.add(cur)
                            val nb = RawBlock(lastToken = token)
                            if (token.kind == TokenKind.D) {
                                nb.switchToDigitwise()
                            }
                            nb.appendNumber(token.value)
                            current = nb
                        }
                        Behavior.CONTINUE -> {
                            val prev = cur.lastToken as? VoiceToken.Number
                            if (token.kind == TokenKind.D &&
                                !cur.isDigitwise &&
                                prev?.kind == TokenKind.D
                            ) {
                                cur.switchToDigitwise()
                                cur.appendNumber(token.value)
                            } else {
                                cur.appendNumber(token.value)
                            }
                            cur.lastToken = token
                        }
                        Behavior.TO_G -> {
                            cur.switchToDigitwise()
                            cur.appendNumber(token.value)
                            cur.lastToken = token
                        }
                    }
                }
            }
        }

        current?.let { if (!it.isEmpty()) blocks.add(it) }
        return blocks
    }

    /**
     * Поведение при добавлении следующего числового токена в блок.
     * Согласно таблице границ §4.4 VOICE.md.
     */
    internal fun behavior(cur: RawBlock, next: VoiceToken.Number): Behavior {
        val prev = cur.lastToken as? VoiceToken.Number ?: return Behavior.CONTINUE

        // В digitwise (G) блоке — продолжают только D, остальное граница.
        if (cur.isDigitwise) {
            return if (next.kind == TokenKind.D) Behavior.CONTINUE
            else Behavior.BOUNDARY
        }

        return when (prev.kind) {
            TokenKind.M, TokenKind.K -> when (next.kind) {
                TokenKind.M, TokenKind.K -> Behavior.BOUNDARY
                TokenKind.H, TokenKind.X, TokenKind.T -> Behavior.CONTINUE
                TokenKind.D ->
                    if (next.value == 0) Behavior.BOUNDARY else Behavior.CONTINUE
            }
            TokenKind.H -> when (next.kind) {
                TokenKind.M, TokenKind.K, TokenKind.H -> Behavior.BOUNDARY
                TokenKind.X, TokenKind.T -> Behavior.CONTINUE
                TokenKind.D ->
                    if (next.value == 0) Behavior.BOUNDARY else Behavior.CONTINUE
            }
            TokenKind.X -> when (next.kind) {
                TokenKind.D ->
                    if (next.value == 0) Behavior.BOUNDARY else Behavior.CONTINUE
                else -> Behavior.BOUNDARY
            }
            TokenKind.T -> Behavior.BOUNDARY
            TokenKind.D -> when (next.kind) {
                TokenKind.D ->
                    if (prev.value == 0 || next.value != 0) Behavior.TO_G
                    else Behavior.BOUNDARY
                else -> Behavior.BOUNDARY
            }
        }
    }

    // ================================================================
    // 5. Кандидаты
    // ================================================================

    /**
     * Приоритет (§5 VOICE.md):
     *   1. concat(all_blocks).
     *   2. each_block_as_number (через «|», если блоков > 1).
     *   3. partial_concat — первые два блока слиты, остальные как есть.
     *   4. prefix + concat — не здесь (5.8.3).
     */
    fun buildCandidates(blocks: List<RawBlock>): List<String> {
        if (blocks.isEmpty()) return emptyList()

        val candidates = ArrayList<String>(3)

        val concat = blocks.joinToString("") { it.asString() }
        candidates.add(concat)

        if (blocks.size > 1) {
            candidates.add(blocks.joinToString("|") { it.asString() })
        }

        if (blocks.size > 2) {
            val head = blocks[0].asString() + blocks[1].asString()
            val tail = blocks.drop(2).joinToString("|") { it.asString() }
            candidates.add("$head|$tail")
        }

        return candidates
    }
}