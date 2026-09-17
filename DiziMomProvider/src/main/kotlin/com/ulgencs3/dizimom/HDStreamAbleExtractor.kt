// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.ulgencs3.dizimom

class HDStreamAble : PeaceMakerst() {
    override var name    = "HDStreamAble"
    override var mainUrl = "https://hdstreamable.com"

    private var isInitialized = false
    private suspend fun ensureInit() {
        if (isInitialized) return
        isInitialized = true
        try {
            val config = app.get(
                "https://raw.githubusercontent.com/ulgenzade/ulgencs3/master/domains.json",
                timeout = 5
            ).text
            AppUtils.parseJson<Map<String, String>>(config)["dizimom"]
                ?.takeIf { it.isNotBlank() }?.let { mainUrl = it }
        } catch (_: Exception) { }
    }

}