package com.ulgencs3.turkanime

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class TurkAnimePlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(TurkAnimeProvider())
    }
}
