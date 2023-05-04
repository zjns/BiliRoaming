package me.iacn.biliroaming.hook.api

import me.iacn.biliroaming.utils.sPrefs
import org.json.JSONArray
import org.json.JSONObject

object CardsHook : ApiHook {
    private val cardsApis = arrayOf(
        "https://api.bilibili.com/pgc/season/player/cards",
        "https://api.bilibili.com/pgc/season/player/ogv/cards"
    )

    override val enabled by lazy {
        sPrefs.getBoolean("hidden", false)
                && sPrefs.getBoolean("block_up_rcmd_ads", false)
    }

    override fun canHandler(api: String) = cardsApis.any { api.startsWith(it) }
    override fun decodeResponse() = false

    override fun hook(response: String): String {
        return JSONObject().apply {
            put("code", 0)
            put("data", JSONArray())
        }.toString()
    }
}
