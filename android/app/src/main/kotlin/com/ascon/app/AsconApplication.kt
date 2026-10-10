package com.ascon.app

import android.app.Application
import com.ascon.engine.adblock.Adblock
import com.ascon.engine.adblock.AdblockOwner
import com.ascon.engine.detection.RuleBackend
import com.ascon.engine.detection.RuleHealthOwner

class AsconApplication :
    Application(),
    AdblockOwner,
    RuleHealthOwner {
    val container: AppContainer by lazy { AppContainer(this) }

    override val adblock: Adblock get() = container.adblock

    override val ruleStore get() = container.ruleStore

    override val ruleBackend: RuleBackend? get() = container.ruleBackend
}
