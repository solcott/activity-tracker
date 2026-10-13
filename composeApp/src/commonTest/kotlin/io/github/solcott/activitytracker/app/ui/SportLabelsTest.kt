package io.github.solcott.activitytracker.app.ui

import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.sport.SportCatalog
import io.github.solcott.activitytracker.data.sport.SportCategory
import kotlin.test.Test
import kotlin.test.assertTrue

class SportLabelsTest {
    @Test
    fun everyCatalogTypeHasALabel() {
        val keys =
            SportCatalog.sports.flatMap { info ->
                listOf("sport_${info.sport}") + info.subSports.map { "sub_sport_${info.sport}_${it.subSport}" }
            }
        val missing = keys.filterNot { it in Res.allStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    @Test
    fun everyCategoryHasALabel() {
        val missing = SportCategory.entries.map { "sport_category_${it.name}" }.filterNot { it in Res.allStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }
}
