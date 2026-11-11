package io.github.solcott.activitytracker.app.profile

import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.feed.FeedSamples
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.PagedStates
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.fakeAdoptedPost
import io.github.solcott.activitytracker.app.fixtures.fakeProfile
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.app.fixtures.fakeVotedPost
import io.github.solcott.activitytracker.app.fixtures.pagedSamples
import io.github.solcott.activitytracker.data.profile.DetailsVisibility
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.dataresult.DataError
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList

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

    /** Posts chosen: the athlete's shared proposals (feed/posts). */
    val posts: ProfileState
        get() = loaded.copy(tab = ProfileTab.Posts, posts = PagedStates.end(listOf(fakeVotedPost(), fakeAdoptedPost()), Unit))

    /** Posts chosen, and the athlete has none the viewer may see. */
    val noPosts: ProfileState
        get() = loaded.copy(tab = ProfileTab.Posts, posts = PagedStates.end(emptyList(), Unit))

    /** Posts chosen, and their first page failed. */
    val postsFailed: ProfileState
        get() = loaded.copy(tab = ProfileTab.Posts, posts = PagedStates.failed())

    /** The viewer's own profile. Not named `self`: Swift export can't carry a member of that name (dev/ios-swift-interop). */
    val own: ProfileState
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
                "self" to own,
                "posts" to posts,
                "noPosts" to noPosts,
                "postsFailed" to postsFailed,
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

    /**
     * What [all]'s content-loading states don't show: the Declined tab, listing dave or no one; bob's answer being written, an account
     * whose DID isn't a valid record key and the last answer failed; a session that must sign in again to decline; and more requests than
     * were read. Kept apart from [all], whose keys are the content-loading conformance set.
     */
    val variants: Map<String, FollowRequestsState>
        get() =
            mapOf(
                "declined" to
                    FollowRequestsState(
                        tab = FollowRequestsTab.Declined,
                        content = ContentStates.loaded(content.copy(declined = persistentListOf(fakeProfileBasic(DAVE)))),
                    ),
                "declinedEmpty" to FollowRequestsState(tab = FollowRequestsTab.Declined, content = ContentStates.loaded(content)),
                "answering" to
                    FollowRequestsState(
                        content =
                            ContentStates.loaded(
                                content.copy(
                                    requests =
                                        (content.requests + fakeProfileBasic(ODD, handle = "dev.test", displayName = null))
                                            .toImmutableList(),
                                ),
                            ),
                        answering = persistentSetOf(Fixed.BOB),
                        unanswerable = persistentSetOf(ODD),
                        error = UiError(UiError.Kind.AnswerFollowRequest, DataError.Network),
                    ),
                "needsSignIn" to FollowRequestsState(content = ContentStates.loaded(content), needsSignIn = true),
                "truncated" to FollowRequestsState(content = ContentStates.loaded(content.copy(truncated = true))),
            )

    private val DAVE = Did("did:plc:dave")

    /** An account whose DID isn't a valid record key (`%` isn't allowed in one), so it can't be answered. */
    private val ODD = Did("did:web:localhost%3A3000")
}

object FollowListSamples {
    val accounts: ImmutableList<ProfileViewBasic> = persistentListOf(fakeProfileBasic(Fixed.BOB), fakeProfileBasic(Fixed.CAROL))

    val all: Map<String, FollowListState>
        get() = pagedSamples(accounts, fakeProfileBasic()) { FollowListState(FollowListKind.Followers, it, eventSink = {}) }

    /**
     * What [all]'s content-loading states don't show: bob's Following list, listing alice and carol or no one, and alice's own followers
     * while she approves followers, with bob's removal being written and the last removal failed. Kept apart from [all], whose keys are the
     * content-loading conformance set.
     */
    val variants: Map<String, FollowListState>
        get() {
            val bob = fakeProfileBasic(Fixed.BOB)
            return mapOf(
                "following" to
                    FollowListState(
                        FollowListKind.Following,
                        PagedStates.end(listOf(fakeProfileBasic(), fakeProfileBasic(Fixed.CAROL)), bob),
                        eventSink = {},
                    ),
                "followingEmpty" to FollowListState(FollowListKind.Following, PagedStates.end(emptyList(), bob), eventSink = {}),
                "removable" to
                    FollowListState(
                        FollowListKind.Followers,
                        PagedStates.end(accounts, fakeProfileBasic()),
                        canRemove = true,
                        removing = persistentSetOf(Fixed.BOB),
                        actionError = UiError(UiError.Kind.RemoveFollower, DataError.Network),
                        eventSink = {},
                    ),
            )
        }
}

object BlockListSamples {
    val accounts: ImmutableList<BlockListAccount> =
        persistentListOf(BlockListAccount(fakeProfileBasic(Fixed.BOB)), BlockListAccount(fakeProfileBasic(Fixed.CAROL)))

    val all: Map<String, BlockListState>
        get() = pagedSamples(accounts, Unit) { BlockListState(BlockListKind.Blocked, it, eventSink = {}) }

    /**
     * What [all]'s content-loading states don't show: the muted list, and the blocked list with bob's unblock being written, a block made
     * only on Bluesky (dave) and the last unblock failed. Kept apart from [all], whose keys are the content-loading conformance set.
     */
    val variants: Map<String, BlockListState>
        get() =
            mapOf(
                "muted" to BlockListState(BlockListKind.Muted, PagedStates.end(accounts, Unit), eventSink = {}),
                "mutedEmpty" to BlockListState(BlockListKind.Muted, PagedStates.end(emptyList(), Unit), eventSink = {}),
                "actions" to
                    BlockListState(
                        BlockListKind.Blocked,
                        PagedStates.end(
                            accounts + BlockListAccount(fakeProfileBasic(DAVE, displayName = null), removable = false, onBluesky = true),
                            Unit,
                        ),
                        removing = persistentSetOf(Fixed.BOB),
                        actionError = UiError(UiError.Kind.UpdateBlock, DataError.Network),
                        eventSink = {},
                    ),
            )

    private val DAVE = Did("did:plc:dave")
}

object EditProfileSamples {
    private fun state(
        content: ContentState<Unit>,
        displayName: String = "Alice",
        weight: String = "61.5",
    ) =
        EditProfileState(
            displayName = textFieldStateOf(displayName),
            description = textFieldStateOf("Trail runner and weekend rider."),
            avatar = null,
            locality = textFieldStateOf("Boulder"),
            region = textFieldStateOf("CO"),
            country = "US",
            birthDate = "1990-04-12",
            gender = textFieldStateOf(),
            weight = textFieldStateOf(weight),
            height = textFieldStateOf("168"),
            heightInches = textFieldStateOf(),
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
