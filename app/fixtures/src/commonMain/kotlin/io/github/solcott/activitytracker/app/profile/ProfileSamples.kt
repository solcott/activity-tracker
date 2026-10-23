package io.github.solcott.activitytracker.app.profile

import androidx.compose.foundation.text.input.TextFieldState
import io.github.solcott.activitytracker.app.feed.FeedSamples
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.fakeProfile
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.app.fixtures.pagedSamples
import io.github.solcott.activitytracker.data.profile.DetailsVisibility
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

object ProfileSamples {
    val profile: ProfileView = fakeProfile()

    private fun state(content: ContentState<ProfileView?>) =
        ProfileState(content = content, activities = ContentStates.loaded(FeedSamples.activities), viewerDid = Fixed.BOB.value)

    val loading: ProfileState
        get() = ProfileState(content = ContentStates.loading(null), viewerDid = Fixed.BOB.value)

    val failed: ProfileState
        get() = ProfileState(content = ContentStates.failed(null), viewerDid = Fixed.BOB.value)

    val loaded: ProfileState
        get() = state(ContentStates.loaded(profile))

    /** The header loaded and the athlete has no activities. */
    val empty: ProfileState
        get() = state(ContentStates.loaded(profile)).copy(activities = ContentStates.loaded(persistentListOf()))

    val refreshing: ProfileState
        get() = state(ContentStates.refreshing(profile))

    val refreshFailed: ProfileState
        get() = state(ContentStates.refreshFailed(profile))

    /** The viewer's own profile. */
    val self: ProfileState
        get() = loaded.copy(isSelf = true, viewerDid = Fixed.ALICE.value)

    val all: Map<String, ProfileState>
        get() =
            mapOf(
                SampleKind.LOADING to loading,
                SampleKind.FAILED to failed,
                SampleKind.EMPTY to empty,
                SampleKind.LOADED to loaded,
                SampleKind.REFRESHING to refreshing,
                SampleKind.REFRESH_FAILED to refreshFailed,
                "self" to self,
            )
}

object FollowRequestsSamples {
    private val content = FollowRequestsContent(requests = persistentListOf(fakeProfileBasic(Fixed.BOB), fakeProfileBasic(Fixed.CAROL)))

    val all: Map<String, FollowRequestsState>
        get() =
            mapOf(
                SampleKind.LOADING to FollowRequestsState(content = ContentStates.loading(FollowRequestsContent())),
                SampleKind.FAILED to FollowRequestsState(content = ContentStates.failed(FollowRequestsContent())),
                SampleKind.EMPTY to FollowRequestsState(content = ContentStates.loaded(FollowRequestsContent())),
                SampleKind.LOADED to FollowRequestsState(content = ContentStates.loaded(content)),
                SampleKind.REFRESHING to FollowRequestsState(content = ContentStates.refreshing(content)),
                SampleKind.REFRESH_FAILED to FollowRequestsState(content = ContentStates.refreshFailed(content)),
            )
}

object FollowListSamples {
    val accounts: ImmutableList<ProfileViewBasic> = persistentListOf(fakeProfileBasic(Fixed.BOB), fakeProfileBasic(Fixed.CAROL))

    val all: Map<String, FollowListState>
        get() = pagedSamples(accounts, fakeProfileBasic()) { FollowListState(FollowListKind.Followers, it, eventSink = {}) }
}

object BlockListSamples {
    val accounts: ImmutableList<BlockListAccount> =
        persistentListOf(BlockListAccount(fakeProfileBasic(Fixed.BOB)), BlockListAccount(fakeProfileBasic(Fixed.CAROL)))

    val all: Map<String, BlockListState>
        get() = pagedSamples(accounts, Unit) { BlockListState(BlockListKind.Blocked, it, eventSink = {}) }
}

object EditProfileSamples {
    private fun state(
        content: ContentState<Unit>,
        displayName: String = "Alice",
        weight: String = "61.5",
    ) =
        EditProfileState(
            displayName = TextFieldState(displayName),
            description = TextFieldState("Trail runner and weekend rider."),
            avatar = null,
            locality = TextFieldState("Boulder"),
            region = TextFieldState("CO"),
            country = "US",
            birthDate = "1990-04-12",
            gender = TextFieldState(),
            weight = TextFieldState(weight),
            height = TextFieldState("168"),
            heightInches = TextFieldState(),
            units = UnitSystem.Metric,
            visibility = DetailsVisibility(),
            nowHidden = persistentListOf(),
            content = content,
            saving = false,
            error = null,
            eventSink = {},
        )

    val all: Map<String, EditProfileState>
        get() =
            mapOf(
                SampleKind.LOADING to state(ContentStates.loading(Unit), displayName = "", weight = ""),
                SampleKind.FAILED to state(ContentStates.failed(Unit), displayName = "", weight = ""),
                SampleKind.LOADED to state(ContentStates.loaded(Unit)),
                SampleKind.INVALID to state(ContentStates.loaded(Unit), weight = "heavy"),
            )
}
