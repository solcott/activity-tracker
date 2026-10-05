package io.github.solcott.activitytracker.activityfile

// Byte transfer between Kotlin/Wasm and JS. Element by element: Wasm memory isn't directly shareable with a JS typed array.

internal fun ByteArray.toUint8Array(): JsAny {
    val array = newUint8Array(size)
    forEachIndexed { i, b -> setByte(array, i, b) }
    return array
}

internal fun uint8ArrayToByteArray(array: JsAny): ByteArray = ByteArray(uint8Length(array)) { uint8At(array, it) }

@Suppress("UnusedParameter") // Used by the JS body.
private fun newUint8Array(size: Int): JsAny = js("new Uint8Array(size)")

@Suppress("UnusedParameter") // Used by the JS body. A negative byte wraps into 0..255 on assignment to a Uint8Array.
private fun setByte(array: JsAny, index: Int, value: Byte) {
    js("array[index] = value")
}

@Suppress("UnusedParameter") // Used by the JS body.
private fun uint8Length(array: JsAny): Int = js("array.length")

@Suppress("UnusedParameter") // Used by the JS body.
private fun uint8At(array: JsAny, index: Int): Byte = js("array[index] << 24 >> 24")
