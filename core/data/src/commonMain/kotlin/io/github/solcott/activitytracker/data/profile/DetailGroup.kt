package io.github.solcott.activitytracker.data.profile

import io.github.solcott.activitytracker.lexicon.app.DetailsVisibility
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails

/**
 * The profile details that each have their own visibility: one per field of [DetailsVisibility]. A group can hold several fields of
 * [ProfileDetails], which share its visibility.
 */
enum class DetailGroup {
    /** Locality, region and country. */
    Location,
    BirthDate,
    Gender,
    /** Weight and height, under [DetailsVisibility.weight]. */
    Body,
    Training;

    companion object {
        /** The groups the edit-profile screen edits. Training thresholds have a screen of their own. */
        val profile: Set<DetailGroup> = setOf(Location, BirthDate, Gender, Body)
    }

    fun isSet(details: ProfileDetails): Boolean =
        when (this) {
            Location -> details.location != null
            BirthDate -> details.birthDate != null
            Gender -> details.gender != null
            Body -> details.weight != null || details.height != null
            Training -> details.training != null
        }

    /** [into] with this group's value taken from [from]. */
    fun copy(from: ProfileDetails, into: ProfileDetails): ProfileDetails =
        when (this) {
            Location -> into.copy(location = from.location)
            BirthDate -> into.copy(birthDate = from.birthDate)
            Gender -> into.copy(gender = from.gender)
            Body -> into.copy(weight = from.weight, height = from.height)
            Training -> into.copy(training = from.training)
        }

    fun visibility(visibility: DetailsVisibility): String? =
        when (this) {
            Location -> visibility.location
            BirthDate -> visibility.birthDate
            Gender -> visibility.gender
            Body -> visibility.weight
            Training -> visibility.training
        }

    fun withVisibility(visibility: DetailsVisibility, value: String?): DetailsVisibility =
        when (this) {
            Location -> visibility.copy(location = value)
            BirthDate -> visibility.copy(birthDate = value)
            Gender -> visibility.copy(gender = value)
            Body -> visibility.copy(weight = value)
            Training -> visibility.copy(training = value)
        }
}

/** Only the [groups] of these details. */
fun ProfileDetails.only(groups: Collection<DetailGroup>): ProfileDetails =
    groups.fold(ProfileDetails()) { acc, group -> group.copy(this, acc) }

/** Only the [groups] of this visibility. */
fun DetailsVisibility.only(groups: Collection<DetailGroup>): DetailsVisibility =
    groups.fold(DetailsVisibility()) { acc, group -> group.withVisibility(acc, group.visibility(this)) }
