package io.github.solcott.activitytracker.lexicon.app

/**
 * Keys of an athlete's `app.activitytracker.space.athlete` spaces: one space per visibility tier, so a tier and a [Visibility] value always
 * match. The AppView, as managing app, decides who reads each one.
 */
object AthleteSpace {
    /** Any signed-in atproto account. */
    const val PUBLIC = Visibility.PUBLIC

    /** Accounts that follow the athlete and that the athlete follows, in this app's follow graph. */
    const val MUTUALS = Visibility.MUTUALS

    /** Only the athlete. */
    const val SELF = Visibility.SELF
}
