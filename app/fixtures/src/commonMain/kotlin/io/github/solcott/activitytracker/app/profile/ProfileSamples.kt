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
import io.github.solcott.activitytracker.data.profile.DetailGroup
import io.github.solcott.activitytracker.data.profile.DetailsVisibility
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.FollowPolicy
import io.github.solcott.activitytracker.lexicon.app.LabelView
import io.github.solcott.activitytracker.lexicon.app.Location
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.ProfileViewerState
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.dataresult.DataError
import io.github.solcott.uistate.ContentState
import kotlin.io.encoding.Base64
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.io.bytestring.ByteString

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

    private val follow = AtUri("at://did:plc:bob/app.activitytracker.graph.follow/3lfollow")
    private val block = AtUri("at://did:plc:bob/app.activitytracker.graph.block/3lblock")
    private val labeler = Did("did:plc:labeler")

    /**
     * What [all]'s content-loading states don't show: Following and Requested, a follow being written, a muted athlete, a block (and one
     * held only on Bluesky), the athlete's own profile while they approve followers with more requests than counted and two invites, every
     * detail, a labelled avatar with a warning, a hidden athlete reached by link, and a failed action. Kept apart from [all], whose keys
     * are the content-loading conformance set.
     */
    val variants: Map<String, ProfileState>
        get() {
            fun withViewer(viewer: ProfileViewerState) = profile.copy(viewer = viewer)
            return mapOf(
                "following" to state(ContentStates.loaded(withViewer(ProfileViewerState(following = follow)))),
                "requested" to state(ContentStates.loaded(withViewer(ProfileViewerState(following = follow, followRequested = true)))),
                "followPending" to loaded.copy(followPending = true),
                "muted" to loaded.copy(muting = true),
                "blocked" to
                    state(ContentStates.loaded(withViewer(ProfileViewerState(blocking = block))))
                        .copy(
                            blocking = true,
                            activities = ContentStates.loaded(persistentListOf()),
                        ),
                "blockedOnBluesky" to
                    state(ContentStates.loaded(withViewer(ProfileViewerState(blocking = block))))
                        .copy(
                            blocking = true,
                            blockedOnBluesky = true,
                            activities = ContentStates.loaded(persistentListOf()),
                        ),
                "ownApproving" to
                    own.copy(
                        content = ContentStates.loaded(profile.copy(followPolicy = FollowPolicy.APPROVE)),
                        followRequestCount = 500,
                        moreFollowRequests = true,
                        activityInviteCount = 2,
                    ),
                "details" to
                    state(
                        ContentStates.loaded(
                            profile.copy(
                                details =
                                    ProfileDetails(
                                        location = Location(locality = "Boulder", region = "CO", country = "US"),
                                        birthDate = "1990-04-12",
                                        gender = "female",
                                        height = 1680,
                                        weight = 61_500,
                                    ),
                            ),
                        ),
                    ),
                "labeled" to
                    state(
                        ContentStates.loaded(
                            profile.copy(
                                avatar = "https://example.test/alice.jpg",
                                labels = listOf(LabelView(labeler, "porn"), LabelView(labeler, "spam")),
                            ),
                        ),
                    ),
                "hidden" to state(ContentStates.loaded(profile.copy(labels = listOf(LabelView(labeler, "!hide"))))),
                "actionFailed" to loaded.copy(error = UiError(UiError.Kind.UpdateFollow, DataError.Network)),
            )
        }
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
    /** Who may see each group, as the presenter always fills it: the location public, the birthdate hidden, gender to followers. */
    private val visibility =
        DetailsVisibility(
            location = Visibility.PUBLIC,
            birthDate = Visibility.SELF,
            gender = Visibility.FOLLOWERS,
            weight = Visibility.MUTUALS,
        )

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
            visibility = visibility,
            nowHidden = persistentListOf(),
            content = content,
            saving = false,
            error = null,
            eventSink = {},
        )

    private val loaded: EditProfileState
        get() = state(ContentStates.loaded(Unit))

    val all: Map<String, EditProfileState>
        get() =
            mapOf(
                SampleKind.LOADING to state(ContentStates.loading(Unit), displayName = "", weight = ""),
                SampleKind.FAILED to state(ContentStates.failed(Unit), displayName = "", weight = ""),
                SampleKind.LOADED to loaded,
                SampleKind.INVALID to state(ContentStates.loaded(Unit), weight = "heavy"),
            )

    /**
     * What [all]'s content-loading states don't show: imperial units with a self-described gender and no country or birthdate; a saved
     * photo and a picked one; the location public until now and set to Followers; approving followers with gender to Followers (so no
     * note); saving; a failed save; and a session that must sign in again. Kept apart from [all], whose keys are the content-loading
     * conformance set.
     */
    val variants: Map<String, EditProfileState>
        get() =
            mapOf(
                "imperial" to
                    loaded.copy(
                        units = UnitSystem.Imperial,
                        weight = textFieldStateOf("135"),
                        height = textFieldStateOf("5"),
                        heightInches = textFieldStateOf("6"),
                        gender = textFieldStateOf("Agender"),
                        country = "",
                        birthDate = "",
                    ),
                "savedPhoto" to loaded.copy(avatar = AvatarImage.Saved("https://example.test/alice.jpg")),
                "pickedPhoto" to loaded.copy(avatar = AvatarImage.Picked(ByteString(Base64.decode(PICKED_PNG)))),
                "nowHidden" to
                    loaded.copy(
                        visibility = visibility.copy(location = Visibility.FOLLOWERS),
                        nowHidden = persistentListOf(DetailGroup.Location),
                    ),
                "approving" to loaded.copy(approveFollowers = true),
                "saving" to loaded.copy(saving = true),
                "saveFailed" to loaded.copy(error = UiError(UiError.Kind.SaveProfile, DataError.Network)),
                "needsSignIn" to loaded.copy(needsSignIn = true),
            )

    /** An 8 × 8 teal PNG, standing in for a picked photo's bytes. */
    private const val PICKED_PNG = "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAIAAABLbSncAAAAEUlEQVR4nGPQmtuPFTEMLQkAT3dVgRdrT9YAAAAASUVORK5CYII="
}
