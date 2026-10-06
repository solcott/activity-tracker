package io.github.solcott.activitytracker.data.profile

import io.github.solcott.activitytracker.lexicon.app.DetailsVisibility
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails

/** The profile details that each have their own visibility: one per field of [ProfileDetails] and [DetailsVisibility]. */
enum class DetailGroup {
    Location,
    BirthDate,
    Gender,
    Weight,
    Training;

    companion object {
        /** The groups the edit-profile screen edits. Training thresholds have a screen of their own. */
        val profile: Set<DetailGroup> = setOf(Location, BirthDate, Gender, Weight)
    }

    fun isSet(details: ProfileDetails): Boolean =
        when (this) {
            Location -> details.location != null
            BirthDate -> details.birthDate != null
            Gender -> details.gender != null
            Weight -> details.weight != null
            Training -> details.training != null
        }

    /** [into] with this group's value taken from [from]. */
    fun copy(from: ProfileDetails, into: ProfileDetails): ProfileDetails =
        when (this) {
            Location -> into.copy(location = from.location)
            BirthDate -> into.copy(birthDate = from.birthDate)
            Gender -> into.copy(gender = from.gender)
            Weight -> into.copy(weight = from.weight)
            Training -> into.copy(training = from.training)
        }

    fun visibility(visibility: DetailsVisibility): String? =
        when (this) {
            Location -> visibility.location
            BirthDate -> visibility.birthDate
            Gender -> visibility.gender
            Weight -> visibility.weight
            Training -> visibility.training
        }

    fun withVisibility(visibility: DetailsVisibility, value: String?): DetailsVisibility =
        when (this) {
            Location -> visibility.copy(location = value)
            BirthDate -> visibility.copy(birthDate = value)
            Gender -> visibility.copy(gender = value)
            Weight -> visibility.copy(weight = value)
            Training -> visibility.copy(training = value)
        }
}

/** Only the [groups] of these details. */
fun ProfileDetails.only(groups: Collection<DetailGroup>): ProfileDetails =
    groups.fold(ProfileDetails()) { acc, group -> group.copy(this, acc) }

/** Only the [groups] of this visibility. */
fun DetailsVisibility.only(groups: Collection<DetailGroup>): DetailsVisibility =
    groups.fold(DetailsVisibility()) { acc, group -> group.withVisibility(acc, group.visibility(this)) }
