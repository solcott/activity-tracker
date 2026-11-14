package io.github.solcott.activitytracker.atproto.space

import kotlin.io.encoding.Base64
import kotlin.math.abs
import kotlin.math.round

/** An RFC 8941 bare item. */
internal sealed interface SfBare {
    data class Integer(val value: Long) : SfBare

    data class Decimal(val value: Double) : SfBare

    data class Str(val value: String) : SfBare

    data class Token(val value: String) : SfBare

    class Bytes(val bytes: ByteArray) : SfBare {
        override fun equals(other: Any?) = other is Bytes && bytes.contentEquals(other.bytes)

        override fun hashCode() = bytes.contentHashCode()

        override fun toString() = "Bytes(${bytes.toHexString()})"
    }

    data class Bool(val value: Boolean) : SfBare
}

/** A dictionary member or inner-list entry: an [SfItem] or an [SfInnerList], each with parameters, in order. */
internal sealed interface SfMember {
    val params: Map<String, SfBare>
}

internal data class SfItem(val bare: SfBare, override val params: Map<String, SfBare> = emptyMap()) : SfMember

internal data class SfInnerList(val items: List<SfItem>, override val params: Map<String, SfBare> = emptyMap()) : SfMember

/**
 * RFC 8941 dictionaries, parsed (§4.2) and serialized (§4.1) in full, as RFC 9421 signatures need: their `sf` components cover a field as
 * re-serialized from its parsed form. A later duplicate key overwrites the earlier one's value in its place. Anything malformed throws
 * [IllegalArgumentException]. No Kotlin Multiplatform library implements structured fields, so this is ours.
 */
@Suppress("TooManyFunctions") // One serializer per RFC 8941 type, as the parser has one per production.
internal object StructuredFields {
    fun parseDictionary(value: String): Map<String, SfMember> = Parser(value).field { dictionary() }

    fun serializeDictionary(members: Map<String, SfMember>): String =
        members.entries.joinToString(", ") { (key, member) ->
            requireKey(key)
            if (member is SfItem && member.bare == SfBare.Bool(true)) key + parameters(member.params) else "$key=${serializeMember(member)}"
        }

    fun serializeInnerList(list: SfInnerList): String =
        list.items.joinToString(" ", "(", ")") { serializeItem(it) } + parameters(list.params)

    fun serializeItem(item: SfItem): String = bare(item.bare) + parameters(item.params)

    private fun serializeMember(member: SfMember): String =
        when (member) {
            is SfItem -> serializeItem(member)
            is SfInnerList -> serializeInnerList(member)
        }

    private fun parameters(params: Map<String, SfBare>): String =
        params.entries.joinToString("") { (key, value) ->
            requireKey(key)
            if (value == SfBare.Bool(true)) ";$key" else ";$key=${bare(value)}"
        }

    private const val MAX_INTEGER = 999_999_999_999_999L
    private const val MAX_DECIMAL_INTEGER_DIGITS = 12
    private const val MAX_DECIMAL_FRACTION_DIGITS = 3
    private const val MAX_DECIMAL_DIGITS = 16
    private const val MAX_INTEGER_DIGITS = 15
    private const val MILLI = 1000

    /** RFC 8941 byte sequences are padded base64 when written; unpadded is accepted when read. */
    private val base64 = Base64.Default.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

    private fun bare(value: SfBare): String =
        when (value) {
            is SfBare.Integer -> {
                require(abs(value.value) <= MAX_INTEGER) { "Integer out of range" }
                value.value.toString()
            }
            is SfBare.Decimal -> decimal(value.value)
            is SfBare.Str -> {
                require(value.value.all { it in ' '..'~' }) { "Not an RFC 8941 string" }
                "\"" + value.value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
            }
            is SfBare.Token -> {
                require(isToken(value.value)) { "Not an RFC 8941 token" }
                value.value
            }
            is SfBare.Bytes -> ":${Base64.Default.withPadding(Base64.PaddingOption.PRESENT).encode(value.bytes)}:"
            is SfBare.Bool -> if (value.value) "?1" else "?0"
        }

    /** Rounded half to even to three places (Kotlin's [round]), with at least one fractional digit. */
    private fun decimal(value: Double): String {
        val milli = round(value * MILLI).toLong()
        val whole = abs(milli) / MILLI
        require(whole.toString().length <= MAX_DECIMAL_INTEGER_DIGITS) { "Decimal out of range" }
        val fraction = (abs(milli) % MILLI).toString().padStart(MAX_DECIMAL_FRACTION_DIGITS, '0').trimEnd('0').ifEmpty { "0" }
        return (if (milli < 0) "-" else "") + "$whole.$fraction"
    }

    private fun requireKey(key: String) = require(isKey(key)) { "Not an RFC 8941 key: $key" }

    private fun isKey(key: String) =
        key.isNotEmpty() && (key[0] in 'a'..'z' || key[0] == '*') && key.all { it in 'a'..'z' || it in '0'..'9' || it in "_-.*" }

    private fun isToken(token: String) =
        token.isNotEmpty() && (token[0].isAsciiLetter() || token[0] == '*') && token.all { isTchar(it) || it == ':' || it == '/' }

    private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'

    private fun isTchar(c: Char) = c.isAsciiLetter() || c in '0'..'9' || c in "!#$%&'*+-.^_`|~"

    @Suppress("TooManyFunctions") // One per RFC 8941 production, plus the cursor helpers.
    private class Parser(private val input: String) {
        private var pos = 0

        /** Parses the whole field: leading and trailing spaces are discarded, and nothing may follow. */
        fun <T> field(parse: Parser.() -> T): T {
            skipSpaces()
            val value = parse()
            skipSpaces()
            require(pos == input.length) { "Unexpected trailing characters" }
            return value
        }

        fun dictionary(): Map<String, SfMember> {
            val members = linkedMapOf<String, SfMember>()
            while (pos < input.length) {
                val key = key()
                val member =
                    if (peek() == '=') {
                        pos++
                        itemOrInnerList()
                    } else {
                        SfItem(SfBare.Bool(true), parameters())
                    }
                members[key] = member
                skipOws()
                if (pos == input.length) break
                expect(',')
                skipOws()
                require(pos < input.length) { "Trailing comma" }
            }
            return members
        }

        private fun itemOrInnerList(): SfMember = if (peek() == '(') innerList() else item()

        private fun innerList(): SfInnerList {
            expect('(')
            val items = mutableListOf<SfItem>()
            while (true) {
                skipSpaces()
                if (peek() == ')') {
                    pos++
                    return SfInnerList(items, parameters())
                }
                items += item()
                require(peek() == ' ' || peek() == ')') { "Bad inner list" }
            }
        }

        private fun item(): SfItem = SfItem(bareItem(), parameters())

        private fun parameters(): Map<String, SfBare> {
            val params = linkedMapOf<String, SfBare>()
            while (peek() == ';') {
                pos++
                skipSpaces()
                val key = key()
                params[key] =
                    if (peek() == '=') {
                        pos++
                        bareItem()
                    } else {
                        SfBare.Bool(true)
                    }
            }
            return params
        }

        private fun key(): String {
            val start = pos
            require(peek()?.let { it in 'a'..'z' || it == '*' } == true) { "Bad key" }
            while (peek()?.let { it in 'a'..'z' || it in '0'..'9' || it in "_-.*" } == true) pos++
            return input.substring(start, pos)
        }

        private fun bareItem(): SfBare {
            val c = peek() ?: throw IllegalArgumentException("Missing value")
            return when {
                c == '"' -> string()
                c == ':' -> bytes()
                c == '?' -> boolean()
                c == '-' || c in '0'..'9' -> number()
                c.isAsciiLetter() || c == '*' -> token()
                else -> throw IllegalArgumentException("Bad item")
            }
        }

        private fun string(): SfBare {
            expect('"')
            val out = StringBuilder()
            while (true) {
                when (val c = next() ?: throw IllegalArgumentException("Unterminated string")) {
                    '"' -> return SfBare.Str(out.toString())
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

        private fun token(): SfBare {
            val start = pos
            pos++
            while (peek()?.let { isTchar(it) || it == ':' || it == '/' } == true) pos++
            return SfBare.Token(input.substring(start, pos))
        }

        private fun bytes(): SfBare {
            expect(':')
            val start = pos
            while (peek()?.let { it.isAsciiLetter() || it in '0'..'9' || it in "+/=" } == true) pos++
            val encoded = input.substring(start, pos)
            expect(':')
            return SfBare.Bytes(base64.decode(encoded))
        }

        private fun boolean(): SfBare {
            expect('?')
            return when (next()) {
                '1' -> SfBare.Bool(true)
                '0' -> SfBare.Bool(false)
                else -> throw IllegalArgumentException("Bad boolean")
            }
        }

        private fun number(): SfBare {
            val start = pos
            if (peek() == '-') pos++
            require(peek()?.isAsciiDigit() == true) { "Bad number" }
            var dot = -1
            while (peek()?.let { it.isAsciiDigit() || (it == '.' && dot < 0) } == true) {
                if (peek() == '.') dot = pos
                pos++
            }
            val text = input.substring(start, pos)
            val digits = text.trimStart('-')
            if (dot < 0) {
                require(digits.length <= MAX_INTEGER_DIGITS) { "Integer too long" }
                return SfBare.Integer(text.toLong())
            }
            val (whole, fraction) = digits.split('.')
            require(whole.length <= MAX_DECIMAL_INTEGER_DIGITS && fraction.length in 1..MAX_DECIMAL_FRACTION_DIGITS) { "Bad decimal" }
            require(digits.length <= MAX_DECIMAL_DIGITS) { "Decimal too long" }
            return SfBare.Decimal(text.toDouble())
        }

        private fun Char.isAsciiDigit() = this in '0'..'9'

        private fun peek(): Char? = input.getOrNull(pos)

        private fun next(): Char? = input.getOrNull(pos)?.also { pos++ }

        private fun expect(c: Char) {
            require(peek() == c) { "Expected '$c'" }
            pos++
        }

        private fun skipSpaces() {
            while (peek() == ' ') pos++
        }

        private fun skipOws() {
            while (peek() == ' ' || peek() == '\t') pos++
        }
    }
}
