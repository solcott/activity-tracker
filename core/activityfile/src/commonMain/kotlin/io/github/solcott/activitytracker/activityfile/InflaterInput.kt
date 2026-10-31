package io.github.solcott.activitytracker.activityfile

/**
 * Raw deflate (RFC 1951) decompressed as it's read, in common code: a zip member inside another zip arrives as a stream, which the
 * platforms' inflaters can't all take a piece at a time (the browser's `DecompressionStream` needs the whole input to say where output
 * ends). Reads [input] no further than the end of the deflate data, pushing back what it peeked, so a zip read as a stream finds the next
 * header. Fails with [ActivityFileException] on corrupt or truncated data.
 */
internal class InflaterInput(private val input: BufferedInput) : ByteInput {
    private val bits = BitReader(input)
    private val window = ByteArray(WINDOW_SIZE)
    private var windowEnd = 0
    /** Decoded bytes not yet read, ending at [windowEnd]. */
    private var pending = 0
    /** Bytes written so far, up to the window: how far back a copy may reach. */
    private var written = 0

    private var state = State.Header
    private var lastBlock = false
    private var storedLeft = 0
    private var literals: Huffman? = null
    private var distances: Huffman? = null

    override suspend fun read(into: ByteArray, offset: Int, length: Int): Int {
        if (length > 0) while (pending == 0 && state != State.Done) decodeSome()
        val count = minOf(length, pending)
        if (count == 0) return if (length == 0) 0 else -1
        var from = (windowEnd - pending) and WINDOW_MASK
        repeat(count) { i ->
            into[offset + i] = window[from]
            from = (from + 1) and WINDOW_MASK
        }
        pending -= count
        return count
    }

    /** Decodes until [OUTPUT_TARGET] bytes are pending, the block ends or the stream does. */
    private suspend fun decodeSome() {
        when (state) {
            State.Header -> readBlockHeader()
            State.Stored -> copyStored()
            State.Huffman -> decodeHuffman()
            State.Done -> Unit
        }
    }

    @Suppress("MagicNumber") // RFC 1951 3.2.3 and 3.2.4: the block header's fields and block types.
    private suspend fun readBlockHeader() {
        input.ensure(LOOKAHEAD_BYTES)
        lastBlock = bits.take(1) == 1
        when (bits.take(2)) {
            0 -> {
                // Stored: skip to a byte boundary, then LEN and its complement.
                bits.alignToByte()
                val length = bits.take(16)
                val complement = bits.take(16)
                if (length != complement.inv() and 0xFFFF) corrupt()
                storedLeft = length
                state = State.Stored
            }
            1 -> {
                literals = FIXED_LITERALS
                distances = FIXED_DISTANCES
                state = State.Huffman
            }
            2 -> {
                readDynamicTables()
                state = State.Huffman
            }
            else -> corrupt()
        }
    }

    private suspend fun copyStored() {
        while (storedLeft > 0 && pending < OUTPUT_TARGET) {
            // Whole bytes left in the bit buffer come first, then the input.
            val byte =
                if (bits.wholeBytes > 0) {
                    bits.take(BITS_PER_BYTE)
                } else {
                    if (!input.ensure(1)) truncated()
                    input.nextBuffered()
                }
            put(byte)
            storedLeft--
        }
        if (storedLeft == 0) endBlock()
    }

    private suspend fun decodeHuffman() {
        val literals = checkNotNull(literals)
        val distances = checkNotNull(distances)
        while (state == State.Huffman && pending < OUTPUT_TARGET) {
            input.ensure(LOOKAHEAD_BYTES)
            val symbol = bits.decode(literals)
            when {
                symbol < END_OF_BLOCK -> put(symbol)
                symbol == END_OF_BLOCK -> endBlock()
                else -> {
                    val lengthIndex = symbol - FIRST_LENGTH_SYMBOL
                    if (lengthIndex >= LENGTH_BASE.size) corrupt()
                    val length = LENGTH_BASE[lengthIndex] + bits.take(LENGTH_EXTRA[lengthIndex])
                    val distanceSymbol = bits.decode(distances)
                    if (distanceSymbol >= DISTANCE_BASE.size) corrupt()
                    val distance = DISTANCE_BASE[distanceSymbol] + bits.take(DISTANCE_EXTRA[distanceSymbol])
                    if (distance > written) corrupt()
                    var from = (windowEnd - distance) and WINDOW_MASK
                    repeat(length) {
                        put(window[from].toInt())
                        from = (from + 1) and WINDOW_MASK
                    }
                }
            }
        }
    }

    private fun put(byte: Int) {
        window[windowEnd] = byte.toByte()
        windowEnd = (windowEnd + 1) and WINDOW_MASK
        pending++
        if (written < WINDOW_SIZE) written++
    }

    private fun endBlock() {
        if (!lastBlock) {
            state = State.Header
        } else {
            state = State.Done
            // Give back whole bytes peeked past the end, so the next zip header reads from where the deflate data ended.
            input.pushBack(bits.takeLeftover())
        }
    }

    @Suppress("MagicNumber") // RFC 1951 3.2.7: the dynamic header's field widths, offsets and repeat codes.
    private suspend fun readDynamicTables() {
        input.ensure(LOOKAHEAD_BYTES)
        val literalCount = bits.take(5) + 257
        val distanceCount = bits.take(5) + 1
        val codeLengthCount = bits.take(4) + 4
        if (literalCount > MAX_LITERALS || distanceCount > MAX_DISTANCES) corrupt()
        val codeLengthLengths = IntArray(CODE_LENGTH_ORDER.size)
        for (i in 0 until codeLengthCount) {
            input.ensure(LOOKAHEAD_BYTES)
            codeLengthLengths[CODE_LENGTH_ORDER[i]] = bits.take(3)
        }
        val codeLengths = Huffman.of(codeLengthLengths) ?: corrupt()
        val lengths = IntArray(literalCount + distanceCount)
        var i = 0
        fun repeatLength(times: Int, length: Int) =
            repeat(times) {
                if (i >= lengths.size) corrupt()
                lengths[i++] = length
            }
        while (i < lengths.size) {
            input.ensure(LOOKAHEAD_BYTES)
            when (val symbol = bits.decode(codeLengths)) {
                in 0..15 -> lengths[i++] = symbol
                16 -> repeatLength(3 + bits.take(2), if (i == 0) corrupt() else lengths[i - 1])
                17 -> repeatLength(3 + bits.take(3), 0)
                else -> repeatLength(11 + bits.take(7), 0)
            }
        }
        if (lengths[END_OF_BLOCK] == 0) corrupt()
        literals = Huffman.of(lengths.copyOfRange(0, literalCount)) ?: corrupt()
        // A block of only literals may have no distance codes at all.
        distances = Huffman.of(lengths.copyOfRange(literalCount, lengths.size)) ?: Huffman.EMPTY
    }

    private enum class State {
        Header,
        Stored,
        Huffman,
        Done,
    }

    private companion object {
        const val WINDOW_SIZE = 32 * 1024
        const val WINDOW_MASK = WINDOW_SIZE - 1

        /** Well under the window less the longest copy, so pending output is never overwritten. */
        const val OUTPUT_TARGET = 16 * 1024

        /** Enough for one literal/length code, its extra bits, a distance code and its extra bits: 48 bits. */
        const val LOOKAHEAD_BYTES = 8

        const val END_OF_BLOCK = 256
        const val FIRST_LENGTH_SYMBOL = 257
        const val MAX_LITERALS = 286
        const val MAX_DISTANCES = 30

        val CODE_LENGTH_ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)
        val LENGTH_BASE =
            intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
        val LENGTH_EXTRA = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
        /** 1, 2, 3, 4, then each extra bit count twice over, doubling the step: 5, 7, 9, 13, … 24577. */
        val DISTANCE_EXTRA = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)
        val DISTANCE_BASE =
            IntArray(DISTANCE_EXTRA.size).also { base ->
                base[0] = 1
                for (i in 1 until base.size) base[i] = base[i - 1] + (1 shl DISTANCE_EXTRA[i - 1])
            }

        /** RFC 1951 3.2.6's fixed codes. */
        val FIXED_LITERALS: Huffman =
            checkNotNull(
                Huffman.of(
                    IntArray(288) { symbol ->
                        when {
                            symbol < 144 -> 8
                            symbol < 256 -> 9
                            symbol < 280 -> 7
                            else -> 8
                        }
                    }
                )
            )
        val FIXED_DISTANCES: Huffman = checkNotNull(Huffman.of(IntArray(30) { 5 }))
    }
}

/**
 * Deflate's bit stream: least significant bit first, over [input]'s buffered bytes, which the inflater fills before each code. Past the end
 * of the input it pads with zero bits so a code can be looked up, but refuses to use them.
 */
private class BitReader(private val input: BufferedInput) {
    private var buffer = 0L
    private var count = 0
    /** Zero bits added past the end of [input]. */
    private var padded = 0

    /** Whole bytes of real data held. */
    val wholeBytes: Int
        get() = (count - padded) / BITS_PER_BYTE

    /** The next [bits] bits as a number. */
    fun take(bits: Int): Int {
        if (bits == 0) return 0
        fill(bits)
        val value = (buffer and ((1L shl bits) - 1)).toInt()
        consume(bits)
        return value
    }

    /** The next symbol in [code]. */
    fun decode(code: Huffman): Int {
        fill(code.maxLength)
        val entry = code.table[(buffer and ((1L shl code.maxLength) - 1)).toInt()]
        if (entry == 0) corrupt()
        consume(entry and Huffman.LENGTH_MASK)
        return entry ushr Huffman.LENGTH_BITS
    }

    /** Drops the bits left of the current byte (padding is whole bytes, so it doesn't move the boundary). */
    fun alignToByte() = consume(count % BITS_PER_BYTE)

    /** Ends the stream: the whole bytes of real data held, which were read past its end, and nothing held after. */
    fun takeLeftover(): ByteArray {
        alignToByte()
        val leftover = ByteArray(wholeBytes) { i -> ((buffer ushr (i * BITS_PER_BYTE)) and BYTE_MASK.toLong()).toByte() }
        buffer = 0
        count = 0
        padded = 0
        return leftover
    }

    private fun fill(bits: Int) {
        while (count < bits) {
            val byte = input.nextBuffered()
            if (byte < 0) padded += BITS_PER_BYTE
            buffer = buffer or (byte.coerceAtLeast(0).toLong() shl count)
            count += BITS_PER_BYTE
        }
    }

    private fun consume(bits: Int) {
        if (bits <= 0) return
        if (bits > count - padded) truncated()
        buffer = buffer ushr bits
        count -= bits
    }
}

/** A canonical Huffman code as a table indexed by the next [maxLength] bits: each entry is its symbol, shifted, and its length. */
private class Huffman(val maxLength: Int, val table: IntArray) {
    companion object {
        const val LENGTH_BITS = 4
        const val LENGTH_MASK = (1 shl LENGTH_BITS) - 1
        private const val MAX_CODE_BITS = 15

        val EMPTY = Huffman(1, IntArray(2))

        /** The code with these code [lengths] per symbol, or null if there are none; fails if they're over-subscribed. */
        fun of(lengths: IntArray): Huffman? {
            val maxLength = lengths.maxOrNull() ?: 0
            if (maxLength == 0) return null
            val counts = IntArray(MAX_CODE_BITS + 1)
            lengths.forEach { counts[it]++ }
            counts[0] = 0
            var left = 1
            for (length in 1..MAX_CODE_BITS) {
                left = (left shl 1) - counts[length]
                if (left < 0) corrupt()
            }
            val next = IntArray(MAX_CODE_BITS + 2)
            for (length in 1..MAX_CODE_BITS) next[length + 1] = (next[length] + counts[length]) shl 1
            val table = IntArray(1 shl maxLength)
            lengths.forEachIndexed { symbol, length ->
                if (length > 0) {
                    var index = reverse(next[length]++, length)
                    while (index < table.size) {
                        table[index] = (symbol shl LENGTH_BITS) or length
                        index += 1 shl length
                    }
                }
            }
            return Huffman(maxLength, table)
        }

        private fun reverse(code: Int, length: Int): Int {
            var result = 0
            var rest = code
            repeat(length) {
                result = (result shl 1) or (rest and 1)
                rest = rest ushr 1
            }
            return result
        }
    }
}

private fun corrupt(): Nothing = activityFileError("Corrupt compressed data")

private fun truncated(): Nothing = activityFileError("Truncated compressed data")
