package com.example.geosamplemanager.data.voice

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

data class VoiceParseResult(
    val candidates: List<String>,
    val normalizedText: String
) {
    val primary: String? get() = candidates.firstOrNull()
}

/**
 * Разбор голосовых номеров в кандидатов для поиска.
 *
 * FIX 5.8.6-2a:
 * Было:
 *   «четыре ноль» → Nulls(4) → «0000»
 *
 * Из-за этого фраза:
 *   «пятнадцать двадцать четыре ноль один»
 * превращалась в:
 *   15 20 0000 1
 * и поиск мог вернуть:
 *   15 20 01
 *
 * Стало:
 * - одиночное «ноль» — это цифра 0;
 * - счётчик нулей срабатывает только на множественные формы:
 *   «два ноля», «три нуля», «четыре ноля», «пять нулей».
 */
class VoiceNumberParser {

    /**
     * Слова, означающие несколько нулей подряд.
     *
     * Важно: сюда НЕ входит одиночное «ноль».
     * «четыре ноль один» должно читаться как 4 0 1,
     * а не как 0000 1.
     */
    private val zeroCountWords = setOf(
        "ноля",
        "нуля",
        "нулей",
        "нолей"
    )

    fun parse(input: String): VoiceParseResult {
        val normalized = normalize(input)
        val rawTokens = tokenize(normalized)
        val merged = mergeMultipliers(rawTokens)
        val blocks = splitIntoBlocks(merged)
        val candidates = buildCandidates(blocks)

        return VoiceParseResult(candidates, normalized)
    }

    fun normalize(input: String): String =
        input.lowercase().replace('ё', 'е').trim()

    fun tokenize(text: String): List<VoiceToken> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val result = ArrayList<VoiceToken>(words.size)

        var i = 0

        while (i < words.size) {
            val w = words[i]
            val wClean = cleanWord(w)

            // FIX 5.8.6-2a:
            // Считаем «два ноля / три нуля / пять нулей» как несколько нулей.
            // Одиночное «ноль» не съедается.
            if (i + 1 < words.size) {
                val nextClean = cleanWord(words[i + 1])

                if (nextClean in zeroCountWords) {
                    val count = VoiceDictionary.singleDigits[wClean]

                    if (count != null && count >= 2) {
                        result.add(
                            VoiceToken.Nulls(
                                count = count,
                                raw = "$wClean $nextClean"
                            )
                        )
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

    private fun cleanWord(word: String): String =
        word.trim(',', '.', '!', '?', ';', ':', '-')

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

        // FIX 5.8.6-2a:
        // Любые формы нуля, не съеденные как счётчик нулей,
        // должны становиться цифрой 0.
        //
        // Иначе «ноль» мог попасть в Unknown и потеряться.
        if (word in VoiceDictionary.zeroWords) {
            return VoiceToken.Number(0, TokenKind.D, word)
        }

        if (word in VoiceDictionary.separators) {
            return VoiceToken.Separator(word)
        }

        if (word in VoiceDictionary.stopWords) {
            return VoiceToken.StopWord(word)
        }

        return null
    }

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

    data class RawBlock(
        var sum: Int = 0,
        var digits: StringBuilder? = null,
        var nulls: Int = 0,
        var isNulls: Boolean = false,
        var lastToken: VoiceToken? = null
    ) {
        val isDigitwise: Boolean get() = digits != null

        fun startDigitwiseWith(digit: Int) {
            if (digits == null) {
                digits = StringBuilder()
                sum = 0
                digits!!.append(digit)
            }
        }

        fun appendNumber(value: Int) {
            if (digits != null) {
                digits!!.append(value)
            } else {
                sum += value
            }
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
                    current?.let {
                        if (!it.isEmpty()) blocks.add(it)
                    }

                    current = null
                }

                is VoiceToken.Nulls -> {
                    current?.let {
                        if (!it.isEmpty()) blocks.add(it)
                    }

                    blocks.add(
                        RawBlock(
                            isNulls = true,
                            nulls = token.count
                        )
                    )

                    current = null
                }

                is VoiceToken.Number -> {
                    val cur = current

                    if (cur == null) {
                        val nb = RawBlock(lastToken = token)

                        if (token.kind == TokenKind.D) {
                            nb.startDigitwiseWith(token.value)
                        } else {
                            nb.appendNumber(token.value)
                        }

                        current = nb
                        continue
                    }

                    when (behavior(cur, token)) {
                        Behavior.SKIP -> Unit

                        Behavior.BOUNDARY -> {
                            if (!cur.isEmpty()) blocks.add(cur)

                            val nb = RawBlock(lastToken = token)

                            if (token.kind == TokenKind.D) {
                                nb.startDigitwiseWith(token.value)
                            } else {
                                nb.appendNumber(token.value)
                            }

                            current = nb
                        }

                        Behavior.CONTINUE -> {
                            cur.appendNumber(token.value)
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

        current?.let {
            if (!it.isEmpty()) blocks.add(it)
        }

        return blocks
    }

    internal fun behavior(cur: RawBlock, next: VoiceToken.Number): Behavior {
        val prev = cur.lastToken as? VoiceToken.Number ?: return Behavior.CONTINUE

        if (cur.isDigitwise) {
            return if (next.kind == TokenKind.D) {
                Behavior.CONTINUE
            } else {
                Behavior.BOUNDARY
            }
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
                    if (prev.value == 0 || next.value != 0) {
                        Behavior.TO_G
                    } else {
                        Behavior.BOUNDARY
                    }

                else -> Behavior.BOUNDARY
            }
        }
    }

    // FIX И-12: убираем дубликаты из списка кандидатов.
    fun buildCandidates(blocks: List<RawBlock>): List<String> {
        if (blocks.isEmpty()) return emptyList()

        val raw = ArrayList<String>(3)

        raw.add(
            blocks.joinToString("") { it.asString() }
        )

        if (blocks.size > 1) {
            raw.add(
                blocks.joinToString("|") { it.asString() }
            )
        }

        if (blocks.size > 2) {
            val head = blocks[0].asString() + blocks[1].asString()
            val tail = blocks.drop(2).joinToString("|") { it.asString() }

            raw.add("$head|$tail")
        }

        return raw
            .filter { it.isNotBlank() }
            .distinct()
    }
}
