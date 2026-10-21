package io.github.solcott.activitytracker.app

import io.github.solcott.uistate.ContentState

/** [this] in the phase it's in (loading, failed, loaded, and from where), holding what [transform] makes of its data. */
internal inline fun <T, R> ContentState<T>.mapContent(transform: (T) -> R): ContentState<R> = ContentState(transform(data), origin, status)
