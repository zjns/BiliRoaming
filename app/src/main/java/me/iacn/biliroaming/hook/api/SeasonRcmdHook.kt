package me.iacn.biliroaming.hook.api

import me.iacn.biliroaming.utils.iterator
import me.iacn.biliroaming.utils.sPrefs
import org.json.JSONObject

object SeasonRcmdHook : ApiHook {
    private const val rcmdApi = "https://api.bilibili.com/pgc/season/app/related/recommend"

    override val enabled by lazy {
        sPrefs.getBoolean("hidden", false)
                && sPrefs.getBoolean("remove_video_relate_promote", false)
    }

    override fun canHandler(api: String) = api.startsWith(rcmdApi)

    override fun hook(response: String): String {
        val json = JSONObject(response)
        val cards = json.optJSONObject("result")
            ?.optJSONArray("cards") ?: return response
        var changed = false
        val toRemoveIdxList = mutableListOf<Int>()
        var index = 0
        for (card in cards) {
            if (card.optInt("type") == 2)
                toRemoveIdxList.add(index)
            index++
        }
        if (toRemoveIdxList.isNotEmpty())
            changed = true
        toRemoveIdxList.reversed().forEach {
            cards.remove(it)
        }
        return if (changed) json.toString() else response
    }
}
