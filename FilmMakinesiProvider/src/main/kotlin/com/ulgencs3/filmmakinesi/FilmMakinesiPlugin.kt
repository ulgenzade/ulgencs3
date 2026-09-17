package com.ulgencs3.filmmakinesi

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class FilmMakinesiPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FilmMakinesi())
    }
}
