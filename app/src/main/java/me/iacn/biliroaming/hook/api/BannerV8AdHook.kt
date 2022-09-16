package me.iacn.biliroaming.hook.api

import me.iacn.biliroaming.utils.iterator
import me.iacn.biliroaming.utils.sPrefs
import me.iacn.biliroaming.utils.toJSONObject

object BannerV8AdHook : ApiHook {
    private const val feedApi = "https://app.bilibili.com/x/v2/feed/index"

    override val enabled: Boolean
        get() = sPrefs.getBoolean("hidden", false)
                && sPrefs.getBoolean("purify_banner_ads", false)

    override fun canHandler(api: String) = api.startsWith(feedApi)

    override fun hook(response: String): String {
        val json = response.toJSONObject()
        val items = json.optJSONObject("data")
            ?.optJSONArray("items")
            ?: return response
        var changed = false
        for (item in items) {
            if (item.optString("card_type") != "banner_v8")
                continue
            val banners = item.optJSONArray("banner_item")
                ?: continue
            val toRemoveIdx = mutableListOf<Int>()
            var index = 0
            for (banner in banners) {
                if (banner.optString("type") == "ad")
                    toRemoveIdx.add(index)
                index++
            }
            if (toRemoveIdx.isNotEmpty())
                changed = true
            toRemoveIdx.reversed().forEach {
                banners.remove(it)
            }
        }
        return if (changed) json.toString() else response
    }
}
