package com.ulgencs3.sinewix

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class SinewixPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Sinewix())
    }
}
