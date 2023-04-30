package me.iacn.biliroaming.hook

import android.view.View
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.from
import me.iacn.biliroaming.hookInfo
import me.iacn.biliroaming.orNull
import me.iacn.biliroaming.utils.*

class TrialVipQualityHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    private val biliAccountInfoClass by Weak { hookInfo.biliAccountInfo.class_ from mClassLoader }
    private val isEffectiveVip: Boolean
        get() = biliAccountInfoClass?.callStaticMethod(hookInfo.biliAccountInfo.get.orNull)
            ?.callMethodAs<Boolean>(hookInfo.biliAccountInfo.isEffectiveVip.orNull) ?: false

    private val vipFreeText by lazy {
        currentContext.getString(getResId("try_listening_tips", "string")) // 限免中
    }
    private val trialingText by lazy {
        currentContext.getString(getResId("player_try_watching", "string")) // 试看中
    }
    private val toTrialText by lazy {
        currentContext.getString(getResId("player_try_watch_enable", "string")) // 可试看
    }
    private val Number.dp2px: Int
        get() = (this.toFloat() * (currentContext.resources.displayMetrics.density + 0.5F)).toInt()

    override fun startHook() {
        if (!sPrefs.getBoolean("hidden", false)
            || !sPrefs.getBoolean("trial_vip_quality", false)
        ) return
        instance.playURLMossClass?.hookAfterMethod(
            XC_MethodHook.PRIORITY_LOWEST,
            "playView", instance.playViewReqClass
        ) { param ->
            param.result ?: return@hookAfterMethod
            if (isEffectiveVip || param.args[0].callMethodAs<Int>("getDownload") >= 1)
                return@hookAfterMethod
            makeVipFree(param.result.callMethod("getVideoInfo"))
        }

        instance.playerMossClass?.hookAfterMethod(
            XC_MethodHook.PRIORITY_LOWEST,
            "playViewUnite", instance.playViewUniteReqClass
        ) { param ->
            param.result ?: return@hookAfterMethod
            if (isEffectiveVip || param.args[0].callMethod("getVod")
                    ?.callMethodAs<Int>("getDownload").let { it != null && it >= 1 }
            ) return@hookAfterMethod
            makeVipFree(param.result.callMethod("getVodInfo"))
        }

        hookInfo.qualityViewHolderList.forEach { info ->
            info.class_.from(mClassLoader)?.declaredMethods
                ?.find { it.name == info.bindOnline.orNull }
                ?.hookAfterMethod { param ->
                    val selected = param.args[1] as Boolean
                    val strokeBadge = param.args[3] as TextView
                    val solidBadge = param.args[4] as TextView
                    if (!isEffectiveVip && solidBadge.text.toString() == vipFreeText) {
                        solidBadge.visibility = View.GONE
                        val strokeText = if (selected) trialingText else toTrialText
                        strokeBadge.text = strokeText
                        strokeBadge.visibility = View.VISIBLE
                        if (strokeText.length > 2) {
                            strokeBadge.setPadding(4.dp2px, 1.dp2px, 4.dp2px, 2.dp2px)
                        } else {
                            strokeBadge.setPadding(8.5.dp2px, 1.dp2px, 8.5.dp2px, 2.dp2px)
                        }
                    }
                }
        }

        abPrefs.javaClass.superclass.hookBeforeMethod(
            "getString", String::class.java, String::class.java
        ) { param ->
            val key = param.args[0]
            if (!isEffectiveVip && key == "ff_unite_player")
                param.result = null
        }
    }

    private fun makeVipFree(videoInfo: Any?) {
        videoInfo ?: return
        videoInfo.callMethodAs<List<Any>>("getStreamListList")
            .filter { it.callMethodAs("hasDashVideo") || it.callMethodAs("hasSegmentVideo") }
            .forEach {
                it.callMethod("getStreamInfo")?.run {
                    if (callMethodAs("getNeedVip")) {
                        callMethod("setNeedVip", false)
                        callMethod("setVipFree", true)
                    }
                }
            }
    }
}
