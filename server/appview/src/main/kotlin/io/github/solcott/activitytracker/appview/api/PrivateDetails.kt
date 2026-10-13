package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.ProfileDetailRules
import io.github.solcott.activitytracker.appview.db.ActorPrivateDetails
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.Follows
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.DetailsVisibility
import io.github.solcott.activitytracker.lexicon.app.GetPrivateDetails
import io.github.solcott.activitytracker.lexicon.app.Location
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.PutPrivateDetails
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.ktor.http.HttpStatusCode
import kotlin.time.Clock
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

/**
 * Mutuals-only and private profile details. Repo records are public, so these are the one kind of data the AppView stores rather than
 * indexes: athletes write them with `putPrivateDetails`, and [visibleDetails] decides who sees each one.
 */
class PrivateDetails(private val db: Database, private val clock: Clock = Clock.System) {
    suspend fun put(author: Did, input: PutPrivateDetails.Input) {
        val details = input.details
        val visibility = input.visibility
        requirePrivate(details.location, visibility.location, "location")
        requirePrivate(details.birthDate, visibility.birthDate, "birthDate")
        requirePrivate(details.gender, visibility.gender, "gender")
        requirePrivate(details.weight ?: details.height, visibility.weight, "weight and height")
        requirePrivate(details.training, visibility.training, "training")
        if (!ProfileDetailRules.isValid(details)) {
            throw XrpcError(HttpStatusCode.BadRequest.value, "InvalidRequest", "Details break the lexicon's constraints")
        }
        val location = details.location?.takeUnless { it == Location() }
        val training = details.training?.takeUnless { it == TrainingDetails() }
        db.tx {
            if (details.copy(location = location, training = training) == ProfileDetails()) {
                ActorPrivateDetails.deleteWhere { did eq author.value }
                return@tx
            }
            ActorPrivateDetails.upsert {
                it[did] = author.value
                it[locality] = location?.locality
                it[region] = location?.region
                it[country] = location?.country
                it[birthDate] = details.birthDate
                it[gender] = details.gender
                it[weightGrams] = details.weight
                it[heightMm] = details.height
                it[ActorPrivateDetails.training] = ProfileDetailRules.encodeTraining(training)
                it[locationVisibility] = visibility.location.takeIf { location != null }
                it[birthDateVisibility] = visibility.birthDate.takeIf { details.birthDate != null }
                it[genderVisibility] = visibility.gender.takeIf { details.gender != null }
                it[weightVisibility] = visibility.weight.takeIf { details.weight != null || details.height != null }
                it[trainingVisibility] = visibility.training.takeIf { training != null }
                it[updatedAt] = clock.now().toEpochMilliseconds()
            }
        }
    }

    suspend fun get(author: Did): GetPrivateDetails.Output = db.tx {
        val row = ActorPrivateDetails.selectAll().where { ActorPrivateDetails.did eq author.value }.singleOrNull()
        GetPrivateDetails.Output(
            details = row?.let(::privateDetails) ?: ProfileDetails(),
            visibility =
                DetailsVisibility(
                    location = row?.get(ActorPrivateDetails.locationVisibility),
                    birthDate = row?.get(ActorPrivateDetails.birthDateVisibility),
                    gender = row?.get(ActorPrivateDetails.genderVisibility),
                    weight = row?.get(ActorPrivateDetails.weightVisibility),
                    training = row?.get(ActorPrivateDetails.trainingVisibility),
                ),
        )
    }

    private fun requirePrivate(value: Any?, visibility: String?, field: String) {
        if (value != null && visibility != Visibility.MUTUALS && visibility != Visibility.SELF) {
            throw XrpcError(
                HttpStatusCode.BadRequest.value,
                PutPrivateDetails.INVALID_VISIBILITY,
                "$field needs a ${Visibility.MUTUALS} or ${Visibility.SELF} visibility; public details belong in the profile record",
            )
        }
    }
}

/**
 * The details of [actor] (an `actors` row) that [viewer] may see: the public ones from the profile record, plus private ones for the
 * athlete themselves and mutuals-only ones for a mutual. A detail in both places is public already, so the public copy wins.
 */
internal fun JdbcTransaction.visibleDetails(actor: ResultRow, viewer: Did?): ProfileDetails? {
    val did = actor[Actors.did]
    val public =
        ProfileDetails(
            location = location(actor[Actors.locality], actor[Actors.region], actor[Actors.country]),
            birthDate = actor[Actors.birthDate],
            gender = actor[Actors.gender],
            weight = actor[Actors.weightGrams],
            height = actor[Actors.heightMm],
            training = ProfileDetailRules.decodeTraining(actor[Actors.training]),
        )
    val private = ActorPrivateDetails.selectAll().where { ActorPrivateDetails.did eq did }.singleOrNull()
    val merged =
        if (private == null || viewer == null) {
            public
        } else {
            val isSelf = viewer.value == did
            val mutual by lazy { isMutual(viewer.value, did) }
            fun visible(visibility: String?) = isSelf || (visibility == Visibility.MUTUALS && mutual)
            val hidden = privateDetails(private)
            ProfileDetails(
                location = public.location ?: hidden.location?.takeIf { visible(private[ActorPrivateDetails.locationVisibility]) },
                birthDate = public.birthDate ?: hidden.birthDate?.takeIf { visible(private[ActorPrivateDetails.birthDateVisibility]) },
                gender = public.gender ?: hidden.gender?.takeIf { visible(private[ActorPrivateDetails.genderVisibility]) },
                weight = public.weight ?: hidden.weight?.takeIf { visible(private[ActorPrivateDetails.weightVisibility]) },
                height = public.height ?: hidden.height?.takeIf { visible(private[ActorPrivateDetails.weightVisibility]) },
                training = public.training ?: hidden.training?.takeIf { visible(private[ActorPrivateDetails.trainingVisibility]) },
            )
        }
    return merged.takeUnless { it == ProfileDetails() }
}

private fun privateDetails(row: ResultRow) =
    ProfileDetails(
        location = location(row[ActorPrivateDetails.locality], row[ActorPrivateDetails.region], row[ActorPrivateDetails.country]),
        birthDate = row[ActorPrivateDetails.birthDate],
        gender = row[ActorPrivateDetails.gender],
        weight = row[ActorPrivateDetails.weightGrams],
        height = row[ActorPrivateDetails.heightMm],
        training = ProfileDetailRules.decodeTraining(row[ActorPrivateDetails.training]),
    )

private fun location(locality: String?, region: String?, country: String?): Location? =
    Location(locality, region, country).takeUnless { it == Location() }

/** Whether [a] and [b] follow each other in this app's follow graph: who sees mutuals-only details and reads a `mutuals` space. */
internal fun JdbcTransaction.isMutual(a: String, b: String): Boolean = follows(a, b) && follows(b, a)

internal fun JdbcTransaction.follows(author: String, subject: String): Boolean =
    !Follows.select(Follows.uri).where { (Follows.authorDid eq author) and (Follows.subjectDid eq subject) }.limit(1).empty()
