package io.github.solcott.activitytracker.app.profile

import io.github.solcott.activitytracker.app.SignInAgainNotice
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.dataresult.DataError
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.pagedStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** What the follow lists and follow requests say and offer (graph/follow-requests, dev/shared-display-text). */
class FollowListsTextsTest {
    private fun account(name: String) = ProfileViewBasic(Did("did:plc:$name"), Handle("$name.test"), null, null)

    private val bob = account("bob")
    private val carol = account("carol")

    @Test
    fun eachListHasItsWords() {
        assertEquals(AppText.named("follow_list_followers"), FollowListTexts.title(FollowListKind.Followers))
        assertEquals(AppText.named("follow_list_following"), FollowListTexts.title(FollowListKind.Following))
        assertEquals(AppText.named("follow_list_no_followers"), FollowListTexts.empty(FollowListKind.Followers))
        assertEquals(AppText.named("follow_list_no_following"), FollowListTexts.empty(FollowListKind.Following))
        assertEquals(UiError.Kind.LoadFollowers, FollowListState(FollowListKind.Followers, pagedStateOf(), eventSink = {}).loadErrorKind)
        assertEquals(UiError.Kind.LoadFollowing, FollowListState(FollowListKind.Following, pagedStateOf(), eventSink = {}).loadErrorKind)
    }

    @Test
    fun theSubtitleIsWhoseListItIsOnceKnown() {
        assertEquals(LabelText.handle("bob.test"), FollowListTexts.subtitle(bob))
        assertNull(FollowListTexts.subtitle(null))
    }

    @Test
    fun aFollowerIsRemovingWhileItsRemovalIsWritten() {
        val state =
            FollowListState(FollowListKind.Followers, pagedStateOf(), canRemove = true, removing = persistentSetOf(bob.did), eventSink = {})
        assertTrue(state.isRemoving(bob))
        assertFalse(state.isRemoving(carol))
    }

    private fun requests(
        tab: FollowRequestsTab,
        requests: List<ProfileViewBasic> = listOf(bob),
        declined: List<ProfileViewBasic> = listOf(carol),
        truncated: Boolean = false,
    ) =
        FollowRequestsState(
            tab = tab,
            content =
                ContentState(
                    FollowRequestsContent(
                        persistentListOf(*requests.toTypedArray()),
                        persistentListOf(*declined.toTypedArray()),
                        truncated,
                    ),
                ),
        )

    @Test
    fun eachTabHasItsWordsAndAccounts() {
        assertEquals(AppText.named("follow_requests_tab_requests"), FollowRequestsTexts.tab(FollowRequestsTab.Requests))
        assertEquals(AppText.named("follow_requests_tab_declined"), FollowRequestsTexts.tab(FollowRequestsTab.Declined))
        assertEquals(AppText.named("follow_requests_none"), FollowRequestsTexts.empty(FollowRequestsTab.Requests))
        assertEquals(AppText.named("follow_requests_none_declined"), FollowRequestsTexts.empty(FollowRequestsTab.Declined))
        assertEquals(listOf(bob), requests(FollowRequestsTab.Requests).accounts)
        assertEquals(listOf(carol), requests(FollowRequestsTab.Declined).accounts)
    }

    @Test
    fun onlyRequestsCanBeDeclined() {
        assertTrue(requests(FollowRequestsTab.Requests).offersDecline)
        assertFalse(requests(FollowRequestsTab.Declined).offersDecline)
    }

    @Test
    fun theDeclinedNoteShowsOverADeclinedListThatListsSomeone() {
        assertTrue(requests(FollowRequestsTab.Declined).showsDeclinedNote)
        assertFalse(requests(FollowRequestsTab.Declined, declined = emptyList()).showsDeclinedNote)
        assertFalse(requests(FollowRequestsTab.Requests).showsDeclinedNote)
    }

    @Test
    fun theTruncatedNoteShowsOnRequestsAlone() {
        assertTrue(requests(FollowRequestsTab.Requests, truncated = true).showsTruncatedNote)
        assertFalse(requests(FollowRequestsTab.Declined, truncated = true).showsTruncatedNote)
        assertFalse(requests(FollowRequestsTab.Requests).showsTruncatedNote)
    }

    @Test
    fun anAccountCantBeAnsweredWhileItsAnswerIsWrittenOrWhenItCantBeAtAll() {
        val odd = account("odd")
        val state = requests(FollowRequestsTab.Requests).copy(answering = persistentSetOf(bob.did), unanswerable = persistentSetOf(odd.did))
        assertFalse(state.canAnswer(bob))
        assertFalse(state.canAnswer(odd))
        assertTrue(state.canAnswer(carol))
    }

    @Test
    fun theNoticeIsTheSharedRules() {
        val failed = UiError(UiError.Kind.AnswerFollowRequest, DataError.Network)
        assertNull(requests(FollowRequestsTab.Requests).notice)
        assertEquals(SignInAgainNotice.of(null, true), requests(FollowRequestsTab.Requests).copy(needsSignIn = true).notice)
        assertEquals(SignInAgainNotice(failed, false), requests(FollowRequestsTab.Requests).copy(error = failed).notice)
    }
}
