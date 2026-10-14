package io.github.solcott.activitytracker.data.profile

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.Visibility

/**
 * A [Visibility] value per [DetailGroup], as the athlete edits it. Not wire data: a detail's visibility is where it's stored, the public
 * profile record or the tier space of that name.
 */
@Immutable
data class DetailsVisibility(
    /** The whole location: locality, region and country. */
    val location: String? = null,
    val birthDate: String? = null,
    val gender: String? = null,
    /** [ProfileDetails.weight] and [ProfileDetails.height] together. */
    val weight: String? = null,
    val training: String? = null,
)
