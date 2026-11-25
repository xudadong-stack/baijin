package com.baijin.game

import com.baijin.game.core.ActionResult
import com.baijin.game.core.GameEngine
import com.baijin.game.core.GameState
import com.baijin.game.core.SavedSparring
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

internal class UnsupportedSaveVersion(val version: Int) : Exception()

internal object SaveSnapshot {
    private const val CURRENT_VERSION = 9
    private const val LEGACY_CONTENT_VERSION = 1

    private val legacyLocations = mapOf(
        "sample_town_start" to "town_start",
        "sample_granny_gate" to "town_granny_gate",
        "sample_training_shed" to "town_training_shed",
    )

    @Serializable
    private data class Snapshot(val schemaVersion: Int, val state: GameState, val sparring: SavedSparring? = null)

    @Serializable
    private data class ExplorationSnapshot(val schemaVersion: Int, val state: GameState)

    @Serializable
    private data class SnapshotV1(
        val schemaVersion: Int,
        val locationId: String,
        val contentVersion: Int = LEGACY_CONTENT_VERSION,
    )

    @Serializable
    private data class SnapshotV2(val schemaVersion: Int, val state: LegacyLocation)

    @Serializable
    private data class LegacyLocation(val contentVersion: Int, val locationId: String)

    fun encode(state: GameState, sparring: SavedSparring? = null): String =
        Json.encodeToString(Snapshot(CURRENT_VERSION, state, sparring))

    fun restore(json: String, engine: GameEngine): ActionResult {
        val snapshot = Json.parseToJsonElement(json) as? JsonObject
            ?: throw SerializationException("Expected a save object")
        val version = (snapshot["schemaVersion"] as? JsonPrimitive)
            ?.takeUnless { it.isString }
            ?.intOrNull
            ?: throw SerializationException("Expected an integer schemaVersion")

        return when (version) {
            1 -> Json.decodeFromJsonElement(SnapshotV1.serializer(), snapshot).let { legacy ->
                restoreLegacy(engine, legacy.contentVersion, legacy.locationId)
            }
            2 -> Json.decodeFromJsonElement(SnapshotV2.serializer(), snapshot).state.let { legacy ->
                restoreLegacy(engine, legacy.contentVersion, legacy.locationId)
            }
            3, 4 -> Json.decodeFromJsonElement(ExplorationSnapshot.serializer(), snapshot).let { legacy ->
                engine.restoreVersionOne(legacy.state)
            }
            5, 6, 7 -> Json.decodeFromJsonElement(ExplorationSnapshot.serializer(), snapshot).let { legacy ->
                engine.restoreSavedGame(legacy.state)
            }
            8, CURRENT_VERSION -> Json.decodeFromJsonElement(Snapshot.serializer(), snapshot).let { current ->
                engine.restoreSavedGame(current.state, current.sparring)
            }
            else -> throw UnsupportedSaveVersion(version)
        }
    }

    private fun restoreLegacy(engine: GameEngine, contentVersion: Int, locationId: String): ActionResult =
        engine.restoreLegacyLocation(contentVersion, legacyLocations[locationId] ?: locationId)
}
