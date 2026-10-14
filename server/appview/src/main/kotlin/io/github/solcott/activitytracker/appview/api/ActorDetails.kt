package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.ProfileDetailRules
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.Follows
import io.github.solcott.activitytracker.lexicon.app.Location
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
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

/** Whether [a] and [b] follow each other in this app's follow graph: who reads a `mutuals` space and sees Mutuals streams. */
internal fun JdbcTransaction.isMutual(a: String, b: String): Boolean = follows(a, b) && follows(b, a)

internal fun JdbcTransaction.follows(author: String, subject: String): Boolean =
    !Follows.select(Follows.uri).where { (Follows.authorDid eq author) and (Follows.subjectDid eq subject) }.limit(1).empty()
