package io.github.solcott.activitytracker.app.profile

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.data.profile.DetailGroup
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.Gender

/**
 * What edit profile says beyond its fixed labels, chosen once for the shared and the native forms (dev/shared-display-text). Numbers are
 * formatted here (`formatWhole`), so ui/number-format holds wherever the text is shown.
 */
object EditProfileTexts {
    /** "5/64": how much of [max] the text holds, under a field with a limit. */
    fun counter(text: String, max: Int): AppText =
        AppText.named("edit_profile_length", AppText.written(formatWhole(text.length)), AppText.written(formatWhole(max)))

    /** The weight's label in [units]: "Weight (kg)" or "Weight (lb)". */
    fun weightLabel(units: UnitSystem): AppText =
        AppText.named(
            when (units) {
                UnitSystem.Metric -> "edit_profile_weight_kg"
                UnitSystem.Imperial -> "edit_profile_weight_lb"
            },
        )

    /** The height's label in [units]: "Height (cm)", or "Height (ft)" for the feet of feet and inches. */
    fun heightLabel(units: UnitSystem): AppText =
        AppText.named(
            when (units) {
                UnitSystem.Metric -> "edit_profile_height_cm"
                UnitSystem.Imperial -> "edit_profile_height_ft"
            },
        )

    /** The inches' label beside the feet, as the shared form lays them out side by side: "(in)". */
    val inchesBeside: AppText
        get() = AppText.named("edit_profile_height_in")

    /** The inches' label as a row of its own under the feet, as the native form lays them out: "Height (in)". */
    val inchesRow: AppText
        get() = AppText.named("edit_profile_height_in_row")

    /** That [group] was public until now and anyone may already have a copy, named so it stands alone. */
    fun nowHidden(group: DetailGroup): AppText = AppText.named("edit_profile_now_hidden", LabelText.detailGroup(group))
}

/** Edit profile's limits, for each UI's fields and for the avatar's compression. */
object EditProfileLimits {
    val displayName: Int
        get() = EditProfileState.DISPLAY_NAME_MAX_LENGTH

    val description: Int
        get() = EditProfileState.DESCRIPTION_MAX_LENGTH

    /** City, state or province, and a self-described gender. */
    val detail: Int
        get() = EditProfileState.DETAIL_MAX_LENGTH

    /** Avatars are shown small, so 512 px JPEGs stay well under the lexicon's 1 MB limit. */
    val avatarPixels: Int
        get() = AVATAR_PIXELS

    val avatarQuality: Int
        get() = AVATAR_QUALITY
}

private const val AVATAR_PIXELS = 512
private const val AVATAR_QUALITY = 85

/**
 * The gender menu's choices: Not set, each known `Gender`, and Self-describe, which shows a field for the athlete's own words. Each choice
 * is a token: [NOT_SET] is the empty value it sends, a known gender is its value, and [SELF_DESCRIBE] is the menu's own, never sent.
 * Whether the athlete just chose Self-describe (before typing anything) is each UI's view state, passed in as `chose`.
 */
object GenderChoices {
    const val NOT_SET = ""
    const val SELF_DESCRIBE = "selfDescribe"

    /** The choices in the menu's order. */
    val all: List<String>
        get() = listOf(NOT_SET) + Gender.all + SELF_DESCRIBE

    fun label(choice: String): AppText =
        when (choice) {
            NOT_SET -> AppText.named("edit_profile_not_set")
            SELF_DESCRIBE -> AppText.named("gender_self_describe")
            else -> LabelText.gender(choice)
        }

    /** Whether [gender] is the athlete's own words, or they [chose] Self-describe and haven't picked a known one since. */
    fun selfDescribes(gender: String, chose: Boolean): Boolean = chose || (gender.isNotEmpty() && gender !in Gender.all)

    /** The choice the menu shows as chosen for [gender]. */
    fun chosen(gender: String, chose: Boolean): String = if (selfDescribes(gender, chose)) SELF_DESCRIBE else gender

    /** What choosing [choice] sends: a known value or empty, or for Self-describe the words already there (none from a known value). */
    fun valueFor(choice: String, gender: String): String =
        if (choice == SELF_DESCRIBE) gender.takeUnless { it in Gender.all }.orEmpty() else choice
}

/**
 * A country as the country choice lists it: its ISO 3166-1 alpha-2 [code], its [name] in the device's language, and [key], that name folded
 * for search.
 */
@Immutable data class CountryName(val code: String, val name: String, val key: String)

/**
 * The countries edit profile offers and how its country choice searches them, for both UIs. Each UI names the countries with its platform
 * and folds case and accents with its platform (so "cote" finds "Côte d'Ivoire"), for [CountryName.key] and for what was typed.
 */
object CountrySearch {
    /** Every choice, in [sorted] order once named. */
    fun sorted(names: List<CountryName>): List<CountryName> = names.sortedBy { it.name }

    /**
     * What [key] (typed text, trimmed and folded) lists from [sorted]: all of them for nothing typed, else the country whose code was typed
     * first, so "us" puts the United States above the names that merely contain it (Australia, Belarus…), then the names containing it.
     */
    fun matches(sorted: List<CountryName>, key: String): List<CountryName> {
        if (key.isEmpty()) return sorted
        val (byCode, byName) = sorted.partition { it.code.equals(key, ignoreCase = true) }
        return byCode + byName.filter { it.key.contains(key) }
    }

    /** ISO 3166-1 alpha-2 codes, the values profile locations store. */
    val codes: List<String>
        get() = COUNTRY_CODES
}
