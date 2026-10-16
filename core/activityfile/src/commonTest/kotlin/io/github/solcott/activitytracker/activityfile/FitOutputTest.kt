package io.github.solcott.activitytracker.activityfile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class FitOutputTest {
    private fun file(): ByteArray {
        val out = FitOutput()
        out.write(FitMessage(0, listOf(FitField(0, FitBaseType.ENUM, 4), FitField(1, FitBaseType.UINT16, 255))))
        out.write(FitMessage(0, listOf(FitField(0, FitBaseType.ENUM, 4), FitField(1, FitBaseType.UINT16, null))))
        return out.finish()
    }

    private fun ByteArray.u(i: Int) = this[i].toInt() and 0xFF

    @Test
    fun headerNamesTheProtocolProfileAndDataSize() {
        val bytes = file()
        assertEquals(14, bytes.u(0))
        assertEquals(0x20, bytes.u(1))
        assertEquals(FitOutput.PROFILE_VERSION, bytes.u(2) or (bytes.u(3) shl 8))
        // One definition (6 + 2 × 3 bytes) and two data messages (1 + 1 + 2 bytes each).
        val dataSize = bytes.u(4) or (bytes.u(5) shl 8) or (bytes.u(6) shl 16) or (bytes.u(7) shl 24)
        assertEquals(12 + 4 + 4, dataSize)
        assertEquals(".FIT", bytes.copyOfRange(8, 12).decodeToString())
        assertEquals(14 + dataSize + 2, bytes.size)
    }

    @Test
    fun crcsCheckOut() {
        val bytes = file()
        // A CRC run over the data and its own CRC comes out zero.
        assertEquals(0, FitCrc.of(bytes, 0, 14))
        assertEquals(0, FitCrc.of(bytes, 0, bytes.size))
    }

    @Test
    fun aLayoutIsDefinedOnceAndAbsentValuesAreInvalid() {
        val bytes = file()
        assertEquals(0x40, bytes.u(14)) // definition of local type 0
        assertEquals(0, bytes.u(26)) // its first data message
        assertEquals(listOf(4, 0xFF, 0x00), (27..29).map { bytes.u(it) })
        assertEquals(0, bytes.u(30)) // the second reuses the definition
        assertEquals(listOf(4, 0xFF, 0xFF), (31..33).map { bytes.u(it) })
    }

    @Test
    fun outOfRangeValuesAreInvalid() {
        val out = FitOutput()
        out.write(FitMessage(20, listOf(FitField(3, FitBaseType.UINT8, 300), FitField(13, FitBaseType.SINT8, -5))))
        val bytes = out.finish()
        val data = 14 + 6 + 2 * 3 + 1
        assertEquals(listOf(0xFF, 0xFB), (data..data + 1).map { bytes.u(it) })
    }

    @Test
    fun timestampsCountFromTheFitEpoch() {
        assertEquals(0, FitOutput.timestamp(Instant.parse("1989-12-31T00:00:00Z")))
        assertEquals(86_400, FitOutput.timestamp(Instant.parse("1990-01-01T00:00:00Z")))
    }
}
