package com.baijin.game

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.baijin.game.GameSession.Screen
import com.baijin.game.SaveSlot.ReadResult

class MainActivity : Activity() {
    private val session by lazy { (application as BaijinApplication).session }
    private val renderScreen: (Screen) -> Unit = ::render
    private lateinit var root: FrameLayout
    private val startup by lazy { layoutInflater.inflate(R.layout.activity_main, root, false) }
    private val newGame by lazy {
        startup.findViewById<Button>(R.id.new_game).apply {
            styleGameButton(primary = true)
            setOnClickListener { requestNewGame() }
        }
    }
    private val continueGame by lazy {
        startup.findViewById<Button>(R.id.continue_game).apply {
            styleGameButton(primary = false)
            setOnClickListener { session.continueGame() }
        }
    }
    private val status by lazy { startup.findViewById<TextView>(R.id.startup_status) }
    private val creation by lazy { CharacterCreationScreen(this, session) }
    private val exploration = lazy { ExplorationScreen(this, session) }
    private val sparring = lazy { SparringScreen(this, session) }
    private var replaceDialog: AlertDialog? = null
    private var interceptsBack = false
    private val backCallback by lazy { OnBackInvokedCallback { session.returnToMenu() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        root = FrameLayout(this).apply { setBackgroundColor(getColor(R.color.paper)) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safeArea = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.mandatorySystemGestures(),
            )
            view.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom)
            insets
        }
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
    }

    override fun onStart() {
        super.onStart()
        session.observe(renderScreen)
    }

    override fun onStop() {
        session.removeObserver(renderScreen)
        if (exploration.isInitialized()) exploration.value.releaseMovement()
        if (sparring.isInitialized()) sparring.value.leave()
        (session.screen as? Screen.PlayingSparring)?.let(session::finishSparringPlayback)
        super.onStop()
    }

    override fun onDestroy() {
        replaceDialog?.dismiss()
        if (exploration.isInitialized()) exploration.value.dismissDialog()
        interceptBack(false)
        super.onDestroy()
    }

    // API 33+ uses the system back callback registered in interceptBack.
    @SuppressLint("GestureBackNavigation")
    @Suppress("DEPRECATION")
    @Deprecated("Back navigation fallback for Android 12 and earlier")
    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && session.returnToMenu()) return
        super.onBackPressed()
    }

    private fun requestNewGame() {
        val menu = session.screen as? Screen.Menu ?: return
        if (menu.save == ReadResult.Empty) {
            session.startNewGame()
        } else {
            replaceDialog = AlertDialog.Builder(this)
                .setTitle(R.string.new_game)
                .setMessage(R.string.replace_save_prompt)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.replace_save) { _, _ -> session.startNewGame(menu.save) }
                .show()
        }
    }

    private fun render(screen: Screen) {
        if (screen is Screen.PlayingSparring) {
            interceptBack(true)
            showView(sparring.value.view)
            sparring.value.play(screen)
            return
        }
        val active = when (screen) {
            is Screen.Active -> screen
            is Screen.Saving -> screen.previous
            is Screen.SaveFailed -> screen.previous
            else -> null
        }
        if (active !is Screen.Sparring && sparring.isInitialized()) sparring.value.leave()
        interceptBack(active != null)
        if (active != null) {
            val notice = when (screen) {
                is Screen.Saving -> if (active is Screen.Creating || active is Screen.Sparring) {
                    getString(R.string.saving_game)
                } else null
                is Screen.SaveFailed -> getString(R.string.save_failed)
                else -> null
            }
            val locked = screen !is Screen.Active
            val canRetry = screen is Screen.SaveFailed
            val target = when (active) {
                is Screen.Creating -> creation.apply { render(active, locked, notice ?: active.notice, canRetry) }.view
                is Screen.Exploring -> exploration.value.apply {
                    render(active.game, locked, notice ?: active.notice, canRetry)
                }.view
                is Screen.Sparring -> {
                    if (exploration.isInitialized()) {
                        exploration.value.releaseMovement()
                        exploration.value.dismissDialog()
                    }
                    sparring.value.apply { render(active.game, locked, notice ?: active.notice, canRetry) }.view
                }
            }
            showView(target)
            return
        }
        if (exploration.isInitialized()) {
            exploration.value.releaseMovement()
            exploration.value.dismissDialog()
        }
        val menu = screen as? Screen.Menu
        newGame.isEnabled = menu != null
        continueGame.isEnabled = menu?.save is ReadResult.Available
        status.text = when (screen) {
            Screen.Loading -> getString(R.string.loading_game)
            is Screen.ContentInvalid -> screen.issues.joinToString("\n") {
                getString(R.string.content_issue, it.path, it.message)
            }
            is Screen.Menu -> menuStatus(screen.save)
            else -> error("Active screens are drawn before the startup menu")
        }
        showView(startup)
    }

    private fun menuStatus(save: ReadResult): String = when (save) {
        ReadResult.Empty -> getString(R.string.no_save)
        is ReadResult.Available -> getString(R.string.last_location, save.exploration.locationName)
        ReadResult.Unreadable -> getString(R.string.save_unreadable)
        ReadResult.Corrupt -> getString(R.string.save_corrupt)
        is ReadResult.UnsupportedVersion -> getString(R.string.save_version_unsupported, save.version)
        is ReadResult.IncompatibleContent -> getString(R.string.save_content_invalid, requirementText(save.requirement))
    }

    private fun showView(target: View) {
        if (target.parent === root) return
        root.removeAllViews()
        root.addView(target, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun interceptBack(intercept: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || interceptsBack == intercept) return
        interceptsBack = intercept
        if (intercept) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback)
        } else {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backCallback)
        }
    }
}
