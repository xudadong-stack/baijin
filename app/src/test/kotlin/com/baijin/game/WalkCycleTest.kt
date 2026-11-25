package com.baijin.game

import org.junit.Assert.assertEquals
import org.junit.Test

class WalkCycleTest {
    @Test fun successfulStepsAlternateFeetAndReturnToRest() {
        val cycle = WalkCycle()
        repeat(4) { step ->
            cycle.begin()
            cycle.advance(0f)
            assertEquals(0, cycle.frame)
            cycle.advance(.5f)
            assertEquals(if (step % 2 == 0) 1 else 2, cycle.frame)
            cycle.advance(1f)
            assertEquals(0, cycle.frame)
        }
    }

    @Test fun cancelledOrSnappedMovementReturnsToRest() {
        val cycle = WalkCycle()
        cycle.begin()
        cycle.advance(.5f)
        cycle.rest()
        assertEquals(0, cycle.frame)
    }
}
