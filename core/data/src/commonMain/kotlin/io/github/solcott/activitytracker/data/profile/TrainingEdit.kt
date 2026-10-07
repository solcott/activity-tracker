package io.github.solcott.activitytracker.data.profile

import io.github.solcott.activitytracker.activityfile.DeviceThresholds
import io.github.solcott.activitytracker.data.training.ThresholdChange
import io.github.solcott.activitytracker.data.training.ThresholdUpdates
import io.github.solcott.activitytracker.lexicon.app.DetailsVisibility
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.time.Instant

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

/**
 * Updates thresholds from an activity that started at [activityStart] and was recorded with [device]'s settings, keeping the training
 * visibility. The changes are worked out again from the saved profile, which another device may have changed since a preview. Returns the
 * changes made; when there are none it writes nothing.
 */
suspend fun ProfileRepository.applyDeviceThresholds(device: DeviceThresholds?, activityStart: Instant): List<ThresholdChange> {
    val own = ownProfile()
    val current = own.details.training ?: return emptyList()
    val changes = ThresholdUpdates.pending(current, device, activityStart)
    if (changes.isNotEmpty()) {
        saveTraining(
            ThresholdUpdates.apply(current, changes, activityStart),
            DetailGroup.Training.visibility(own.visibility) ?: Visibility.SELF,
        )
    }
    return changes
}
