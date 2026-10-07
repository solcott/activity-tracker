package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.app.Location
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.SportTraining
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.activitytracker.lexicon.app.TrainingSetAt
import io.github.solcott.activitytracker.lexicon.atproto.BlobRef
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * The `actor.defs#details` and `actor.profile#avatar` constraints, shared by the indexer (which drops what breaks them, because records are
 * untrusted) and `putPrivateDetails` (which rejects the request instead).
 */
internal object ProfileDetailRules {
    /** The lexicon `maxLength` of free-text details. */
    const val MAX_TEXT_LENGTH = 640
    const val MAX_WEIGHT_GRAMS = 500_000L
    const val MAX_AVATAR_BYTES = 1_000_000L
    val AVATAR_TYPES = setOf("image/png", "image/jpeg")

    private val country = Regex("[A-Z]{2}")
    private val cid = Regex("[a-z0-9]{1,255}")

    /** [details] without anything that breaks a rule; free text is truncated. Null when nothing is left. */
    fun sanitize(details: ProfileDetails?): ProfileDetails? {
        details ?: return null
        val location =
            details.location
                ?.let { l ->
                    Location(
                        locality = l.locality?.take(MAX_TEXT_LENGTH),
                        region = l.region?.take(MAX_TEXT_LENGTH),
                        country = l.country?.takeIf(country::matches),
                    )
                }
                ?.takeUnless { it == Location() }
        return ProfileDetails(
                location = location,
                birthDate = details.birthDate?.takeIf(::isDate),
                gender = details.gender?.take(MAX_TEXT_LENGTH),
                weight = details.weight?.takeIf { it in 1..MAX_WEIGHT_GRAMS },
                training = sanitize(details.training),
            )
            .takeUnless { it == ProfileDetails() }
    }

    private fun sanitize(training: TrainingDetails?): TrainingDetails? {
        training ?: return null
        val heartRate = TrainingDetails.MIN_HEART_RATE..TrainingDetails.MAX_HEART_RATE
        return TrainingDetails(
                functionalThresholdPower = training.functionalThresholdPower?.takeIf { it in 1..TrainingDetails.MAX_FTP },
                maxHeartRate = training.maxHeartRate?.takeIf { it in heartRate },
                thresholdHeartRate = training.thresholdHeartRate?.takeIf { it in heartRate },
                restingHeartRate = training.restingHeartRate?.takeIf { it in heartRate },
                heartRateZoneModel = training.heartRateZoneModel?.takeIf { it.length <= TrainingDetails.MAX_MODEL_LENGTH },
                powerZoneModel = training.powerZoneModel?.takeIf { it.length <= TrainingDetails.MAX_MODEL_LENGTH },
                autoUpdate =
                    training.autoUpdate
                        .filter { it.length <= TrainingDetails.MAX_MODEL_LENGTH }
                        .distinct()
                        .take(TrainingDetails.MAX_AUTO_UPDATE),
                setAt = training.setAt?.takeUnless { it == TrainingSetAt() },
                sports =
                    training.sports
                        .filter { it.sport.isNotEmpty() && it.sport.length <= TrainingDetails.MAX_MODEL_LENGTH }
                        .distinctBy { it.sport }
                        .take(TrainingDetails.MAX_SPORTS)
                        .map(::sanitize),
            )
            .takeUnless { it == TrainingDetails() }
    }

    private fun sanitize(sport: SportTraining): SportTraining {
        val heartRate = TrainingDetails.MIN_HEART_RATE..TrainingDetails.MAX_HEART_RATE
        fun String?.model() = this?.takeIf { it.length <= TrainingDetails.MAX_MODEL_LENGTH }
        return SportTraining(
            sport = sport.sport,
            functionalThresholdPower = sport.functionalThresholdPower?.takeIf { it in 1..TrainingDetails.MAX_FTP },
            maxHeartRate = sport.maxHeartRate?.takeIf { it in heartRate },
            thresholdHeartRate = sport.thresholdHeartRate?.takeIf { it in heartRate },
            thresholdSpeed =
                sport.thresholdSpeed?.takeIf { it in TrainingDetails.MIN_THRESHOLD_SPEED..TrainingDetails.MAX_THRESHOLD_SPEED },
            heartRateZoneModel = sport.heartRateZoneModel.model(),
            powerZoneModel = sport.powerZoneModel.model(),
            speedZoneModel = sport.speedZoneModel.model(),
            setAt = sport.setAt?.takeUnless { it == TrainingSetAt() },
        )
    }

    /** True when [sanitize] would change nothing, apart from dropping an empty location or training. */
    fun isValid(details: ProfileDetails): Boolean =
        sanitize(details) ==
            details
                .copy(
                    location = details.location?.takeUnless { it == Location() },
                    training = details.training?.takeUnless { it == TrainingDetails() },
                )
                .takeUnless { it == ProfileDetails() }

    /** [training] as the JSON stored in a `training` column, or null to store nothing. */
    fun encodeTraining(training: TrainingDetails?): String? = training?.let { AtJson.encodeToString(TrainingDetails.serializer(), it) }

    /** A `training` column's JSON, written by [encodeTraining]. */
    fun decodeTraining(json: String?): TrainingDetails? = json?.let { AtJson.decodeFromString(TrainingDetails.serializer(), it) }

    /** The avatar's CID when the blob is an accepted image type and size, and its CID is safe to put in a URL path. */
    fun avatarCid(avatar: BlobRef?): String? =
        avatar?.takeIf { it.mimeType in AVATAR_TYPES && it.size in 1..MAX_AVATAR_BYTES }?.ref?.link?.takeIf(::isCid)

    fun isCid(value: String): Boolean = cid.matches(value)

    private fun isDate(value: String): Boolean =
        value.length == DATE_LENGTH &&
            try {
                LocalDate.parse(value)
                true
            } catch (_: DateTimeParseException) {
                false
            }

    private const val DATE_LENGTH = 10
}
