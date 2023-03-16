package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import java.lang.reflect.Proxy

class ProtoBufHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        val hidden = sPrefs.getBoolean("hidden", false)
        val purifyCity = sPrefs.getBoolean("purify_city", false)
        val removeRelatePromote = sPrefs.getBoolean("remove_video_relate_promote", false)
        val removeRelateOnlyAv = sPrefs.getBoolean("remove_video_relate_only_av", false)
        val removeRelateNothing = sPrefs.getBoolean("remove_video_relate_nothing", false)
        val removeCmdDms = sPrefs.getBoolean("remove_video_cmd_dms", false)
        val purifySearch = sPrefs.getBoolean("purify_search", false)
        val purifyCampus = sPrefs.getBoolean("purify_campus", false)
        val unlockPlayActions = sPrefs.getBoolean("play_arc_conf", false)
        val removeCommentCm = sPrefs.getBoolean("remove_comment_cm", false)
        val blockWordSearch = sPrefs.getBoolean("block_word_search", false)
        val blockModules = sPrefs.getBoolean("block_modules", false)
        val blockUpperRecommendAd = sPrefs.getBoolean("block_upper_recommend_ad", false)

        if (hidden && (purifyCity || purifyCampus)) {
            listOf(
                "com.bapis.bilibili.app.dynamic.v1.DynTabReply",
                "com.bapis.bilibili.app.dynamic.v2.DynTabReply"
            ).forEach { clazz ->
                clazz.hookAfterMethod(
                    mClassLoader,
                    "getDynTabList"
                ) { param ->
                    param.result = (param.result as List<*>).filterNot {
                        purifyCity && it?.callMethodAs<Long>("getCityId") != 0L
                                || purifyCampus && it?.callMethodAs<String>("getAnchor") == "campus"
                    }
                }
            }
        }

        "com.bapis.bilibili.app.view.v1.ViewMoss".hookAfterMethod(
            mClassLoader,
            "view",
            "com.bapis.bilibili.app.view.v1.ViewReq"
        ) { param ->
            param.result ?: return@hookAfterMethod
            val aid = param.result.callMethod("getArc")
                ?.callMethodAs("getAid") ?: -1L
            val like = param.result.callMethod("getReqUser")
                ?.callMethodAs("getLike") ?: -1
            AutoLikeHook.detail = aid to like
            if (hidden && removeRelatePromote && removeRelateOnlyAv && removeRelateNothing) {
                param.result.callMethod("clearRelates")
                return@hookAfterMethod
            }
            buildSet {
                param.result.callMethodAs<List<*>?>("getRelatesList")
                    ?.onEachIndexed { idx, r ->
                        if (hidden && removeRelatePromote
                            && (r?.callMethodAs<Long?>("getFromSourceType") == 2L ||
                                    r?.callMethodAs<String?>("getGoto") == "cm")
                        ) add(idx)
                        if (hidden && removeRelatePromote && removeRelateOnlyAv
                            && r?.callMethodAs<String?>("getGoto").let { it != "av" }
                        ) add(idx)
                    }
            }.reversed().forEach {
                param.result.callMethod("removeRelates", it)
            }
        }

        if (hidden && removeCmdDms) {
            "com.bapis.bilibili.app.view.v1.ViewMoss".hookAfterMethod(
                mClassLoader,
                "viewProgress",
                "com.bapis.bilibili.app.view.v1.ViewProgressReq"
            ) { param ->
                param.result?.callMethod("getVideoGuide")?.run {
                    callMethod("clearAttention")
                    callMethod("clearCommandDms")
                    callMethod("clearContractCard")
                }
            }
        }
        if (hidden && purifySearch) {
            "com.bapis.bilibili.app.interfaces.v1.SearchMoss".hookAfterMethod(
                mClassLoader,
                "defaultWords",
                "com.bapis.bilibili.app.interfaces.v1.DefaultWordsReq"
            ) { param ->
                param.result = null
            }
        }
        if (unlockPlayActions) {
            "com.bapis.bilibili.app.playurl.v1.PlayURLMoss".hookAfterMethod(
                mClassLoader,
                "playView",
                "com.bapis.bilibili.app.playurl.v1.PlayViewReq"
            ) { param ->
                param.result?.callMethod("getPlayArc")?.run {
                    listOf(
                        callMethod("getBackgroundPlayConf"),
                        callMethod("getCastConf"),
                        callMethod("getSmallWindowConf")
                    ).forEach {
                        it?.callMethod("setDisabled", false)
                        it?.callMethod("setIsSupport", true)
                        it?.callMethod("clearExtraContent")
                    }
                }
            }
        }
        if (hidden && removeCommentCm) {
            "com.bapis.bilibili.main.community.reply.v1.ReplyMoss".hookBeforeMethod(
                mClassLoader,
                "mainList",
                "com.bapis.bilibili.main.community.reply.v1.MainListReq",
                instance.mossResponseHandlerClass
            ) { param ->
                val handler = param.args[1]
                param.args[1] = Proxy.newProxyInstance(
                    handler.javaClass.classLoader,
                    arrayOf(instance.mossResponseHandlerClass)
                ) { _, m, args ->
                    if (m.name == "onNext") {
                        val reply = args[0]
                        reply?.callMethod("getCm")
                            ?.callMethod("clearSourceContent")
                        m(handler, *args)
                    } else if (args == null) {
                        m(handler)
                    } else {
                        m(handler, *args)
                    }
                }
            }
        }
        if (hidden && blockWordSearch) {
            "com.bapis.bilibili.main.community.reply.v1.Content".hookAfterMethod(
                mClassLoader,
                "internalGetUrls"
            ) { param ->
                (param.result as LinkedHashMap<*, *>?)?.let { m ->
                    val iterator = m.iterator()
                    while (iterator.hasNext()) {
                        iterator.next().value.callMethodAs<String?>("getAppUrlSchema")
                            ?.takeIf {
                                it.startsWith("bilibili://search?from=appcommentline_search")
                            }?.run {
                                iterator.remove()
                            }
                    }
                }
            }
        }
        if (hidden && blockModules) {
            "com.bapis.bilibili.app.resource.v1.ModuleMoss".hookAfterMethod(
                mClassLoader,
                "list",
                "com.bapis.bilibili.app.resource.v1.ListReq"
            ) {
                it.result.callMethod("clearPools")
            }
        }
        if (hidden && blockUpperRecommendAd) {
            "com.bapis.bilibili.ad.v1.SourceContentDto".from(mClassLoader)
                ?.replaceMethod("getAdContent") { null }
        }
    }
}
