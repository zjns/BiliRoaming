package me.iacn.biliroaming.hook.api

import me.iacn.biliroaming.utils.iterator
import me.iacn.biliroaming.utils.sPrefs
import org.json.JSONObject

object BannerV3AdHook : ApiHook {
    private val targetApis = arrayOf(
        // 追番
        "https://api.bilibili.com/pgc/page/bangumi?",
        // 影视
        "https://api.bilibili.com/pgc/page/cinema/tab?",
        // 番剧推荐
        "https://api.bilibili.com/pgc/page/?"
    )

    override val enabled by lazy {
        sPrefs.getBoolean("hidden", false)
                && sPrefs.getBoolean("purify_banner_ads", false)
    }

    override fun canHandler(api: String) = targetApis.any { api.startsWith(it) }

    override fun hook(response: String): String {
        val json = JSONObject(response)
        val modules = json.optJSONObject("result")
            ?.optJSONArray("modules")
            ?: return response
        var changed = false
        for (module in modules) {
            if (module.optString("style") != "banner_v3")
                continue
            val items = module.optJSONArray("items")
                ?: continue
            val toRemoveIdx = mutableListOf<Int>()
            var index = 0
            for (item in items) {
                if (item.optJSONObject("source_content")
                        ?.optJSONObject("ad_content") != null
                ) toRemoveIdx.add(index)
                index++
            }
            if (toRemoveIdx.isNotEmpty())
                changed = true
            toRemoveIdx.reversed().forEach {
                items.remove(it)
            }
        }
        return if (changed) json.toString() else response
    }
}
