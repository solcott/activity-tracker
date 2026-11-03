package io.github.solcott.activitytracker.app

import io.github.solcott.dataresult.Origin
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.LoadStatus

/** [this] in the phase it's in (loading, failed, loaded, and from where), holding what [transform] makes of its data. */
inline fun <T, R> ContentState<T>.mapContent(transform: (T) -> R): ContentState<R> = ContentState(transform(data), origin, status)

/** A form with nothing to load (a new item, or one the session can't read): loaded from the start. */
fun readyFormContent(): ContentState<Unit> = ContentState(Unit, Origin.Network, LoadStatus.Idle)

/**
 * [this] as a form's load (ui/content-loading): loading or failed until the form is [seeded] from it, then loaded for good. A later reload
 * and its failure aren't shown, since they never change what the athlete is editing.
 */
fun ContentState<*>.formContent(seeded: Boolean): ContentState<Unit> =
    when {
        seeded -> ContentState(Unit, origin ?: Origin.Network, LoadStatus.Idle)
        status is LoadStatus.Failed -> ContentState(Unit, null, status)
        else -> ContentState(Unit, null, LoadStatus.Loading)
    }
