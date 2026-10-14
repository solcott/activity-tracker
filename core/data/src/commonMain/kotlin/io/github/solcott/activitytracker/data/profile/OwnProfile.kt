package io.github.solcott.activitytracker.data.profile

import io.github.solcott.activitytracker.lexicon.app.FollowPolicy
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.atproto.BlobRef

/**
 * The signed-in athlete's profile as they edit it. [details] holds every detail they have set, public or not, and [visibility] has an
 * effective value for every group: public when the detail is in the record, the tier when it's in a tier space, and Only me for a detail
 * that isn't set.
 */
data class OwnProfile(
    val displayName: String?,
    val description: String?,
    val avatar: BlobRef?,
    /** Where [avatar] can be loaded from: the blob on the athlete's own PDS. */
    val avatarUrl: String?,
    val details: ProfileDetails,
    val visibility: DetailsVisibility,
    /**
     * The session was signed in before the `space:` scope, so [details] holds only the public ones and saving fails until the athlete signs
     * in again.
     */
    val needsSignIn: Boolean = false,
    /** A `FollowPolicy` value: whether the athlete approves followers. */
    val followPolicy: String = FollowPolicy.OPEN,
)

/**
 * A profile save. Blank text clears a field; a group in [groups] that isn't set in [details] is cleared, and groups outside [groups] keep
 * their current values.
 */
data class ProfileEdit(
    val displayName: String,
    val description: String,
    val details: ProfileDetails,
    val visibility: DetailsVisibility,
    val avatar: AvatarEdit = AvatarEdit.Keep,
    val groups: Set<DetailGroup> = DetailGroup.profile,
    /** A `FollowPolicy` value, or null to keep the current one. Turning approval on first approves everyone who follows the athlete now. */
    val followPolicy: String? = null,
)

sealed interface AvatarEdit {
    data object Keep : AvatarEdit

    data object Remove : AvatarEdit

    /** A new picture, already downscaled to fit the lexicon's 1 MB limit. */
    class Replace(val bytes: ByteArray, val mimeType: String) : AvatarEdit
}
