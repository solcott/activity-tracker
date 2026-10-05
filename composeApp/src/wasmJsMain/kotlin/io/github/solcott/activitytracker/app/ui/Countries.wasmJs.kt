@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.solcott.activitytracker.app.ui

// Returns a JS string: a Kotlin `String` return type here crashes the Wasm backend when linking webApp. `code` is used inside js(), which
// detekt can't see.
@Suppress("UnusedParameter")
private fun regionName(code: JsString): JsString? = js("new Intl.DisplayNames(undefined, { type: 'region' }).of(code) ?? null")

actual fun countryName(code: String): String = regionName(code.toJsString())?.toString() ?: code

// Same JsString constraint as regionName. `text` is used inside js().
@Suppress("UnusedParameter") private fun withoutMarks(text: JsString): JsString = js("text.normalize('NFD').replace(/\\p{M}/gu, '')")

actual fun stripDiacritics(text: String): String = withoutMarks(text.toJsString()).toString()
