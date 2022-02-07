package me.iacn.biliroaming.hook

import android.net.Uri
import me.iacn.biliroaming.BiliBiliPackage
import me.iacn.biliroaming.utils.*

class VideoSubtitleHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    private val host = "https://www.kofua.top/bsub/t2s"

    override fun startHook() {
        if (!sPrefs.getBoolean("auto_generate_chs_subtitle", false)) return

        BiliBiliPackage.instance.videoSubtitleClass?.hookAfterMethod("getSubtitlesList") ret@{ param ->
            val subtitles = param.result as? List<*> ?: listOf<Any>()
            val subtitleItemClass = BiliBiliPackage.instance.subtitleItemClass ?: return@ret
            val subTypeClass = BiliBiliPackage.instance.subtitleTypeClass ?: return@ret
            val lanCodes = subtitles.map { s -> s?.callMethodOrNullAs<String>("getLan") }
            if ("zh-CN" !in lanCodes && "zh-Hant" in lanCodes) {
                val zhHant = subtitles.find { s ->
                    s?.callMethod("getLan") == "zh-Hant"
                } ?: return@ret
                val subUrl = zhHant.callMethodOrNullAs<String>("getSubtitleUrl") ?: return@ret
                val zhHansUrl = Uri.parse(host).buildUpon()
                    .appendQueryParameter("sub_url", subUrl)
                    .build().toString()
                val ccType = subTypeClass.getStaticObjectFieldOrNull("CC") ?: return@ret
                var id = zhHant.callMethodOrNullAs<Long>("getId") ?: 0L
                val item = subtitleItemClass.new().apply {
                    callMethod("setLan", "zh-CN")
                    callMethod("setLanDoc", "简中（生成）")
                    callMethod("setSubtitleUrl", zhHansUrl)
                    callMethod("setType", ccType)
                    callMethod("setId", ++id)
                    callMethod("setIdStr", id.toString())
                }
                param.thisObject.callMethod("addSubtitles", subtitles.size, item)
                param.result = param.thisObject.callMethod("getSubtitlesList")
            }
        }
    }
}
