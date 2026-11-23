package com.baijin.game.core

import org.junit.Assert.assertEquals
import org.junit.Test

class GameCoreSmokeTest {
    @Test
    fun exposesModuleNameOnJvm() {
        assertEquals("game-core", GameCore.moduleName)
    }
}
