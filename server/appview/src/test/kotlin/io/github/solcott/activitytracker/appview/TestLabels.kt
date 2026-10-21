package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.labels.Label

/**
 * An unsigned label from [src], as `LabelStore` takes them after the subscriber verified them; [extra] adds fields such as `neg`, `cid` or
 * `exp`.
 */
fun testLabel(src: String, uri: String, value: String, cts: String = "2026-10-21T10:00:00Z", extra: Map<String, Any> = emptyMap()) =
    Label(mapOf("ver" to 1L, "src" to src, "uri" to uri, "val" to value, "cts" to cts) + extra)

/** `neg: true` when [neg], and `cid` when given, as [testLabel]'s extra fields. */
fun labelExtras(neg: Boolean = false, cid: String? = null, exp: String? = null): Map<String, Any> = buildMap {
    if (neg) put("neg", true)
    cid?.let { put("cid", it) }
    exp?.let { put("exp", it) }
}
