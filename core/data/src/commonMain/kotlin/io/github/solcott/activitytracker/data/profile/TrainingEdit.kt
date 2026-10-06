package io.github.solcott.activitytracker.data.profile

import io.github.solcott.activitytracker.lexicon.app.DetailsVisibility
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails

/**
 * Saves the athlete's training thresholds and zone models ([DetailGroup.Training]) with [visibility], leaving their name, picture and other
 * details as they are. Null [training] clears it.
 */
suspend fun ProfileRepository.saveTraining(training: TrainingDetails?, visibility: String) {
    val record = ownProfileRecord()
    save(
        ProfileEdit(
            displayName = record?.displayName.orEmpty(),
            description = record?.description.orEmpty(),
            details = ProfileDetails(training = training),
            visibility = DetailsVisibility(training = visibility),
            groups = setOf(DetailGroup.Training),
        )
    )
}
