package io.github.solcott.activitytracker.data.text

import platform.Foundation.NSDiacriticInsensitiveSearch
import platform.Foundation.NSString
import platform.Foundation.stringByFoldingWithOptions

@Suppress("CAST_NEVER_SUCCEEDS")
actual fun stripDiacritics(text: String): String =
    (text as NSString).stringByFoldingWithOptions(NSDiacriticInsensitiveSearch, locale = null)
