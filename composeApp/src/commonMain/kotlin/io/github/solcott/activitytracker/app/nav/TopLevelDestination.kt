package io.github.solcott.activitytracker.app.nav

import com.slack.circuit.runtime.screen.Screen
import io.github.solcott.activitytracker.app.edit.EditActivityScreen
import io.github.solcott.activitytracker.app.feed.FeedScreen
import io.github.solcott.activitytracker.app.media.MediaGalleryScreen
import io.github.solcott.activitytracker.app.profile.EditProfileScreen
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.record.ImportActivityScreen
import io.github.solcott.activitytracker.app.record.ManualActivityScreen
import io.github.solcott.activitytracker.app.record.RecordScreen
import io.github.solcott.activitytracker.app.search.AthleteSearchScreen
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.nav_feed
import io.github.solcott.activitytracker.composeApp.resources.nav_search
import io.github.solcott.activitytracker.composeApp.resources.nav_you
import org.jetbrains.compose.resources.StringResource

/** The signed-in app's tabs (sidebar entries on wide desktop and web). Each keeps its own back stack. */
enum class TopLevelDestination(val label: StringResource, val icon: AppIcon) {
    Feed(Res.string.nav_feed, AppIcon.Feed),
    Search(Res.string.nav_search, AppIcon.Search),
    You(Res.string.nav_you, AppIcon.You);

    /** The root screen of this tab's back stack. */
    fun screen(viewerDid: String): Screen =
        when (this) {
            Feed -> FeedScreen
            Search -> AthleteSearchScreen
            You -> ProfileScreen(viewerDid)
        }

    companion object {
        /** The tab whose back stack has [root] at the bottom, or null when [root] isn't a tab's root. */
        fun of(root: Screen, viewerDid: String): TopLevelDestination? = entries.firstOrNull { it.screen(viewerDid) == root }

        /**
         * Whether the tab bar, rail or sidebar shows over [top], the screen on top of the stack. Not over a flow with its own finish
         * (recording, adding or importing an activity, editing the profile), which takes the whole window.
         */
        fun showsNavigation(top: Screen): Boolean =
            top !in FullScreenFlows && top !is ImportActivityScreen && top !is EditActivityScreen && top !is MediaGalleryScreen

        private val FullScreenFlows = setOf(RecordScreen, ManualActivityScreen, EditProfileScreen)
    }
}
