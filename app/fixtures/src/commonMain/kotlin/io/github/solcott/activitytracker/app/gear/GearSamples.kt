package io.github.solcott.activitytracker.app.gear

import androidx.compose.foundation.text.input.TextFieldState
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.contentSamples
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

object GearListSamples {
    private val lists =
        GearLists(
            active =
                persistentListOf(
                    GearGroup(
                        GearType.BIKE,
                        persistentListOf(GearRow("3kgear1", "Gravel bike", GearType.BIKE, null, Visibility.PUBLIC, null)),
                    ),
                    GearGroup(
                        GearType.SHOES,
                        persistentListOf(GearRow("3kgear2", "Trail shoes", GearType.SHOES, null, Visibility.SELF, null)),
                    ),
                ),
            retired = persistentListOf(GearRow("3kgear3", "Old road bike", GearType.BIKE, null, Visibility.PUBLIC, null)),
        )

    val all: Map<String, GearListState>
        get() =
            contentSamples(GearLists(), lists) { GearListState(it, eventSink = {}) } +
                (SampleKind.EMPTY to GearListState(ContentStates.loaded(GearLists()), eventSink = {}))
}

object EditGearSamples {
    private fun state(content: ContentState<Unit>, name: String = "Gravel bike", isNew: Boolean = false) =
        EditGearState(
            content = content,
            isNew = isNew,
            name = TextFieldState(name),
            brand = TextFieldState("Rivendell"),
            model = TextFieldState("Appaloosa"),
            weight = TextFieldState("11.2"),
            notes = TextFieldState(),
            type = GearType.BIKE,
            types = GearType.all.toImmutableList(),
            kind = null,
            kinds = GearType.kinds(GearType.BIKE).toImmutableList(),
            sports = persistentListOf(Sport.RIDE),
            visibility = Visibility.PUBLIC,
            acquiredOn = null,
            retiredOn = null,
            photo = null,
            nameMissing = name.isBlank(),
            saving = false,
            confirmingDelete = false,
            approvesFollowers = false,
            error = null,
            eventSink = {},
        )

    val all: Map<String, EditGearState>
        get() =
            mapOf(
                SampleKind.LOADING to state(ContentStates.loading(Unit), name = ""),
                SampleKind.FAILED to state(ContentStates.failed(Unit), name = ""),
                SampleKind.LOADED to state(ContentStates.loaded(Unit)),
                SampleKind.INVALID to state(ContentStates.loaded(Unit), name = ""),
                "new" to state(ContentStates.loaded(Unit), name = "", isNew = true).copy(nameMissing = false),
            )
}
