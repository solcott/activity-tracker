@file:Suppress("TooManyFunctions") // The view kit: one small function per element a page draws.

package io.github.solcott.activitytracker.appview.admin

import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.html.FORM
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.TBODY
import kotlinx.html.TR
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.hiddenInput
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.time
import kotlinx.html.tr

// The admin site's view kit (admin/admin-site): every page draws states, times, numbers, tiles and action forms through these, so they
// look the same everywhere and admin.css has stable classes to style. Nothing here may emit a style attribute: the CSP refuses it.

/** A state with a fixed meaning, as a badge whose colour gives [status] and whose text names the state, so colour is never alone. */
internal fun FlowContent.badge(text: String, status: TileStatus?) {
    span("badge ${status.css}") { +text }
}

/** The CSS class of a status; none has its own neutral look. */
internal val TileStatus?.css: String
    get() = this?.name?.lowercase() ?: "none"

/** [at] relative to [now] (`3 min ago`, `in 2 h`), with its exact UTC time in `datetime` and `title`; `—` when there is none. */
internal fun FlowContent.at(at: Instant?, now: Instant) {
    if (at == null) {
        +EMPTY
        return
    }
    val exact = exact(at)
    time {
        attributes["datetime"] = exact
        attributes["title"] = exact
        +relative(at, now)
    }
}

/** [at] relative to [now]: `just now`, `12 min ago`, `in 3 h`. */
internal fun relative(at: Instant, now: Instant): String {
    val d = now - at
    return when {
        d.absoluteValue < JUST_NOW -> "just now"
        d.isPositive() -> "${durationText(d)} ago"
        else -> "in ${durationText(-d)}"
    }
}

/** An instant to the second, in UTC: `2026-11-19T23:59:59Z`. */
internal fun exact(at: Instant): String = Instant.fromEpochSeconds(at.epochSeconds).toString()

/** A duration in its two largest units, to the second: `45 s`, `12 min`, `3 h 41 min`, `2 d 4 h`. */
internal fun durationText(d: Duration): String {
    val s = d.inWholeSeconds.coerceAtLeast(0)
    val days = s / DAY
    val hours = s % DAY / HOUR
    val minutes = s % HOUR / MINUTE
    val seconds = s % MINUTE
    return when {
        days > 0 -> if (hours > 0) "$days d $hours h" else "$days d"
        hours > 0 -> if (minutes > 0) "$hours h $minutes min" else "$hours h"
        minutes > 0 -> "$minutes min"
        else -> "$seconds s"
    }
}

/** Epoch millis as an instant, for the many columns stored that way. */
internal fun instantOfMs(epochMs: Long?): Instant? = epochMs?.let(Instant::fromEpochMilliseconds)

/** A size in binary units: `512 B`, `1.5 MiB`. */
internal fun bytesText(n: Long): String {
    var value = n.toDouble()
    var unit = 0
    while (value >= KIB && unit < UNITS.lastIndex) {
        value /= KIB
        unit++
    }
    return if (unit == 0) "$n B" else String.format(Locale.ROOT, "%.1f %s", value, UNITS[unit])
}

/**
 * A table of [headers], wrapped so a wide one scrolls within itself rather than the page sideways. The [numeric] headers align right, as
 * their cells do when drawn with [num].
 */
internal fun FlowContent.dataTable(headers: List<String>, numeric: Set<String> = emptySet(), body: TBODY.() -> Unit) {
    div("table-wrap") {
        table {
            thead { tr { headers.forEach { h -> if (h in numeric) th(classes = "num") { +h } else th { +h } } } }
            tbody { body() }
        }
    }
}

/** A count or other number, aligned right in tabular figures. */
internal fun TR.num(value: Any) {
    td("num") { +value.toString() }
}

/** A cell holding a time relative to [now]. */
internal fun TR.timeCell(at: Instant?, now: Instant) {
    td("time") { at(at, now) }
}

/** A row of stat tiles in place of a bare definition list. */
internal fun FlowContent.stats(body: StatRow.() -> Unit) {
    div("stats") { StatRow(this).body() }
}

/** What [stats] draws into: each [stat] is a label over a value, tinted by its status when it has one. */
internal class StatRow(private val parent: FlowContent) {
    fun stat(label: String, value: String, status: TileStatus? = null) {
        parent.div("stat ${status.css}") {
            span("label") { +label }
            span("value") { +value }
        }
    }

    fun stat(label: String, value: FlowContent.() -> Unit) {
        parent.div("stat none") {
            span("label") { +label }
            span("value") { value() }
        }
    }
}

/** Where htmx puts an action's answer: [id] is what the page's action forms target. */
internal fun FlowContent.outcome(id: String) {
    div("outcome-slot") {
        attributes["id"] = id
        attributes["aria-live"] = "polite"
    }
}

/**
 * The one action form: it posts to [action] by htmx, and plainly without it (the CSRF field is in the form for that), with [target] as its
 * `target` field. htmx puts the answer in the element with id [outcome], or in the form's place when [outcome] is null. A [confirm] text
 * marks the action destructive: its button is styled apart and htmx asks that question before posting.
 */
@Suppress("LongParameterList") // Every variant of the site's action forms, each with a default.
internal fun FlowContent.actionForm(
    action: String,
    label: String,
    csrf: String,
    outcome: String?,
    target: String? = null,
    confirm: String? = null,
    inline: Boolean = false,
    fields: FORM.() -> Unit = {},
) {
    form(action = action, method = FormMethod.post, classes = if (inline) "action inline" else "action") {
        attributes["hx-post"] = action
        if (outcome == null) {
            attributes["hx-target"] = "this"
            attributes["hx-swap"] = "outerHTML"
        } else attributes["hx-target"] = "#$outcome"
        confirm?.let { attributes["hx-confirm"] = it }
        hiddenInput(name = AdminSite.CSRF_FIELD) { value = csrf }
        target?.let { hiddenInput(name = "target") { value = it } }
        fields()
        button(classes = if (confirm != null) "danger" else null) { +label }
    }
}

private const val EMPTY = "—"
private val JUST_NOW = 5_000.milliseconds
private const val MINUTE = 60L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR
private const val KIB = 1024.0
private val UNITS = listOf("B", "KiB", "MiB", "GiB", "TiB")
