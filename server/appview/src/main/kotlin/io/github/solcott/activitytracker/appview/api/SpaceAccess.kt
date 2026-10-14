package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.atproto.SimpleSpace.CheckUserAccess
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory

/**
 * Answers `checkUserAccess`, the managing-app callout: an athlete's PDS asks whether a user may read or write one of the athlete's spaces.
 * Each `app.activitytracker.space.athlete` space is one visibility tier, named by its key. Every decision is an answer, never an error, and
 * anything this AppView doesn't recognise is denied. The PDS admits the authority itself without asking. The AppView's own DID ([self]) may
 * read every tier, so it can sync the activities in them; it serves each one only to the viewers its tier admits.
 */
class SpaceAccess(private val db: Database, private val self: Did) {
    private val log = LoggerFactory.getLogger(SpaceAccess::class.java)

    /** [issuer] is the verified caller, which must be the space's authority. */
    suspend fun check(issuer: Did, params: CheckUserAccess.Params): CheckUserAccess.Output {
        val space = params.space
        val authorized =
            when {
                space.authority != issuer -> {
                    log.warn("checkUserAccess for {} from {}, not its authority", space, issuer)
                    false
                }
                space.type != Nsid.SPACE_ATHLETE -> false
                // Only the athlete writes to their spaces.
                params.access != CheckUserAccess.READ -> false
                params.user == self -> space.skey in TIERS
                else -> canRead(space.authority, space.skey, params.user)
            }
        return CheckUserAccess.Output(authorized)
    }

    private suspend fun canRead(authority: Did, tier: String, user: Did): Boolean =
        when (tier) {
            AthleteSpace.PUBLIC -> true
            AthleteSpace.FOLLOWERS -> db.tx { follows(user.value, authority.value) }
            AthleteSpace.MUTUALS -> db.tx { isMutual(authority.value, user.value) }
            else -> false
        }

    private companion object {
        val TIERS = setOf(AthleteSpace.PUBLIC, AthleteSpace.FOLLOWERS, AthleteSpace.MUTUALS, AthleteSpace.SELF)
    }
}
