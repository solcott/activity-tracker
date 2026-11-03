package io.github.solcott.activitytracker.app.gear

import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.gear.GearNeedsSignInException
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors

/**
 * [uiErrorOf] for a gear write: a session whose grant predates gear asks to sign in again for gear, whether this client or the PDS refused
 * the save.
 */
internal fun gearErrorOf(kind: UiError.Kind, e: Throwable): UiError =
    when {
        e is GearNeedsSignInException -> UiError(UiError.Kind.GearSignInAgain)
        e is XrpcException && e.error == SpaceErrors.SCOPE_MISSING -> UiError(UiError.Kind.GearSignInAgain)
        else -> uiErrorOf(kind, e)
    }
