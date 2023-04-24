package me.iacn.biliroaming.hook

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import me.iacn.biliroaming.from
import me.iacn.biliroaming.hookInfo
import me.iacn.biliroaming.orNull
import me.iacn.biliroaming.utils.*

class ScreenOrientationHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val oldExpandId by lazy { getId("bbplayer_halfscreen_expand") }
    private val newExpandId by lazy { getId("gemini_halfscreen_expand") }

    private var shouldIgnoreBack = false
    private var shouldIgnoreClick = false

    override fun startHook() {
        if (!sPrefs.getBoolean("unlock_screen_orientation", false))
            return
        "com.bilibili.multitypeplayerV2.MultiTypeVideoContentActivity".from(mClassLoader)
            ?.hookAfterMethod("onCreate", Bundle::class.java) { param ->
                val activity = param.thisObject as Activity
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        "com.bilibili.video.videodetail.VideoDetailsActivity".from(mClassLoader)
            ?.hookAfterMethod("onCreate", Bundle::class.java) { param ->
                val activity = param.thisObject as Activity
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        hookInfo.orientationProcessor.run {
            class_.from(mClassLoader)?.let {
                it.replaceMethod(startGravitySensor.orNull) { null }
                it.replaceMethod(correctOrientation.orNull, it) { null }
                it.hookAfterMethod(
                    switchOrientation.orNull, Int::class.javaPrimitiveType
                ) { param ->
                    val activity = param.thisObject.getObjectFieldAs<Activity>(activity.orNull)
                    if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }
        }
        "com.bilibili.app.gemini.player.widget.story.GeminiPlayerFullscreenWidget"
            .from(mClassLoader)?.hookBeforeMethod("onClick", View::class.java) {
                shouldIgnoreClick = Thread.currentThread().stackTrace.none {
                    it.methodName == "onConfigurationChanged"
                }
            }
        "com.bilibili.bangumi.ui.page.detail.playerV2.widget.halfscreen.PgcPlayerFullscreenWidget"
            .from(mClassLoader)?.hookBeforeMethod("onClick", View::class.java) {
                shouldIgnoreClick = Thread.currentThread().stackTrace.none {
                    it.methodName == "onConfigurationChanged"
                }
            }
        "com.bilibili.bangumi.ui.page.detail.playerV2.widget.landscape.PgcPlayerBackWidget"
            .from(mClassLoader)?.hookBeforeMethod("onClick", View::class.java) {
                shouldIgnoreBack = Thread.currentThread().stackTrace.none {
                    it.methodName == "onConfigurationChanged"
                }
            }
        "com.bilibili.bangumi.ui.page.detail.BangumiDetailActivityV3".from(mClassLoader)?.run {
            hookBeforeMethod("onBackPressed") {
                shouldIgnoreBack = Thread.currentThread().stackTrace.none {
                    it.methodName == "onConfigurationChanged"
                }
            }
            hookAfterMethod("onCreate", Bundle::class.java) { param ->
                shouldIgnoreBack = false
                shouldIgnoreClick = false
                val activity = param.thisObject as Activity
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            hookBeforeMethod("onConfigurationChanged", Configuration::class.java) { param ->
                val activity = param.thisObject as Activity
                val newConfig = param.args[0] as Configuration
                if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    if (!shouldIgnoreClick) {
                        (activity.findViewById(newExpandId)
                            ?: activity.findViewById<View>(oldExpandId))
                            ?.callOnClick()
                    } else {
                        shouldIgnoreClick = false
                    }
                } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    if (!shouldIgnoreBack) {
                        @Suppress("DEPRECATION")
                        activity.onBackPressed()
                    } else {
                        shouldIgnoreBack = false
                    }
                }
            }
        }
        hookInfo.screenLayoutHelper.class_.from(mClassLoader)?.declaredMethods?.find {
            it.name == hookInfo.screenLayoutHelper.onStateChange.orNull
        }?.run {
            var orientationHook: XC_MethodHook.Unhook? = null
            hookBeforeMethod {
                val shouldSkip = Thread.currentThread().stackTrace.map { it.methodName }.let {
                    ("onClick" in it || "onBackPressed" in it) && "onConfigurationChanged" !in it
                }
                if (shouldSkip) return@hookBeforeMethod
                orientationHook = Activity::class.java.replaceMethod(
                    "setRequestedOrientation",
                    Int::class.javaPrimitiveType
                ) { null }
            }
            hookAfterMethod { param ->
                orientationHook?.unhook()
                orientationHook = null
                val activity = param.thisObject.getObjectFieldAs<Activity>(
                    hookInfo.screenLayoutHelper.activity.orNull
                )
                if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
    }
}
