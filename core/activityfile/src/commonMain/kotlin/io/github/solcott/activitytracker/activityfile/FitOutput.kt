package io.github.solcott.activitytracker.activityfile

import kotlin.time.Instant

/** A FIT base type: its number in a definition message, its size in bytes, the value that means "no value", and the valid range. */
internal class FitBaseType private constructor(val id: Int, val size: Int, val invalid: Long, val min: Long, val max: Long) {
    companion object {
        val ENUM = FitBaseType(0x00, 1, 0xFF, 0, 0xFE)
        val SINT8 = FitBaseType(0x01, 1, 0x7F, -0x80, 0x7E)
        val UINT8 = FitBaseType(0x02, 1, 0xFF, 0, 0xFE)
        val SINT16 = FitBaseType(0x83, 2, 0x7FFF, -0x8000, 0x7FFE)
        val UINT16 = FitBaseType(0x84, 2, 0xFFFF, 0, 0xFFFE)
        val SINT32 = FitBaseType(0x85, 4, 0x7FFF_FFFF, -0x8000_0000, 0x7FFF_FFFE)
        val UINT32 = FitBaseType(0x86, 4, 0xFFFF_FFFF, 0, 0xFFFF_FFFE)
        val UINT8Z = FitBaseType(0x0A, 1, 0, 1, 0xFF)
        val UINT16Z = FitBaseType(0x8B, 2, 0, 1, 0xFFFF)
        val UINT32Z = FitBaseType(0x8C, 4, 0, 1, 0xFFFF_FFFF)
        /** A float's raw bits ([floatBits]); all ones (a NaN) is invalid. */
        val FLOAT32 = FitBaseType(0x88, 4, 0xFFFF_FFFF, 0, 0xFFFF_FFFE)

        /** [value] as [FLOAT32] takes it. */
        fun floatBits(value: Double): Long = value.toFloat().toRawBits().toUInt().toLong()
    }
}

/** One field of a message: its number in the message's profile, its base type, and its raw (already scaled) value, or null for none. */
internal data class FitField(val num: Int, val type: FitBaseType, val value: Long?)

/** A data message of global message number [global]. Two messages with the same fields in the same order share a definition. */
internal data class FitMessage(val global: Int, val fields: List<FitField>)

/**
 * Writes FIT messages into a FIT file: the 14-byte header, a definition before each new message layout, little-endian data messages with
 * normal headers, and the trailing CRC. Only what [FitWriter] needs: single-value numeric fields, no strings, arrays, compressed timestamps
 * or developer fields.
 */
internal class FitOutput {
    private val data = FitBytes()
    private val localTypes = mutableMapOf<Pair<Int, List<Pair<Int, FitBaseType>>>, Int>()

    fun write(message: FitMessage) {
        val layout = message.global to message.fields.map { it.num to it.type }
        val local =
            localTypes.getOrPut(layout) {
                check(localTypes.size < MAX_LOCAL_TYPES) { "More than $MAX_LOCAL_TYPES message layouts" }
                localTypes.size.also { writeDefinition(it, message) }
            }
        data.byte(local)
        for (field in message.fields) {
            data.value(field.type, field.value?.takeIf { it in field.type.min..field.type.max } ?: field.type.invalid)
        }
    }

    /** The whole file: header, messages and CRC. */
    fun finish(): ByteArray {
        val file = FitBytes()
        file.byte(HEADER_SIZE)
        file.byte(PROTOCOL_VERSION)
        file.value(FitBaseType.UINT16, PROFILE_VERSION.toLong())
        file.value(FitBaseType.UINT32, data.size.toLong())
        ".FIT".encodeToByteArray().forEach { file.byte(it.toInt()) }
        file.value(FitBaseType.UINT16, file.crc().toLong())
        file.append(data)
        file.value(FitBaseType.UINT16, file.crc().toLong())
        return file.toByteArray()
    }

    private fun writeDefinition(local: Int, message: FitMessage) {
        data.byte(DEFINITION_FLAG or local)
        data.byte(0) // reserved
        data.byte(0) // little-endian
        data.value(FitBaseType.UINT16, message.global.toLong())
        data.byte(message.fields.size)
        for (field in message.fields) {
            data.byte(field.num)
            data.byte(field.type.size)
            data.byte(field.type.id)
        }
    }

    companion object {
        const val HEADER_SIZE = 14
        /** Protocol 2.0, as Garmin's SDKs write. */
        const val PROTOCOL_VERSION = 0x20
        /** The FIT profile the field numbers come from: Garmin's SDK 21.218, which the tests decode with. */
        const val PROFILE_VERSION = 21218
        private const val DEFINITION_FLAG = 0x40
        private const val MAX_LOCAL_TYPES = 16

        /** FIT times are seconds since 1989-12-31T00:00:00Z. */
        private const val FIT_EPOCH_SECONDS = 631_065_600L

        fun timestamp(instant: Instant): Long = instant.epochSeconds - FIT_EPOCH_SECONDS
    }
}

/** A growable byte buffer that writes values little-endian. */
private class FitBytes {
    private var bytes = ByteArray(INITIAL_CAPACITY)
    var size = 0
        private set

    fun byte(value: Int) {
        ensure(1)
        bytes[size++] = value.toByte()
    }

    fun value(type: FitBaseType, value: Long) {
        ensure(type.size)
        for (i in 0 until type.size) bytes[size++] = (value ushr (BITS_PER_BYTE * i)).toByte()
    }

    fun append(other: FitBytes) {
        ensure(other.size)
        other.bytes.copyInto(bytes, size, 0, other.size)
        size += other.size
    }

    /** The CRC of everything written so far. */
    fun crc(): Int = FitCrc.of(bytes, 0, size)

    fun toByteArray(): ByteArray = bytes.copyOf(size)

    private fun ensure(count: Int) {
        if (size + count > bytes.size) bytes = bytes.copyOf(maxOf(bytes.size * 2, size + count))
    }

    private companion object {
        const val INITIAL_CAPACITY = 4096
        const val BITS_PER_BYTE = 8
    }
}

/** FIT's CRC-16, computed a nibble at a time as the FIT protocol specifies. */
internal object FitCrc {
    private val TABLE =
        intArrayOf(
            0x0000,
            0xCC01,
            0xD801,
            0x1400,
            0xF001,
            0x3C00,
            0x2800,
            0xE401,
            0xA001,
            0x6C00,
            0x7800,
            0xB401,
            0x5000,
            0x9C01,
            0x8801,
            0x4400,
        )

    fun of(bytes: ByteArray, from: Int, to: Int): Int {
        var crc = 0
        for (i in from until to) crc = update(crc, bytes[i].toInt() and BYTE_MASK)
        return crc
    }

    private fun update(crc: Int, byte: Int): Int {
        var c = crc
        c = (c shr NIBBLE and LOW_12) xor TABLE[c and NIBBLE_MASK] xor TABLE[byte and NIBBLE_MASK]
        c = (c shr NIBBLE and LOW_12) xor TABLE[c and NIBBLE_MASK] xor TABLE[byte shr NIBBLE and NIBBLE_MASK]
        return c
    }

    private const val BYTE_MASK = 0xFF
    private const val NIBBLE = 4
    private const val NIBBLE_MASK = 0xF
    private const val LOW_12 = 0x0FFF
}
