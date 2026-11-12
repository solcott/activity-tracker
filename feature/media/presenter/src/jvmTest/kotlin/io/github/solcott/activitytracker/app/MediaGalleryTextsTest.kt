package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.app.media.MediaGalleryState
import io.github.solcott.activitytracker.app.media.MediaGalleryTexts
import io.github.solcott.activitytracker.app.media.MediaItem
import io.github.solcott.activitytracker.app.moderation.Revealed
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.moderation.BlurReason
import io.github.solcott.uistate.ContentState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** The gallery's words, which both UIs ask (dev/shared-display-text). */
class MediaGalleryTextsTest {
    private val photo = MediaItem.Photo("https://example.test/a.jpg", 1.5f, alt = null)

    @Test
    fun theCounterShowsOverMoreThanOnePhoto() {
        val two = MediaGalleryState(ContentState(persistentListOf(photo, photo)), page = 1)
        assertEquals(AppText.named("media_counter", AppText.written("2"), AppText.written("2")), MediaGalleryTexts.counter(two))
        assertNull(MediaGalleryTexts.counter(MediaGalleryState(ContentState(persistentListOf(photo)))))
    }

    @Test
    fun aLabelledPhotoIsCoveredUntilRevealed() {
        val covered = MediaGalleryState(ContentState(persistentListOf(photo)), blur = BlurReason.entries.first())
        assertNotNull(MediaGalleryTexts.cover(covered))
        assertNull(MediaGalleryTexts.cover(covered.copy(revealed = Revealed(persistentSetOf("at://a/b/c")))))
        assertEquals(AppText.named("details_photo"), MediaGalleryTexts.description(photo))
    }
}
