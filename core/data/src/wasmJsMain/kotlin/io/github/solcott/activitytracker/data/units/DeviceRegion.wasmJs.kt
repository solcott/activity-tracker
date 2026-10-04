@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.solcott.activitytracker.data.units

// Returns a JS string: a Kotlin `String` return type here crashes the Wasm backend when linking webApp.
private fun navigatorLanguage(): JsString = js("navigator.language")

/** The region subtag of the browser's language, e.g. `US` from `en-US`. */
actual fun deviceRegion(): String? =
    navigatorLanguage().toString().split('-', '_').drop(1).firstOrNull { it.length == 2 && it.all(Char::isLetter) }
