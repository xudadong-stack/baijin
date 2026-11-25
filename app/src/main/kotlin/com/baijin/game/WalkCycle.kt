package com.baijin.game

/** Each successful tile step alternates its leading foot; rest always uses frame zero. */
internal class WalkCycle {
    private var nextFoot = 1
    private var leadingFoot = 1
    var frame = 0
        private set

    fun begin() {
        leadingFoot = nextFoot
        nextFoot = 3 - nextFoot
        frame = 0
    }

    fun advance(fraction: Float) {
        frame = if (fraction >= .18f && fraction < .82f) leadingFoot else 0
    }

    fun rest() { frame = 0 }
}
