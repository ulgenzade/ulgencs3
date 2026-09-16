package com.ulgencs3.animecix

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class AnimeCixPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AnimeCixProvider())
        registerExtractorAPI(TauVideo())
    }
}
