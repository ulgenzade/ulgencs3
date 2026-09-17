// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.ulgencs3.cizgimax

class CizgiPass : CizgiDuo() {
    override var name    = "CizgiPass"
    override var mainUrl = "https://cizgipass5.online"

    private var isInitialized = false
    private suspend fun ensureInit() {
        if (isInitialized) return
        isInitialized = true
        try {
            val config = app.get(
                "https://raw.githubusercontent.com/ulgenzade/ulgencs3/master/domains.json",
                timeout = 5
            ).text
            AppUtils.parseJson<Map<String, String>>(config)["cizgimax"]
                ?.takeIf { it.isNotBlank() }?.let { mainUrl = it }
        } catch (_: Exception) { }
    }

}