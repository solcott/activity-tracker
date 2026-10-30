package io.github.solcott.activitytracker.app.ui

import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSDiacriticInsensitiveSearch
import platform.Foundation.NSLocale
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.currentLocale
import platform.Foundation.localizedStringForCountryCode
import platform.Foundation.stringByFoldingWithOptions

actual fun countryName(code: String): String = NSLocale.currentLocale.localizedStringForCountryCode(code) ?: code

@OptIn(BetaInteropApi::class) // NSString.create
actual fun stripDiacritics(text: String): String =
    NSString.create(string = text).stringByFoldingWithOptions(NSDiacriticInsensitiveSearch, locale = null)
