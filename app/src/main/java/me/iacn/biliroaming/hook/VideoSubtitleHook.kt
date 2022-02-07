package me.iacn.biliroaming.hook

import android.net.Uri
import me.iacn.biliroaming.BiliBiliPackage
import me.iacn.biliroaming.utils.*

class VideoSubtitleHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    private val host = "https://www.kofua.top/bsub/%s"

    override fun startHook() {
        if (!sPrefs.getBoolean("auto_generate_subtitle", false)) return

        BiliBiliPackage.instance.videoSubtitleClass?.hookAfterMethod("getSubtitlesList") ret@{ param ->
            val subtitles = param.result as? List<*> ?: listOf<Any>()
            val subtitleItemClass = BiliBiliPackage.instance.subtitleItemClass ?: return@ret
            val subTypeClass = BiliBiliPackage.instance.subtitleTypeClass ?: return@ret
            val lanCodes = subtitles.map { it?.callMethodOrNullAs<String>("getLan") }
            val genCN = "zh-Hant" in lanCodes && "zh-CN" !in lanCodes
            val genHant = "zh-CN" in lanCodes && "zh-Hant" !in lanCodes
            val origin = if (genCN) "zh-Hant" else if (genHant) "zh-CN" else ""
            val target = if (genCN) "zh-CN" else if (genHant) "zh-Hant" else ""
            val converter = if (genCN) "t2cn" else if (genHant) "cn2t" else ""
            val targetDoc = if (genCN) "简中（生成）" else if (genHant) "繁中（生成）" else ""
            if (origin.isNotEmpty()) {
                val origSub = subtitles.find { it?.callMethod("getLan") == origin } ?: return@ret
                val origSubUrl = origSub.callMethodOrNullAs<String>("getSubtitleUrl") ?: return@ret
                var origSubId = origSub.callMethodOrNullAs<Long>("getId") ?: 0L
                val targetSubUrl = Uri.parse(host.format(converter)).buildUpon()
                    .appendQueryParameter("sub_url", origSubUrl)
                    .appendQueryParameter("sub_id", origSubId.toString())
                    .build().toString()
                val ccType = subTypeClass.getStaticObjectFieldOrNull("CC") ?: return@ret
                val item = subtitleItemClass.new().apply {
                    callMethod("setLan", target)
                    callMethod("setLanDoc", targetDoc)
                    callMethod("setSubtitleUrl", targetSubUrl)
                    callMethod("setType", ccType)
                    callMethod("setId", ++origSubId)
                    callMethod("setIdStr", origSubId.toString())
                }
                param.thisObject.callMethod("addSubtitles", subtitles.size, item)
                param.result = param.thisObject.callMethod("getSubtitlesList")
            }
        }
    }
}
