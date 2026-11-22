@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.solcott.activitytracker.data.text

// Returns a JS string: a Kotlin `String` return type here crashes the Wasm backend when linking webApp. `text` is used inside js(), which
// detekt can't see.
@Suppress("UnusedParameter") private fun withoutMarks(text: JsString): JsString = js("text.normalize('NFD').replace(/\\p{M}/gu, '')")

actual fun stripDiacritics(text: String): String = withoutMarks(text.toJsString()).toString()
