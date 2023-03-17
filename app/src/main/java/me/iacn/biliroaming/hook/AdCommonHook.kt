package me.iacn.biliroaming.hook

import me.iacn.biliroaming.utils.*

class AdCommonHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        Log.d("startHook: AdCommon")
        val removeAdExtra = sPrefs.getBoolean("remove_ad_extra", false)
        if (removeAdExtra) {
            "com.bilibili.adcommon.util.j".from(mClassLoader)
                ?.replaceMethod(
                    "a"
                ) { param ->
                    Log.d("Remove AdExtra succeeded.")
                    true
                }
        }
    }
}