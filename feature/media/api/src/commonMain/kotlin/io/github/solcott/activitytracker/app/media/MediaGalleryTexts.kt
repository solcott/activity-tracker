package io.github.solcott.activitytracker.app.media

import io.github.solcott.activitytracker.app.moderation.ModerationTexts
import io.github.solcott.activitytracker.app.text.AppText

/**
 * What the media gallery says and offers (moderation/labels, moderation/reports, dev/shared-display-text): Close, the counter, the photo
 * menu's Report, a photo's spoken name, a labelled photo's cover and a taken-down one's placeholder. The Compose gallery and the native one
 * ask these.
 */
object MediaGalleryTexts {
    val close: AppText = AppText.named("media_close")
    val more: AppText = AppText.named("detail_more")
    val reportPhoto: AppText = AppText.named("report_photo")
    val zoomHint: AppText = AppText.named("media_zoom_hint")
    val previous: AppText = AppText.named("media_previous")
    val next: AppText = AppText.named("media_next")
    val takenDown: AppText = AppText.named("label_taken_down_photo")
    val show: AppText = AppText.named("label_show")

    /** "2 / 5", over more than one item; null for one. */
    fun counter(state: MediaGalleryState): AppText? =
        state.items.size
            .takeIf { it > 1 }
            ?.let { AppText.named("media_counter", AppText.written("${state.page + 1}"), AppText.written("$it")) }

    /** Whether the photo menu, with Report, is offered: someone else's photos, while reporting is. */
    fun offersReport(state: MediaGalleryState): Boolean = state.canReport && state.reporting.available

    /** A photo's spoken name: its alt text, else "Photo". */
    fun description(photo: MediaItem.Photo): AppText = photo.alt?.let(AppText::written) ?: AppText.named("details_photo")

    /** What covers each photo until the viewer reveals them: the label's name; null when nothing does. */
    fun cover(state: MediaGalleryState): AppText? = state.blur?.takeUnless { state.revealed.keys.isNotEmpty() }?.let(ModerationTexts::blur)

    /** Photos can be swiped between: none is zoomed. */
    fun swipes(state: MediaGalleryState): Boolean = !state.zoomed
}
