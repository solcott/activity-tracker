@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.solcott.activitytracker.data.units

// Returns a JS string: a Kotlin `String` return type here crashes the Wasm backend when linking webApp.
private fun localeOneAndAHalf(): JsString = js("(1.5).toLocaleString()")

/** The character between 1 and 5 in the browser's rendering of 1.5. */
actual fun decimalSeparator(): Char = localeOneAndAHalf().toString().getOrNull(1) ?: '.'
