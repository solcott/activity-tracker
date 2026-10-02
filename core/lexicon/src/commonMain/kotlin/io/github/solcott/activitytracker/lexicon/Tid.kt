package io.github.solcott.activitytracker.lexicon

import kotlin.random.Random
import kotlin.time.Clock

/**
 * Timestamp identifier used as a record key. Generating rkeys client-side makes repo writes idempotent: a retried `putRecord` with the same
 * TID overwrites instead of duplicating.
 */
object Tid {
    private const val ALPHABET = "234567abcdefghijklmnopqrstuvwxyz"
    private val clockId = Random.nextInt(0, 1024)
    private var last = 0L

    fun next(clock: Clock = Clock.System): String {
        val now = clock.now()
        var micros = now.epochSeconds * 1_000_000 + now.nanosecondsOfSecond / 1_000
        if (micros <= last) micros = last + 1
        last = micros
        return encode((micros shl 10) or clockId.toLong())
    }

    internal fun encode(value: Long): String {
        val chars = CharArray(13)
        var v = value
        for (i in 12 downTo 0) {
            chars[i] = ALPHABET[(v and 31).toInt()]
            v = v ushr 5
        }
        return chars.concatToString()
    }
}
