package io.github.solcott.activitytracker.app

import kotlin.test.Test
import kotlin.test.assertEquals

class ReloadTest {
    @Test
    fun retryBumpsTheKey() {
        val reload = Reload()
        assertEquals(0, reload.key)
        reload.retry()
        assertEquals(1, reload.key)
        reload.retry()
        assertEquals(2, reload.key)
    }
}
