package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.ProfileDetailRules
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.FollowApprovals
import io.github.solcott.activitytracker.appview.db.Follows
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.FollowPolicy
import io.github.solcott.activitytracker.lexicon.app.Location
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.ProfileViewerState
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select

/**
 * The details of [actor] (an `actors` row) from their profile record: the public ones, all the AppView has. Details an athlete marks
 * Mutuals or Only me live in their own tier spaces, which clients read directly; the AppView stores none.
 */
internal fun publicDetails(actor: ResultRow): ProfileDetails? =
    ProfileDetails(
            location = Location(actor[Actors.locality], actor[Actors.region], actor[Actors.country]).takeUnless { it == Location() },
            birthDate = actor[Actors.birthDate],
            gender = actor[Actors.gender],
            weight = actor[Actors.weightGrams],
            height = actor[Actors.heightMm],
            training = ProfileDetailRules.decodeTraining(actor[Actors.training]),
        )
        .takeUnless { it == ProfileDetails() }

/** Whether [a] and [b] effectively follow each other in this app's follow graph: who reads a `mutuals` space and sees Mutuals streams. */
internal fun JdbcTransaction.isMutual(a: String, b: String): Boolean = follows(a, b) && follows(b, a)

/** Whether [author]'s follow of [subject] is effective ([effectiveFollow]): who reads a `followers` space and sees Followers streams. */
internal fun JdbcTransaction.follows(author: String, subject: String): Boolean =
    !Follows.select(Follows.uri)
        .where { (Follows.authorDid eq author) and (Follows.subjectDid eq subject) and effectiveFollow() }
        .limit(1)
        .empty()

/**
 * True for a `follows` row that counts: its subject's policy is open (no `approve` on their actor row, or no row at all), or the subject
 * approved its author. Every query that reads `follows` for anything but the viewer's own follow record ANDs this in, so a pending follow
 * never reaches a timeline, a count, a list or a space. It's a correlated subquery on the outer `follows` row, so it holds whichever of the
 * follow and the approval was indexed first, and a policy change needs no rewrite.
 */
internal fun effectiveFollow(): Op<Boolean> =
    notExists(Actors.select(Actors.did).where { (Actors.did eq Follows.subjectDid) and (Actors.followPolicy eq FollowPolicy.APPROVE) }) or
        exists(
            FollowApprovals.select(FollowApprovals.uri).where {
                (FollowApprovals.authorDid eq Follows.subjectDid) and (FollowApprovals.subjectDid eq Follows.authorDid)
            }
        )

/**
 * [viewer]'s follow of [did], effective or not, and whether it still waits for [did]'s approval; and [did]'s follow of [viewer], only when
 * effective, so a client can skip a `mutuals` read the PDS would refuse.
 */
internal fun JdbcTransaction.viewerState(viewer: String, did: String): ProfileViewerState {
    val following =
        Follows.select(Follows.uri)
            .where { (Follows.authorDid eq viewer) and (Follows.subjectDid eq did) }
            .firstOrNull()
            ?.let { AtUri(it[Follows.uri]) }
    val followedBy =
        Follows.select(Follows.uri)
            .where { (Follows.authorDid eq did) and (Follows.subjectDid eq viewer) and effectiveFollow() }
            .firstOrNull()
            ?.let { AtUri(it[Follows.uri]) }
    return ProfileViewerState(
        following = following,
        followRequested = (following != null && !follows(viewer, did)).takeIf { it },
        followedBy = followedBy,
    )
}
