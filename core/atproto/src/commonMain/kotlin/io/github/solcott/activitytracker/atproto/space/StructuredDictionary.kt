package io.github.solcott.activitytracker.atproto.space

/** A dictionary member's value, as far as Spaces' MAC headers need: their three types, and everything else as [Other]. */
internal sealed interface SfItem {
    data class Str(val value: String) : SfItem

    data class Integer(val value: Long) : SfItem

    /** A byte sequence's base64, as written between the colons. */
    data class Bytes(val base64: String) : SfItem

    /** A token, decimal, boolean or inner list: parsed past, never read. */
    data object Other : SfItem
}

/**
 * RFC 8941 §4.2.2 dictionary parsing, narrowly: members' parameters are skipped, a later duplicate key replaces an earlier one, and
 * anything malformed throws [IllegalArgumentException].
 */
@Suppress("TooManyFunctions") // One per RFC 8941 production, plus the cursor helpers.
internal class StructuredDictionary private constructor(private val input: String) {
    private var pos = 0

    companion object {
        private const val MAX_INTEGER_DIGITS = 15
        private const val ASCII_END = 128

        fun parse(value: String): Map<String, SfItem> = StructuredDictionary(value).dictionary()
    }

    private fun dictionary(): Map<String, SfItem> {
        val members = mutableMapOf<String, SfItem>()
        skip(' ')
        while (pos < input.length) {
            val key = key()
            val value =
                if (peek() == '=') {
                    pos++
                    if (peek() == '(') innerList() else bareItem()
                } else {
                    SfItem.Other // a bare key is boolean true
                }
            parameters()
            members.remove(key)
            members[key] = value
            skipOws()
            if (pos == input.length) break
            expect(',')
            skipOws()
            require(pos < input.length) { "Trailing comma" }
        }
        return members
    }

    private fun key(): String {
        val start = pos
        require(peek()?.let { it in 'a'..'z' || it == '*' } == true) { "Bad key" }
        while (peek()?.let { it in 'a'..'z' || it in '0'..'9' || it in "_-.*" } == true) pos++
        return input.substring(start, pos)
    }

    private fun parameters() {
        while (peek() == ';') {
            pos++
            skip(' ')
            key()
            if (peek() == '=') {
                pos++
                bareItem()
            }
        }
    }

    private fun innerList(): SfItem {
        expect('(')
        while (true) {
            skip(' ')
            if (peek() == ')') break
            bareItem()
            parameters()
            require(peek() == ' ' || peek() == ')') { "Bad inner list" }
        }
        pos++
        return SfItem.Other
    }

    private fun bareItem(): SfItem =
        when (val c = peek()) {
            '"' -> string()
            ':' -> bytes()
            '?' -> {
                pos++
                require(peek() == '0' || peek() == '1') { "Bad boolean" }
                pos++
                SfItem.Other
            }
            null -> throw IllegalArgumentException("Missing value")
            else ->
                when {
                    c == '-' || c in '0'..'9' -> number()
                    c in 'a'..'z' || c in 'A'..'Z' || c == '*' -> token()
                    else -> throw IllegalArgumentException("Bad item")
                }
        }

    private fun string(): SfItem {
        expect('"')
        val out = StringBuilder()
        while (true) {
            val c = requireNotNull(next()) { "Unterminated string" }
            when (c) {
                '"' -> return SfItem.Str(out.toString())
                '\\' -> {
                    val escaped = next()
                    require(escaped == '"' || escaped == '\\') { "Bad escape" }
                    out.append(escaped)
                }
                in ' '..'~' -> out.append(c)
                else -> throw IllegalArgumentException("Bad string character")
            }
        }
    }

    private fun bytes(): SfItem {
        expect(':')
        val start = pos
        while (peek()?.let { it.isLetterOrDigit() && it.code < ASCII_END || it in "+/=" } == true) pos++
        val base64 = input.substring(start, pos)
        expect(':')
        return SfItem.Bytes(base64)
    }

    private fun number(): SfItem {
        val start = pos
        if (peek() == '-') pos++
        while (peek()?.isDigit() == true || peek() == '.') pos++
        val text = input.substring(start, pos)
        if ('.' in text) {
            requireNotNull(text.toDoubleOrNull()) { "Bad decimal" }
            return SfItem.Other
        }
        require(text.trimStart('-').length in 1..MAX_INTEGER_DIGITS) { "Bad integer" }
        return SfItem.Integer(text.toLong())
    }

    private fun token(): SfItem {
        pos++
        while (peek()?.let { it in '!'..'~' && it !in "\"(),;<=>?@[\\]{}" } == true) pos++
        return SfItem.Other
    }

    private fun peek(): Char? = input.getOrNull(pos)

    private fun next(): Char? = input.getOrNull(pos)?.also { pos++ }

    private fun expect(c: Char) {
        require(peek() == c) { "Expected '$c'" }
        pos++
    }

    private fun skip(c: Char) {
        while (peek() == c) pos++
    }

    private fun skipOws() {
        while (peek() == ' ' || peek() == '\t') pos++
    }
}
