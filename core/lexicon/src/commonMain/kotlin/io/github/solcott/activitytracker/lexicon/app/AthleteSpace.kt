package io.github.solcott.activitytracker.lexicon.app

/**
 * Keys of an athlete's `app.activitytracker.space.athlete` spaces: one space per visibility tier, so a tier and a [Visibility] value always
 * match, plus [INTERACTIONS]. The AppView, as managing app, decides who reads each one.
 */
object AthleteSpace {
    /** Any signed-in atproto account. */
    const val PUBLIC = Visibility.PUBLIC

    /** Accounts whose follow of the athlete is effective in this app's follow graph (approved, when the athlete approves followers). */
    const val FOLLOWERS = Visibility.FOLLOWERS

    /** Accounts that follow the athlete and that the athlete follows, in this app's follow graph. */
    const val MUTUALS = Visibility.MUTUALS

    /** Only the athlete. */
    const val SELF = Visibility.SELF

    /**
     * Not a tier: the athlete's likes on non-public activities, read only by the athlete and the AppView, which shows each like to the
     * liked activity's audience. Keep it out of every list of tiers, so profile details and activity streams never go here.
     */
    const val INTERACTIONS = "interactions"
}
