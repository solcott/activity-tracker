package io.github.solcott.activitytracker.lexicon

import kotlin.random.Random
import kotlin.time.Clock

/**
 * Timestamp identifier used as a record key. Generating rkeys client-side makes repo writes idempotent: a retried `putRecord` with the same
 * TID overwrites instead of duplicating.
 */
object Tid {
    private const val ALPHABET = "234567abcdefghijklmnopqrstuvwxyz"

    // 64-bit layout: 53 bits of microseconds, then a 10-bit clock id; base32-sortable as 13 chars of 5 bits.
    private const val CLOCK_ID_BITS = 10
    private const val LENGTH = 13
    private const val BITS_PER_CHAR = 5
    private const val CHAR_MASK = (1L shl BITS_PER_CHAR) - 1
    private const val MICROS_PER_SECOND = 1_000_000L
    private const val NANOS_PER_MICRO = 1_000

    private val clockId = Random.nextInt(0, 1 shl CLOCK_ID_BITS)
    private var last = 0L

    fun next(clock: Clock = Clock.System): String {
        val now = clock.now()
        var micros = now.epochSeconds * MICROS_PER_SECOND + now.nanosecondsOfSecond / NANOS_PER_MICRO
        if (micros <= last) micros = last + 1
        last = micros
        return encode((micros shl CLOCK_ID_BITS) or clockId.toLong())
    }

    internal fun encode(value: Long): String {
        val chars = CharArray(LENGTH)
        var v = value
        for (i in LENGTH - 1 downTo 0) {
            chars[i] = ALPHABET[(v and CHAR_MASK).toInt()]
            v = v ushr BITS_PER_CHAR
        }
        return chars.concatToString()
    }
}
