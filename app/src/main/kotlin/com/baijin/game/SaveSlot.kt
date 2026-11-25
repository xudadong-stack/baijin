package com.baijin.game

import android.util.Log
import androidx.core.util.AtomicFile
import com.baijin.game.core.ActionRequirement
import com.baijin.game.core.ActionResult
import com.baijin.game.core.Exploration
import com.baijin.game.core.GameEngine
import com.baijin.game.core.GameState
import com.baijin.game.core.SavedSparring
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files

internal class SaveSlot(file: File) {
    sealed interface ReadResult {
        data object Empty : ReadResult
        data class Available(
            val state: GameState,
            val exploration: Exploration,
            val sparring: SavedSparring? = null,
        ) : ReadResult
        data object Unreadable : ReadResult
        data object Corrupt : ReadResult
        data class UnsupportedVersion(val version: Int) : ReadResult
        data class IncompatibleContent(val requirement: ActionRequirement) : ReadResult
    }

    private val atomicFile = AtomicFile(file)
    private val savePath = file.toPath()
    private val stagingPath = File(file.path + ".new").toPath()
    private val backupPath = File(file.path + ".bak").toPath()

    fun read(engine: GameEngine): ReadResult {
        val bytes = try {
            atomicFile.readFully()
        } catch (error: IOException) {
            if (error is FileNotFoundException && Files.notExists(savePath) && Files.notExists(backupPath)) {
                return ReadResult.Empty
            }
            Log.w(TAG, "Could not read the save slot", error)
            return ReadResult.Unreadable
        }

        val result = try {
            SaveSnapshot.restore(bytes.toString(Charsets.UTF_8), engine)
        } catch (error: UnsupportedSaveVersion) {
            return ReadResult.UnsupportedVersion(error.version)
        } catch (_: SerializationException) {
            return ReadResult.Corrupt
        }

        return when (result) {
            is ActionResult.Display -> ReadResult.Available(result.state, result.exploration, result.sparring)
            is ActionResult.Blocked -> ReadResult.IncompatibleContent(result.requirement)
            is ActionResult.Changed -> error("InspectLocation changed the saved state")
        }
    }

    fun write(state: GameState, sparring: SavedSparring?) {
        val bytes = SaveSnapshot.encode(state, sparring).toByteArray(Charsets.UTF_8)
        val output = atomicFile.startWrite()
        try {
            output.write(bytes)
            // Surface a sync failure before AtomicFile can replace the previous save.
            output.fd.sync()
        } catch (error: IOException) {
            atomicFile.failWrite(output)
            throw error
        }

        // finishWrite logs a failed rename, so verify the commit before reporting success.
        atomicFile.finishWrite(output)
        if (!Files.notExists(stagingPath) || !atomicFile.readFully().contentEquals(bytes)) {
            throw IOException("The save slot did not commit the pending snapshot")
        }
    }

    private companion object {
        const val TAG = "SaveSlot"
    }
}
