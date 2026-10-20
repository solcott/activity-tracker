@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.solcott.activitytracker.data.units

// Returns a JS string: a Kotlin `String` return type here crashes the Wasm backend when linking webApp.
private fun localeOneAndAHalf(): JsString = js("(1.5).toLocaleString()")

private fun localeMillion(): JsString = js("(1000000).toLocaleString()")

/** The character after the 1 in the browser's rendering of 1,000,000: a million, since some locales (es, pl) leave 1000 ungrouped. */
internal actual fun platformGroupingSeparator(): Char? = localeMillion().toString().getOrNull(1)

/** The character between 1 and 5 in the browser's rendering of 1.5. */
actual fun decimalSeparator(): Char = localeOneAndAHalf().toString().getOrNull(1) ?: '.'
