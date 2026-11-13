package io.github.solcott.activitytracker.app.profile

import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.DateText
import io.github.solcott.activitytracker.data.profile.DetailGroup
import io.github.solcott.activitytracker.data.profile.DetailsVisibility
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.dataresult.DataError
import io.github.solcott.dataresult.Origin
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.LoadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate

/** What edit profile says and decides, for the shared and the native forms (profile/private-details, dev/shared-display-text). */
class EditProfileTextsTest {
    @Suppress("LongParameterList") // Each a part of the form a test sets.
    private fun state(
        displayName: String = "Alice",
        description: String = "Trail runner and weekend rider.",
        locality: String = "Boulder",
        weight: String = "61.5",
        height: String = "168",
        inches: String = "",
        units: UnitSystem = UnitSystem.Metric,
        birthDate: String = "1990-04-12",
        visibility: DetailsVisibility = DetailsVisibility(location = Visibility.PUBLIC, gender = Visibility.FOLLOWERS),
        approve: Boolean = false,
        nowHidden: List<DetailGroup> = emptyList(),
        content: ContentState<Unit> = ContentState(Unit, Origin.Network, LoadStatus.Idle),
        needsSignIn: Boolean = false,
        saving: Boolean = false,
        error: UiError? = null,
    ) =
        EditProfileState(
            displayName = textFieldStateOf(displayName),
            description = textFieldStateOf(description),
            avatar = null,
            locality = textFieldStateOf(locality),
            region = textFieldStateOf("CO"),
            country = "US",
            birthDate = birthDate,
            gender = textFieldStateOf(),
            weight = textFieldStateOf(weight),
            height = textFieldStateOf(height),
            heightInches = textFieldStateOf(inches),
            units = units,
            visibility = visibility,
            approveFollowers = approve,
            nowHidden = persistentListOf(*nowHidden.toTypedArray()),
            content = content,
            needsSignIn = needsSignIn,
            saving = saving,
            error = error,
            eventSink = {},
        )

    @Test
    fun countersSayHowMuchOfTheLimitIsUsed() {
        assertEquals(AppText.named("edit_profile_length", AppText.written("5"), AppText.written("64")), state().displayNameCounter)
        assertEquals(AppText.named("edit_profile_length", AppText.written("31"), AppText.written("256")), state().descriptionCounter)
        // The bio under a name Swift export carries.
        val filled = state()
        assertSame(filled.description, filled.bio)
        assertTrue(state().displayNameValid)
        assertFalse(state(displayName = "a".repeat(65)).displayNameValid)
        assertFalse(state(description = "a".repeat(257)).descriptionValid)
        assertFalse(state(description = "a".repeat(257)).canSave)
        assertTrue(state().detailValid("a".repeat(64)))
        assertFalse(state().detailValid("a".repeat(65)))
        assertFalse(state(locality = "a".repeat(65)).valid)
    }

    @Test
    fun saveIsOfferedOnlyWhenLoadedValidAndIdle() {
        assertTrue(state().canSave)
        assertFalse(state(saving = true).canSave)
        assertFalse(state(content = ContentState(Unit, null, LoadStatus.Loading)).canSave)
        assertFalse(state(weight = "heavy").canSave)
        assertFalse(state(weight = "heavy").weightValid)
        assertTrue(state(weight = "").weightValid)
    }

    @Test
    fun unitsChooseTheLabels() {
        assertEquals(AppText.named("edit_profile_weight_kg"), state().weightLabel)
        assertEquals(AppText.named("edit_profile_height_cm"), state().heightLabel)
        assertFalse(state().heightInFeetAndInches)
        val imperial = state(units = UnitSystem.Imperial, weight = "135", height = "5", inches = "6")
        assertEquals(AppText.named("edit_profile_weight_lb"), imperial.weightLabel)
        assertEquals(AppText.named("edit_profile_height_ft"), imperial.heightLabel)
        assertTrue(imperial.heightInFeetAndInches)
        assertTrue(imperial.heightValid)
        assertEquals(AppText.named("edit_profile_height_in_row"), EditProfileTexts.inchesRow)
        assertEquals(AppText.named("edit_profile_height_in"), EditProfileTexts.inchesBeside)
    }

    @Test
    fun eachGroupHasItsVisibilityAndNotes() {
        val shown = state()
        assertEquals(Visibility.PUBLIC, shown.visibilityOf(DetailGroup.Location))
        // None chosen reads as Only me, as the presenter fills it.
        assertEquals(Visibility.SELF, shown.visibilityOf(DetailGroup.Body))
        assertEquals(AppText.named("visibility_followers_open_note"), shown.followersNote(DetailGroup.Gender))
        assertNull(shown.followersNote(DetailGroup.Location))
        assertNull(state(approve = true).followersNote(DetailGroup.Gender))
        assertEquals(Visibility.all, shown.visibilityOptions)
    }

    @Test
    fun aHiddenPublicDetailIsWarnedOfUnderItsGroup() {
        val hidden = state(nowHidden = listOf(DetailGroup.Location))
        assertTrue(hidden.isNowHidden(DetailGroup.Location))
        assertFalse(hidden.isNowHidden(DetailGroup.Gender))
        assertEquals(
            AppText.named("edit_profile_now_hidden", AppText.named("detail_location")),
            EditProfileTexts.nowHidden(DetailGroup.Location),
        )
    }

    @Test
    fun theBirthdateIsACalendarDay() {
        assertEquals(LocalDate(1990, 4, 12), state().birthDay)
        assertNull(state(birthDate = "").birthDay)
        assertNull(state(birthDate = "1990-4-12").birthDay)
        assertEquals("1990-04-13", EditProfileEvent.BirthDateChanged(LocalDate(1990, 4, 13)).value)
        assertEquals("", EditProfileEvent.BirthDateChanged(null as LocalDate?).value)
    }

    @Test
    fun theBirthdateReadsWrittenOut() {
        assertEquals(DateText.date(1990, 4, 12), EditProfileTexts.birthDate(state()))
        assertNull(EditProfileTexts.birthDate(state(birthDate = "")))
    }

    @Test
    fun theNoticeIsTheFailureOrSigningInAgain() {
        assertNull(state().signInAgainNotice)
        val signIn = state(needsSignIn = true).signInAgainNotice
        assertEquals(UiError.Kind.SignInAgain, signIn?.error?.kind)
        assertEquals(true, signIn?.offersSignInAgain)
        val failed = state(error = UiError(UiError.Kind.SaveProfile, DataError.Network)).signInAgainNotice
        assertEquals(UiError.Kind.SaveProfile, failed?.error?.kind)
        assertEquals(false, failed?.offersSignInAgain)
    }

    @Test
    fun genderChoicesKeepASelfDescription() {
        assertEquals(listOf("", "female", "male", "nonBinary", GenderChoices.SELF_DESCRIBE), GenderChoices.all)
        assertEquals(AppText.named("edit_profile_not_set"), GenderChoices.label(GenderChoices.NOT_SET))
        assertEquals(AppText.named("gender_female"), GenderChoices.label("female"))
        assertEquals(AppText.named("gender_self_describe"), GenderChoices.label(GenderChoices.SELF_DESCRIBE))
        // A saved description is a self-description.
        assertEquals(GenderChoices.SELF_DESCRIBE, GenderChoices.chosen("Agender", chose = false))
        assertEquals("female", GenderChoices.chosen("female", chose = false))
        assertEquals(GenderChoices.NOT_SET, GenderChoices.chosen("", chose = false))
        // Just chosen, before any words: still Self-describe, with its field.
        assertEquals(GenderChoices.SELF_DESCRIBE, GenderChoices.chosen("", chose = true))
        assertTrue(GenderChoices.selfDescribes("", chose = true))
        assertFalse(GenderChoices.selfDescribes("male", chose = false))
        // Self-describe keeps words already typed, and starts empty from a known value.
        assertEquals("Agender", GenderChoices.valueFor(GenderChoices.SELF_DESCRIBE, "Agender"))
        assertEquals("", GenderChoices.valueFor(GenderChoices.SELF_DESCRIBE, "female"))
        assertEquals("male", GenderChoices.valueFor("male", "Agender"))
    }

    @Test
    fun countriesAreSearchedByCodeThenName() {
        val names =
            CountrySearch.sorted(
                listOf(
                    CountryName("US", "United States", "united states"),
                    CountryName("AU", "Australia", "australia"),
                    CountryName("CI", "Côte d'Ivoire", "cote d'ivoire"),
                    CountryName("BY", "Belarus", "belarus"),
                ),
            )
        assertEquals(listOf("AU", "BY", "CI", "US"), names.map { it.code })
        assertEquals(names, CountrySearch.matches(names, ""))
        assertEquals(listOf("US", "AU", "BY"), CountrySearch.matches(names, "us").map { it.code })
        assertEquals(listOf("CI"), CountrySearch.matches(names, "cote").map { it.code })
        assertEquals(emptyList(), CountrySearch.matches(names, "zz"))
        assertEquals(249, CountrySearch.codes.size)
        assertTrue("US" in CountrySearch.codes)
    }

    @Test
    fun theAvatarIsASmallJpeg() {
        assertEquals(512, EditProfileLimits.avatarPixels)
        assertEquals(85, EditProfileLimits.avatarQuality)
        assertEquals(64, EditProfileLimits.displayName)
        assertEquals(256, EditProfileLimits.description)
        assertEquals(64, EditProfileLimits.detail)
    }
}
