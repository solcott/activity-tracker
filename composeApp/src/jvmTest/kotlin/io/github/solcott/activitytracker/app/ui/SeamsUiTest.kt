package io.github.solcott.activitytracker.app.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.detail.DataSourceRow
import io.github.solcott.activitytracker.app.detail.DataSources
import io.github.solcott.activitytracker.app.detail.DataSourcesSection
import io.github.solcott.activitytracker.app.detail.FigureValue
import io.github.solcott.activitytracker.app.detail.startTime
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.fakeProfile
import io.github.solcott.activitytracker.app.preview.SessionPreviewSubUis
import io.github.solcott.activitytracker.app.profile.ProfileSamples
import io.github.solcott.activitytracker.app.profile.ProfileUi
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.data.activity.DataKind
import io.github.solcott.activitytracker.lexicon.app.MemberView
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.TimeZone

/** UIs take the time, time zone and clock style from values a test sets, not from the machine (dev/ui-test-harness). */
@OptIn(ExperimentalTestApi::class)
class SeamsUiTest {
    private val profile =
        ProfileSamples.loaded.copy(content = ContentStates.loaded(fakeProfile(details = ProfileDetails(birthDate = "1990-03-15"))))

    @Test
    fun aProfilesAgeIsFromTheSetTime() {
        runComposeUiTest {
            setContent { AppPreview(subUis = SessionPreviewSubUis) { ProfileUi(profile) } }
            onNodeWithText("36 years old", substring = true).assertExists()
        }
        runComposeUiTest {
            setContent { AppPreview(subUis = SessionPreviewSubUis, now = Instant.parse("2027-06-01T12:00:00Z")) { ProfileUi(profile) } }
            onNodeWithText("37 years old", substring = true).assertExists()
        }
    }

    @Test
    fun aDevicelessSourceIsNamedByItsStartInTheSetZone() {
        val member =
            MemberView(Fixed.uri(Fixed.ALICE, "app.activitytracker.feed.activity", "3kmember"), Fixed.STARTED_AT, elapsedTime = 3_600)
        val sources =
            DataSources(persistentListOf(DataSourceRow(DataKind.HeartRate, included = true, chosen = member, offered = persistentListOf())))
        runComposeUiTest {
            setContent {
                AppPreview(subUis = SessionPreviewSubUis, timeZone = TimeZone.UTC) { DataSourcesSection(sources, eventSink = {}) }
            }
            onNodeWithText("Recording started 07:00", substring = true).assertExists()
        }
        runComposeUiTest {
            setContent {
                AppPreview(subUis = SessionPreviewSubUis, timeZone = TimeZone.of("America/Denver")) {
                    DataSourcesSection(sources, eventSink = {})
                }
            }
            onNodeWithText("Recording started 01:00", substring = true).assertExists()
        }
    }

    @Test
    fun aStartTimeFollowsTheSetClockStyle() {
        val value = FigureValue.StartTime(Instant.parse("2026-05-31T14:05:00Z").epochSeconds, utcOffsetMinutes = 0, dateOnly = false)
        runComposeUiTest {
            setContent { AppPreview(subUis = SessionPreviewSubUis, use24Hour = true) { Text(startTime(value)) } }
            onNodeWithText("14:05", substring = true).assertExists()
        }
        runComposeUiTest {
            setContent { AppPreview(subUis = SessionPreviewSubUis, use24Hour = false) { Text(startTime(value)) } }
            onNodeWithText("2:05 PM", substring = true).assertExists()
        }
    }
}
