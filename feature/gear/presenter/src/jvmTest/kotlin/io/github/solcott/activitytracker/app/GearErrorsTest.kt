package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.app.gear.gearErrorOf
import io.github.solcott.activitytracker.app.gear.mountErrorOf
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.activity.PendingWritesException
import io.github.solcott.activitytracker.data.di.SpacesUnsupportedException
import io.github.solcott.activitytracker.data.gear.GearMountException
import io.github.solcott.activitytracker.data.gear.GearNeedsSignInException
import io.github.solcott.activitytracker.lexicon.app.MountProblem
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import kotlin.test.Test
import kotlin.test.assertEquals

class GearErrorsTest {
    private val scopeMissing = XrpcException(403, SpaceErrors.SCOPE_MISSING, null)

    @Test
    fun `every gear write asks to sign in again for gear, whether this client or the PDS refused it`() {
        for (kind in listOf(UiError.Kind.SaveGear, UiError.Kind.SaveGearCollection)) {
            assertEquals(UiError.Kind.GearSignInAgain, gearErrorOf(kind, GearNeedsSignInException()).kind)
            assertEquals(UiError.Kind.GearSignInAgain, gearErrorOf(kind, scopeMissing).kind)
        }
        assertEquals(UiError.Kind.GearSignInAgain, mountErrorOf(GearNeedsSignInException()).kind)
        assertEquals(UiError.Kind.GearSignInAgain, mountErrorOf(scopeMissing).kind)
    }

    @Test
    fun `other failures read as uiErrorOf does`() {
        assertEquals(UiError.Kind.SpacesUnsupported, gearErrorOf(UiError.Kind.SaveGear, SpacesUnsupportedException()).kind)
        assertEquals(UiError.Kind.WritesPending, mountErrorOf(PendingWritesException(2)).kind)
        assertEquals(UiError.Kind.SaveGearCollection, gearErrorOf(UiError.Kind.SaveGearCollection, IllegalStateException()).kind)
    }

    @Test
    fun `a refused mount names its rule`() {
        assertEquals(UiError.Kind.MountLoop, mountErrorOf(GearMountException(MountProblem.LOOP)).kind)
        assertEquals(UiError.Kind.SaveMount, mountErrorOf(GearMountException(MountProblem.FOREIGN_PARENT)).kind)
    }
}
