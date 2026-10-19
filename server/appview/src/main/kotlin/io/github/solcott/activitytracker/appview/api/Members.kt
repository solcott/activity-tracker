package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityMembers
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.MemberView
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select

// Combined activities (feed.activity#combined): which of their claims on members count. The indexer stores every claim on one of the
// author's own activities (`activity_members`); whether it counts is decided here, when reading, since members and the combined activity
// may be indexed in any order and change visibility independently.

/**
 * Whether the claim ([claimMember], [claimCombined]) on the member row ([memberUri], [memberAuthor], [memberVisibility]) counts:
 * - the combined activity is indexed, by the member's author and at its visibility;
 * - the member isn't itself a combined activity (it claims nothing);
 * - no earlier-created combined activity (a smaller URI, so an earlier TID) of the same author and visibility claims it too.
 */
@Suppress("LongParameterList") // The claim's two ends and the member row's identity.
internal fun validClaim(
    claimMember: Expression<String>,
    claimCombined: Expression<String>,
    memberUri: Expression<String>,
    memberAuthor: Expression<String>,
    memberVisibility: Expression<String>,
): Op<Boolean> {
    val combined = Activities.alias("claiming")
    val nested = ActivityMembers.alias("nested")
    val earlier = ActivityMembers.alias("earlier")
    val earlierCombined = Activities.alias("earlier_claiming")
    return (claimMember eq memberUri) and
        exists(
            combined.select(combined[Activities.uri]).where {
                (combined[Activities.uri] eq claimCombined) and
                    (combined[Activities.authorDid] eq memberAuthor) and
                    (combined[Activities.visibility] eq memberVisibility)
            }
        ) and
        not(exists(nested.select(nested[ActivityMembers.memberUri]).where { nested[ActivityMembers.combinedUri] eq memberUri })) and
        not(
            exists(
                earlier
                    .join(earlierCombined, JoinType.INNER, earlier[ActivityMembers.combinedUri], earlierCombined[Activities.uri])
                    .select(earlier[ActivityMembers.memberUri])
                    .where {
                        (earlier[ActivityMembers.memberUri] eq memberUri) and
                            (earlier[ActivityMembers.combinedUri] less claimCombined) and
                            (earlierCombined[Activities.authorDid] eq memberAuthor) and
                            (earlierCombined[Activities.visibility] eq memberVisibility)
                    }
            )
        )
}

/** The current [Activities] row is a member of a combined activity: feeds show that one instead. */
internal fun isMember(): Op<Boolean> {
    val claim = ActivityMembers.alias("claim")
    return exists(
        claim.select(claim[ActivityMembers.memberUri]).where {
            validClaim(
                claim[ActivityMembers.memberUri],
                claim[ActivityMembers.combinedUri],
                Activities.uri,
                Activities.authorDid,
                Activities.visibility,
            )
        }
    )
}

/** Each of [combined]'s members whose claim counts, oldest first, keyed by the combined activity's URI. */
internal fun JdbcTransaction.membersOf(combined: Collection<String>): Map<String, List<MemberView>> =
    if (combined.isEmpty()) {
        emptyMap()
    } else {
        validMemberRows { ActivityMembers.combinedUri inList combined }
            .orderBy(Activities.startedAt to SortOrder.ASC, Activities.uri to SortOrder.ASC)
            .toList()
            .groupBy({ it[ActivityMembers.combinedUri] }) {
                val record = AtJson.decodeFromString(ActivityRecord.serializer(), it[Activities.record])
                MemberView(
                    uri = AtUri(it[Activities.uri]),
                    startedAt = Instant.fromEpochMilliseconds(it[Activities.startedAt]),
                    elapsedTime = record.elapsedTime.coerceAtLeast(0),
                    sport = record.sport.take(MAX_SPORT_LENGTH),
                    device = record.device?.take(MAX_MEMBER_DEVICE_LENGTH),
                )
            }
    }

/** For each of [members] that is one, the combined activity it's part of. */
internal fun JdbcTransaction.combinedIntoOf(members: Collection<String>): Map<String, String> =
    if (members.isEmpty()) {
        emptyMap()
    } else {
        validMemberRows { ActivityMembers.memberUri inList members }
            .toList()
            .associate { it[ActivityMembers.memberUri] to it[ActivityMembers.combinedUri] }
    }

/** The URIs whose likes and comments count as [uri]'s: itself and, for a combined activity, its members. */
internal fun JdbcTransaction.interactionSubjects(uri: String): List<String> =
    listOf(uri) + membersOf(listOf(uri))[uri].orEmpty().map { it.uri.value }

/** Member rows (an [Activities] row joined to its claim) whose claim counts, among the claims [where] picks. */
private fun validMemberRows(where: () -> Op<Boolean>) =
    Activities.join(ActivityMembers, JoinType.INNER, Activities.uri, ActivityMembers.memberUri)
        .select(
            Activities.uri,
            Activities.startedAt,
            Activities.record,
            ActivityMembers.memberUri,
            ActivityMembers.combinedUri,
        )
        .where {
            where() and
                validClaim(
                    ActivityMembers.memberUri,
                    ActivityMembers.combinedUri,
                    Activities.uri,
                    Activities.authorDid,
                    Activities.visibility,
                )
        }

/** A member's sport and device as `#memberView` allows them: the record's own are untrusted. */
private const val MAX_SPORT_LENGTH = 64
private const val MAX_MEMBER_DEVICE_LENGTH = 256
