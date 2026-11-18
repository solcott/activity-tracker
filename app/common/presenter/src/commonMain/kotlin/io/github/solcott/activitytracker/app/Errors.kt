package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.activity.PendingWritesException
import io.github.solcott.activitytracker.data.di.SpacesUnsupportedException
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors

/**
 * [cause] as a [UiError] of [kind], except that a session without access to the athlete's spaces is told to sign in again, one whose PDS
 * doesn't host spaces is told so, and a save waiting on unsent writes says so.
 */
fun uiErrorOf(kind: UiError.Kind, cause: Throwable): UiError =
    when {
        cause is XrpcException && cause.error == SpaceErrors.SCOPE_MISSING -> UiError(UiError.Kind.SignInAgain)
        cause is SpacesUnsupportedException -> UiError(UiError.Kind.SpacesUnsupported)
        cause is PendingWritesException -> UiError(UiError.Kind.WritesPending)
        else -> UiError.of(kind, cause)
    }
