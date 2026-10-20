package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.activityfile.ActivityFileException
import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.activityfile.FitDecoder
import io.github.solcott.activitytracker.app.detail.FigureKey
import io.github.solcott.activitytracker.app.detail.FigureValue
import io.github.solcott.activitytracker.app.record.ImportActivityEvent
import io.github.solcott.activitytracker.app.record.ImportActivityPresenter
import io.github.solcott.activitytracker.app.record.ImportActivityScreen
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString

/** The import screen reads a file by its content, whatever its name, and turns away what it can't read. */
class ImportFileFormatTest {
    private val fixture = SessionTestFixture()

    @Test
    fun aGpxFileIsReadByItsContentWhateverItsName() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen())
        val gpx =
            """<gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1"><trk><type>cycling</type><trkseg>
                <trkpt lat="47.6" lon="-122.3"><time>2026-09-20T07:00:00Z</time></trkpt>
                <trkpt lat="47.61" lon="-122.3"><time>2026-09-20T07:05:00Z</time></trkpt>
            </trkseg></trk></gpx>"""
        presenterTestOf({
            ImportActivityPresenter(
                ImportActivityScreen(),
                navigator,
                repos.activities,
                repos.manager,
                ActivityFileReader { error("not FIT") },
                repos.typeChoices,
                repos.profiles,
                repos.feed,
                repos.defaults,
                repos.units,
                repos.suggestions,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("export.dat", ByteString(gpx.encodeToByteArray())))
            val read = awaitUntil { it.preview != null }
            assertEquals(ActivityType(Sport.RIDE), read.type.selected)
            val distance = assertNotNull(awaitUntil { it.page != null }.page).hero.first { it.key == FigureKey.Distance }.value
            assertEquals(1112.0, (distance as FigureValue.Distance).meters, 1.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun otherFilesAreNotSupported() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen())
        presenterTestOf({
            ImportActivityPresenter(
                ImportActivityScreen(),
                navigator,
                repos.activities,
                repos.manager,
                ActivityFileReader { error("not called") },
                repos.typeChoices,
                repos.profiles,
                repos.feed,
                repos.defaults,
                repos.units,
                repos.suggestions,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("notes.txt", ByteString("just some notes".encodeToByteArray())))
            assertEquals(UiError(UiError.Kind.UnsupportedActivityFile, null), awaitUntil { it.error != null }.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Just a FIT header: the reader picks the decoder by the ".FIT" signature at byte 8. */
    private val fitHeader = ByteString(14, 16, 0, 0, 0, 0, 0, 0, '.'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'T'.code.toByte())

    @Test
    fun anUnreadableFileShowsAnError() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen())
        val decoder = FitDecoder { throw ActivityFileException("FIT file is corrupt or truncated") }
        presenterTestOf({
            ImportActivityPresenter(
                ImportActivityScreen(),
                navigator,
                repos.activities,
                repos.manager,
                ActivityFileReader(decoder),
                repos.typeChoices,
                repos.profiles,
                repos.feed,
                repos.defaults,
                repos.units,
                repos.suggestions,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("broken.fit", fitHeader))
            val failed = awaitUntil { it.error != null && !it.reading }
            assertEquals(UiError(UiError.Kind.ReadActivityFile, "FIT file is corrupt or truncated"), failed.error)
            assertNull(failed.preview)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(fixture.putRecords.isEmpty())
    }
}
