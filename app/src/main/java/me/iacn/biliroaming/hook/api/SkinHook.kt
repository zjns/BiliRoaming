package me.iacn.biliroaming.hook.api

import me.iacn.biliroaming.utils.runCatchingOrNull
import me.iacn.biliroaming.utils.sPrefs
import me.iacn.biliroaming.utils.toJSONObject
import org.json.JSONObject

object SkinHook : ApiHook {
    private const val skinApi = "https://app.bilibili.com/x/resource/show/skin"

    override val enabled by lazy {
        sPrefs.getBoolean("hidden", false)
                && sPrefs.getBoolean("skin", false)
                && !sPrefs.getString("skin_json", null).isNullOrEmpty()
    }

    override fun canHandler(api: String) = api.startsWith(skinApi)

    override fun hook(response: String): String {
        val skinJson = sPrefs.getString("skin_json", null)
        val skin = skinJson.runCatchingOrNull { toJSONObject() }
            ?.apply {
                if (optString("package_md5").isNotEmpty())
                    put("package_md5", JSONObject.NULL)
            } ?: return response
        return response.toJSONObject()
            .apply {
                optJSONObject("data")
                    ?.put("user_equip", skin)
            }.toString()
    }
}
