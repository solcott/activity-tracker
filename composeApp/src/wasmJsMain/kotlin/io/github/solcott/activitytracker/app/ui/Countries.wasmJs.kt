@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.solcott.activitytracker.app.ui

// Returns a JS string: a Kotlin `String` return type here crashes the Wasm backend when linking webApp. `code` is used inside js(), which
// detekt can't see.
@Suppress("UnusedParameter")
private fun regionName(code: JsString): JsString? = js("new Intl.DisplayNames(undefined, { type: 'region' }).of(code) ?? null")

actual fun countryName(code: String): String = regionName(code.toJsString())?.toString() ?: code
