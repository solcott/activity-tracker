package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.lexicon.app.Location
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
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
            )
            .takeUnless { it == ProfileDetails() }
    }

    /** True when [sanitize] would change nothing, apart from dropping an empty location. */
    fun isValid(details: ProfileDetails): Boolean =
        sanitize(details) ==
            details.copy(location = details.location?.takeUnless { it == Location() }).takeUnless { it == ProfileDetails() }

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
