package com.baijin.game

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.baijin.game.core.ActionRequirement
import com.baijin.game.core.ActionResult
import com.baijin.game.core.GameAction
import com.baijin.game.core.GameCore
import com.baijin.game.core.GameEngine
import com.baijin.game.core.GameState
import com.baijin.game.core.content.ContentIssue
import com.baijin.game.core.content.ContentLoadResult
import com.baijin.game.core.content.ContentLoader
import com.baijin.game.core.content.GameContent
import java.io.IOException

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val checkButton = findViewById<Button>(R.id.check_startup)
        val status = findViewById<TextView>(R.id.startup_status)
        when (val result = loadSampleContent()) {
            is ContentLoadResult.Loaded -> bindStartupCheck(checkButton, status, result.content)
            is ContentLoadResult.Invalid -> {
                checkButton.isEnabled = false
                status.text = result.issues.joinToString("\n") {
                    getString(R.string.content_issue, it.path, it.message)
                }
            }
        }
    }

    private fun loadSampleContent(): ContentLoadResult = try {
        assets.open(SAMPLE_ASSET).bufferedReader(Charsets.UTF_8).use { reader ->
            ContentLoader.load(reader.readText())
        }
    } catch (_: IOException) {
        ContentLoadResult.Invalid(listOf(ContentIssue(SAMPLE_ASSET, getString(R.string.content_unreadable))))
    }

    private fun bindStartupCheck(button: Button, status: TextView, content: GameContent) {
        val engine = GameEngine(content)
        var state: GameState? = null
        button.setOnClickListener {
            val action = if (state == null) GameAction.Start else GameAction.InspectLocation
            status.text = when (val result = engine.dispatch(state, action)) {
                is ActionResult.Changed -> {
                    state = result.state
                    startupStatus(result.state.contentVersion, result.locationName)
                }
                is ActionResult.Display -> startupStatus(content.contentVersion, result.locationName)
                is ActionResult.Blocked -> missingRequirement(result.requirement)
            }
        }
    }

    private fun startupStatus(version: Int, locationName: String): String =
        getString(R.string.startup_check_result, GameCore.moduleName) + "\n" +
            getString(R.string.startup_content_result, version, locationName)

    private fun missingRequirement(requirement: ActionRequirement): String = when (requirement) {
        ActionRequirement.StartRequired -> getString(R.string.start_required)
        is ActionRequirement.ContentVersionMismatch ->
            getString(R.string.content_version_mismatch, requirement.actual, requirement.expected)
        is ActionRequirement.UnknownLocation -> getString(R.string.location_missing, requirement.locationId)
    }

    private companion object {
        const val SAMPLE_ASSET = "content/sample.json"
    }
}
