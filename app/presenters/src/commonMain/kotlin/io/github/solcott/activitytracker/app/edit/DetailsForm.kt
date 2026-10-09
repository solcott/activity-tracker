package io.github.solcott.activitytracker.app.edit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.app.media.MediaItem
import io.github.solcott.activitytracker.data.activity.MediaEdit
import io.github.solcott.activitytracker.data.activity.NewPhoto
import io.github.solcott.activitytracker.data.activity.cid
import io.github.solcott.activitytracker.lexicon.app.ActivityMedia
import io.github.solcott.activitytracker.lexicon.app.ActivityMediaView
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

/** The [ActivityDetailsFields] a presenter owns: [seed] it with what the activity has, then read back what the athlete changed. */
@Stable
class DetailsForm internal constructor() {
    val description = TextFieldState()
    val diveSite = TextFieldState()
    var media: PersistentList<EditMedia> by mutableStateOf(persistentListOf())
        private set

    private var seeded by mutableStateOf(Seed("", "", persistentListOf()))

    fun seed(description: String?, diveSite: String?, media: List<EditMedia> = emptyList()) {
        this.description.setTextAndPlaceCursorAtEnd(description.orEmpty())
        this.diveSite.setTextAndPlaceCursorAtEnd(diveSite.orEmpty())
        this.media = media.toPersistentList()
        seeded = Seed(description.orEmpty(), diveSite.orEmpty(), this.media)
    }

    /** The form's state; [isDive] shows the dive site. */
    fun fields(isDive: Boolean): ActivityDetailsFields =
        ActivityDetailsFields(description, diveSite.takeIf { isDive }, media, ActivityMedia.MAX, ::onEvent)

    /** The athlete changed something since [seed]. */
    val isChanged: Boolean
        get() = Seed(description.text.toString(), diveSite.text.toString(), media) != seeded

    /** The media in order, for [io.github.solcott.activitytracker.data.activity.ActivityManager.edit]. */
    fun mediaEdits(): List<MediaEdit> = media.map {
        when (it) {
            is EditMedia.Existing -> MediaEdit.Keep(it.cid)
            is EditMedia.Picked -> MediaEdit.Add(it.photo.toNewPhoto())
        }
    }

    /** The photos picked here, for a draft that has none yet. */
    fun newPhotos(): List<NewPhoto> = media.filterIsInstance<EditMedia.Picked>().map { it.photo.toNewPhoto() }

    fun onEvent(event: DetailsEvent) {
        when (event) {
            is DetailsEvent.PhotosPicked -> media = (media + event.photos.map(EditMedia::Picked)).take(ActivityMedia.MAX).toPersistentList()

            is DetailsEvent.RemoveMedia -> if (event.index in media.indices) media = media.removeAt(event.index)
        }
    }

    private data class Seed(val description: String, val diveSite: String, val media: List<EditMedia>)
}

@Composable fun rememberDetailsForm(): DetailsForm = remember { DetailsForm() }

private fun PickedPhoto.toNewPhoto() = NewPhoto(bytes.toByteArray(), mimeType, width, height)

/** [activity]'s photos as the form shows them: each from its view, named by the CID its record holds. */
fun existingMedia(activity: ActivityView): List<EditMedia> {
    val views = activity.media.orEmpty().filterIsInstance<ActivityMediaView.Image>()
    return activity.record.media.orEmpty().mapNotNull { media ->
        val cid = media.cid ?: return@mapNotNull null
        val view = views.firstOrNull { it.fullsize.endsWith("/$cid") } ?: return@mapNotNull null
        EditMedia.Existing(cid, MediaItem.Photo(view.fullsize, view.aspectRatio?.let { it.width.toFloat() / it.height }, view.alt))
    }
}
