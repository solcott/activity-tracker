package io.github.solcott.activitytracker.app.post

import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.fakeQuote
import io.github.solcott.activitytracker.app.fixtures.fakeUnavailableQuote
import io.github.solcott.activitytracker.app.fixtures.pagedSamples
import io.github.solcott.activitytracker.app.report.Reporting
import io.github.solcott.activitytracker.lexicon.app.PostView
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** The quotes of Bob's post (feed/quote-posts), as Alice sees them. */
object PostQuotesSamples {
    // Two, so a page's footer shows under them.
    private val quotes: ImmutableList<PostView> = persistentListOf(fakeQuote(), fakeUnavailableQuote())

    val all: Map<String, PostQuotesState>
        get() =
            pagedSamples(quotes, Unit) {
                PostQuotesState(it, viewerDid = Fixed.ALICE.value, reporting = Reporting(available = true), eventSink = {})
            } + ("notFound" to notFound)

    val loaded: PostQuotesState
        get() = all.getValue(SampleKind.LOADED)

    /** The viewer may no longer see the quoted post. */
    val notFound: PostQuotesState
        get() = PostQuotesState(notFound = true, viewerDid = Fixed.ALICE.value, eventSink = {})
}
