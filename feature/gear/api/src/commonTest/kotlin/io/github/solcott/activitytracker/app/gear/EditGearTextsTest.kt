package io.github.solcott.activitytracker.app.gear

import io.github.solcott.activitytracker.app.edit.PickedPhoto
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.dataresult.Origin
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.LoadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate
import kotlinx.io.bytestring.ByteString

/** What the gear and collection forms say and offer (dev/shared-display-text). */
class EditGearTextsTest {
    /** A saved bike for no sport, with nothing mounted, retired or pictured; each test changes what it is about. */
    private val base =
        EditGearState(
            content = ContentState(Unit, Origin.Network, LoadStatus.Idle),
            isNew = false,
            name = textFieldStateOf(""),
            brand = textFieldStateOf(""),
            model = textFieldStateOf(""),
            weight = textFieldStateOf(""),
            startingDistance = textFieldStateOf(""),
            startingTime = textFieldStateOf(""),
            units = UnitSystem.Metric,
            notes = textFieldStateOf(""),
            type = GearType.BIKE,
            types = GearType.all.toImmutableList(),
            kind = null,
            kinds = GearType.kinds(GearType.BIKE).toImmutableList(),
            sports = persistentListOf(),
            visibility = Visibility.SELF,
            acquiredOn = null,
            retiredOn = null,
            photo = null,
            nameMissing = false,
            saving = false,
            confirmingDelete = false,
            approvesFollowers = false,
            error = null,
            eventSink = {},
        )

    private fun state() = base

    private fun state(sports: List<String>) = base.copy(sports = sports.toImmutableList())

    private fun state(defaultFor: List<ActivityType>, sports: List<String> = emptyList()) =
        base.copy(defaultFor = defaultFor.toImmutableList(), sports = sports.toImmutableList())

    private fun pair(a: AppText, b: AppText) = AppText.named("text_list_pair", a, b)

    @Test
    fun titlesSayWhetherItIsNew() {
        assertEquals(AppText.named("gear_new_title"), base.copy(isNew = true).title)
        assertEquals(AppText.named("gear_edit_title"), state().title)
        assertEquals(AppText.named("gear_collection_new"), EditGearTexts.collectionTitle(isNew = true))
        assertEquals(AppText.named("gear_collection_edit_title"), EditGearTexts.collectionTitle(isNew = false))
    }

    @Test
    fun sportsAreJoinedOrAnySport() {
        assertEquals(AppText.named("gear_sports_none"), state().sportsLabel)
        assertEquals(LabelText.sport(Sport.RIDE), state(sports = listOf(Sport.RIDE)).sportsLabel)
        assertEquals(
            pair(pair(LabelText.sport(Sport.RIDE), LabelText.sport(Sport.RUN)), LabelText.sport(Sport.SWIM)),
            state(sports = listOf(Sport.RIDE, Sport.RUN, Sport.SWIM)).sportsLabel,
        )
    }

    @Test
    fun defaultsAreJoinedOrNone() {
        assertEquals(AppText.named("gear_default_for_none"), state().defaultForLabel)
        val gravel = ActivityType(Sport.RIDE, "gravel")
        assertEquals(
            pair(LabelText.activityType(ActivityType(Sport.RIDE)), LabelText.activityType(gravel)),
            state(defaultFor = listOf(ActivityType(Sport.RIDE), gravel)).defaultForLabel,
        )
    }

    @Test
    fun theKindIsItsNameOrNone() {
        assertEquals(AppText.named("gear_kind_none"), state().kindLabel)
        assertEquals(GearTexts.kind(GearType.BIKE, "gravel"), base.copy(kind = "gravel").kindLabel)
        val options = state().kindOptions
        assertEquals(GearKindOption(null, AppText.named("gear_kind_none")), options.first())
        assertEquals(GearType.kinds(GearType.BIKE), options.drop(1).map { it.kind })
    }

    @Test
    fun aTypeWithoutKindsOffersNone() {
        val other = base.copy(type = GearType.OTHER, kinds = persistentListOf())
        assertFalse(other.showsKind)
        assertTrue(state().showsKind)
    }

    @Test
    fun theStartingDistanceIsInTheAthletesUnits() {
        assertEquals(AppText.named("gear_starting_distance_km"), state().startingDistanceLabel)
        assertEquals(AppText.named("gear_starting_distance_mi"), base.copy(units = UnitSystem.Imperial).startingDistanceLabel)
    }

    @Test
    fun thePhotoButtonAddsOrChanges() {
        assertEquals(AppText.named("gear_photo_add"), state().photoAction)
        assertEquals(AppText.named("gear_photo_change"), base.copy(photo = GearPhoto.Saved("https://x/a.webp")).photoAction)
        val picked = GearPhoto.Picked(PickedPhoto(ByteString(1, 2), "image/webp", 1, 1))
        assertEquals(AppText.named("gear_photo_change"), base.copy(photo = picked).photoAction)
    }

    @Test
    fun mountedAndRetiredLinesShowTheirDays() {
        val mounted = GearMountedOn("at://x", "Gravel bike", LocalDate(2026, 7, 1))
        assertEquals(
            AppText.named("gear_mounted_on_since", AppText.written("Gravel bike"), AppText.written("2026-07-01")),
            base.copy(mountedOn = mounted).mountedOnLabel,
        )
        assertNull(state().mountedOnLabel)
        assertEquals(
            AppText.named("gear_retired_on", AppText.written("2026-08-01")),
            base.copy(retiredOn = LocalDate(2026, 8, 1)).retiredOnLabel,
        )
        assertTrue(base.copy(retiredOn = LocalDate(2026, 8, 1)).isRetired)
        assertNull(state().retiredOnLabel)
        assertFalse(state().isRetired)
    }

    @Test
    fun saveShowsOnceTheFormHasLoaded() {
        assertTrue(state().showsSave)
        assertFalse(state().copy(content = ContentState(Unit, null, LoadStatus.Loading)).showsSave)
    }

    @Test
    fun savingDisablesTheForm() {
        assertTrue(state().enabled)
        assertFalse(base.copy(saving = true).enabled)
    }

    @Test
    fun managementShowsForASavedItem() {
        assertTrue(state().showsManagement)
        assertFalse(base.copy(isNew = true).showsManagement)
    }

    @Test
    fun membershipIsAskedOfTheState() {
        val s = state(defaultFor = listOf(ActivityType(Sport.RIDE, "gravel")), sports = listOf(Sport.RIDE))
        assertTrue(s.isForSport(Sport.RIDE))
        assertFalse(s.isForSport(Sport.RUN))
        assertTrue(s.isDefaultFor(ActivityType(Sport.RIDE, "gravel")))
        assertFalse(s.isDefaultFor(ActivityType(Sport.RIDE)))
    }

    @Test
    fun visibilityOffersAllFourAndTheFollowersNote() {
        assertEquals(Visibility.all, state().visibilityOptions)
        assertNull(state().followersNote)
        assertEquals(AppText.named("visibility_followers_open_note"), base.copy(visibility = Visibility.FOLLOWERS).followersNote)
        assertNull(base.copy(visibility = Visibility.FOLLOWERS, approvesFollowers = true).followersNote)
    }

    @Test
    fun theCollectionFormsConditions() {
        val collection =
            EditGearCollectionState(
                content = ContentState(Unit),
                isNew = true,
                name = textFieldStateOf(""),
                notes = textFieldStateOf(""),
                eventSink = {},
            )
        assertEquals(AppText.named("gear_collection_new"), collection.title)
        assertEquals(AppText.named("gear_collection_edit_title"), collection.copy(isNew = false).title)
        assertTrue(collection.enabled)
        assertFalse(collection.copy(saving = true).enabled)
        assertTrue(collection.canAdd)
        assertFalse(collection.copy(full = true).canAdd)
    }

    @Test
    fun limitsAreTheRecordsAndTheFormsOwn() {
        assertEquals(7, EditGearLimits.weightDigits)
        assertEquals(9, EditGearLimits.starting)
        assertTrue(EditGearLimits.name > 0 && EditGearLimits.collectionName > 0)
    }

    @Test fun aJoinedListOfNoneIsNull() = assertNull(AppText.joined(persistentListOf()))
}
