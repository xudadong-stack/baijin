package com.baijin.game

import android.app.Application

class BaijinApplication : Application() {
    internal val session by lazy { GameSession(this) }
}
