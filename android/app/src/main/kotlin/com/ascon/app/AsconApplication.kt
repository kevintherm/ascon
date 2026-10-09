package com.ascon.app

import android.app.Application
import com.ascon.engine.adblock.Adblock
import com.ascon.engine.adblock.AdblockOwner

class AsconApplication :
    Application(),
    AdblockOwner {
    val container: AppContainer by lazy { AppContainer(this) }

    override val adblock: Adblock get() = container.adblock
}
