package io.github.solcott.activitytracker.data.di

/**
 * Scope of a signed-in account. The `SessionGraph` extension (in `composeApp`) is created with the account's `OAuthSession` after sign-in
 * and discarded on sign-out, so nothing authenticated outlives the session.
 */
abstract class SessionScope private constructor()
