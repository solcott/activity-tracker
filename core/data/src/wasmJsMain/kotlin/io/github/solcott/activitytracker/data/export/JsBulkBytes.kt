@file:OptIn(UnsafeWasmMemoryApi::class)

package io.github.solcott.activitytracker.data.export

import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

/**
 * [bytes] as a new JS `Uint8Array`, copied through Wasm linear memory: one store per byte inside Wasm and one bulk `slice` in JS, rather
 * than a JS call per byte, which a multi-gigabyte export can't afford.
 */
internal fun toUint8ArrayBulk(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): JsAny =
    withScopedMemoryAllocator { allocator ->
        val pointer = allocator.allocate(length)
        for (i in 0 until length) (pointer + i).storeByte(bytes[offset + i])
        sliceOfMemory(pointer.address.toInt(), length)
    }

/** [array] (a JS `Uint8Array`) as a new ByteArray, copied through Wasm linear memory the same way. */
internal fun byteArrayOfBulk(array: JsAny): ByteArray {
    val length = uint8Length(array)
    return withScopedMemoryAllocator { allocator ->
        val pointer = allocator.allocate(length)
        copyIntoMemory(array, pointer.address.toInt())
        ByteArray(length) { (pointer + it).loadByte() }
    }
}

@Suppress("UnusedParameter") // Used by the JS body.
private fun sliceOfMemory(address: Int, length: Int): JsAny = js("new Uint8Array(wasmExports.memory.buffer, address, length).slice()")

@Suppress("UnusedParameter") // Used by the JS body.
private fun copyIntoMemory(array: JsAny, address: Int) {
    js("new Uint8Array(wasmExports.memory.buffer, address, array.length).set(array)")
}

@Suppress("UnusedParameter") // Used by the JS body.
private fun uint8Length(array: JsAny): Int = js("array.length")
