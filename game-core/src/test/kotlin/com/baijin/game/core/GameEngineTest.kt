package com.baijin.game.core

import com.baijin.game.core.content.loadBundledSample
import org.junit.Assert.assertEquals
import org.junit.Test

class GameEngineTest {
    private val content = loadBundledSample()
    private val engine = GameEngine(content)
    private val start = content.locations.single { it.id == content.startLocationId }

    @Test
    fun startReturnsTheConfiguredVersionAndLocation() {
        val expected = ActionResult.Changed(GameState(content.contentVersion, start.id), start.name)
        assertEquals(expected, engine.dispatch(null, GameAction.Start))
    }

    @Test
    fun inspectReportsTheCurrentLocationWithoutAStateChange() {
        val location = content.locations.first { it.id != start.id }
        val state = GameState(content.contentVersion, location.id)
        assertEquals(ActionResult.Display(location.name), engine.dispatch(state, GameAction.InspectLocation))
    }

    @Test
    fun inspectReportsMissingState() {
        assertEquals(ActionResult.Blocked(ActionRequirement.StartRequired), engine.dispatch(null, GameAction.InspectLocation))
    }

    @Test
    fun inspectReportsAnIncompatibleContentVersion() {
        val state = GameState(content.contentVersion + 1, start.id)
        val expected = ActionResult.Blocked(ActionRequirement.ContentVersionMismatch(content.contentVersion, state.contentVersion))
        assertEquals(expected, engine.dispatch(state, GameAction.InspectLocation))
    }

    @Test
    fun inspectReportsAMissingLocationId() {
        val state = GameState(content.contentVersion, "missing_location")
        val expected = ActionResult.Blocked(ActionRequirement.UnknownLocation(state.locationId))
        assertEquals(expected, engine.dispatch(state, GameAction.InspectLocation))
    }
}
