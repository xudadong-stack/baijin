package com.baijin.game.core

import com.baijin.game.core.content.GameContent
import com.baijin.game.core.content.LocationDefinition

data class GameState(val contentVersion: Int, val locationId: String)

sealed interface GameAction {
    data object Start : GameAction
    data object InspectLocation : GameAction
}

sealed interface ActionResult {
    data class Changed(val state: GameState, val locationName: String) : ActionResult
    data class Display(val locationName: String) : ActionResult
    data class Blocked(val requirement: ActionRequirement) : ActionResult
}

sealed interface ActionRequirement {
    data object StartRequired : ActionRequirement
    data class ContentVersionMismatch(val expected: Int, val actual: Int) : ActionRequirement
    data class UnknownLocation(val locationId: String) : ActionRequirement
}

class GameEngine(private val content: GameContent) {
    private val locations = content.locations.associateBy(LocationDefinition::id)

    fun dispatch(state: GameState?, action: GameAction): ActionResult = when (action) {
        GameAction.Start -> start()
        GameAction.InspectLocation -> inspectLocation(state)
    }

    private fun start(): ActionResult {
        val location = locations[content.startLocationId]
            ?: return ActionResult.Blocked(ActionRequirement.UnknownLocation(content.startLocationId))
        return ActionResult.Changed(GameState(content.contentVersion, location.id), location.name)
    }

    private fun inspectLocation(state: GameState?): ActionResult {
        if (state == null) return ActionResult.Blocked(ActionRequirement.StartRequired)
        if (state.contentVersion != content.contentVersion) {
            return ActionResult.Blocked(
                ActionRequirement.ContentVersionMismatch(content.contentVersion, state.contentVersion),
            )
        }
        val location = locations[state.locationId]
            ?: return ActionResult.Blocked(ActionRequirement.UnknownLocation(state.locationId))
        return ActionResult.Display(location.name)
    }
}
