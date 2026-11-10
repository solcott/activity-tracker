package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.app.text.AppText
import kotlin.test.Test
import kotlin.test.assertEquals

/** What an avatar shows without its picture, in both UIs (dev/shared-display-text). */
class ShownNameTest {
    @Test
    fun anAvatarShowsTheNamesFirstLetter() {
        assertEquals(AppText.written("B"), avatarInitial(" bob"))
        assertEquals(AppText.written(""), avatarInitial(""))
    }
}
