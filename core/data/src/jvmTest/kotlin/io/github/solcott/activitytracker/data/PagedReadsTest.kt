package io.github.solcott.activitytracker.data

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.profile.AthleteSearchRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.profile.TrainingNarrowing
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.app.BlockSource
import io.github.solcott.activitytracker.lexicon.app.GetBlocks
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.dataresult.DataError
import io.github.solcott.dataresult.Origin
import io.github.solcott.dataresult.Outcome
import io.github.solcott.uistate.Page
import io.github.solcott.uistate.pageOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** The page reads paged lists load (ui/content-loading): each page as an outcome, and failures as reasons, never exceptions. */
class PagedReadsTest {
    private val pds = FakeAthletePds()
    private val bob = basic(Did("did:plc:bob"))
    private val carol = basic(Did("did:plc:carol"))

    private fun basic(of: Did) = ProfileViewBasic(of, Handle("${of.value.substringAfterLast(':')}.test"))

    private fun profiles(s: FakeAthletePds.Session) = ProfileRepository(s.clients, Clock.System, Logger, TrainingNarrowing {})

    private fun <T> Outcome<T>.cause(): DataError = assertIs<Outcome.Error>(this).cause

    @Test
    fun blocksAndMutesArePages() = runTest {
        val s = pds.session()
        val block = GetBlocks.BlockView(bob, listOf(BlockSource.APP), Instant.parse("2026-10-21T09:00:00Z"))
        pds.blocks = listOf(block)
        pds.mutes = listOf(carol)
        assertEquals(Outcome.Data(pageOf(listOf(block), null), Origin.Network), s.blocks.blocksPage(null))
        assertEquals(Outcome.Data(pageOf(listOf(carol), null), Origin.Network), s.blocks.mutesPage(null))
    }

    @Test
    fun followListsAreHeadedByTheirSubject() = runTest {
        val s = pds.session()
        pds.followers = listOf(bob)
        pds.follows = listOf(carol)
        val subject = ProfileViewBasic(Did("did:plc:dave"), Handle("subject.test"))
        assertEquals(Outcome.Data(Page(listOf(bob), subject, null), Origin.Network), profiles(s).followersPage("did:plc:dave", null))
        assertEquals(Outcome.Data(Page(listOf(carol), subject, null), Origin.Network), profiles(s).followsPage("did:plc:dave", null))
    }

    @Test
    fun searchIsAPage() = runTest {
        val s = pds.session()
        pds.searched = listOf(bob, carol)
        assertEquals(
            Outcome.Data(pageOf(listOf(bob, carol), null), Origin.Network),
            AthleteSearchRepository(s.clients).searchPage("b", null),
        )
    }

    @Test
    fun offlineIsANetworkFailure() = runTest {
        val s = pds.session()
        pds.appViewOffline = true
        assertEquals(DataError.Network, s.blocks.blocksPage(null).cause())
        assertEquals(DataError.Network, s.blocks.mutesPage(null).cause())
        assertEquals(DataError.Network, profiles(s).followersPage("did:plc:dave", null).cause())
        assertEquals(DataError.Network, AthleteSearchRepository(s.clients).searchPage("b", "30").cause())
    }

    @Test
    fun aServerErrorIsAnApiFailure() = runTest {
        val s = pds.session()
        pds.appViewFailing = true
        assertIs<DataError.Api>(s.blocks.blocksPage(null).cause())
        assertIs<DataError.Api>(profiles(s).followsPage("did:plc:dave", "30").cause())
        assertIs<DataError.Api>(AthleteSearchRepository(s.clients).searchPage("b", null).cause())
    }
}
