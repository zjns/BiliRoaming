package me.iacn.biliroaming.hook

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*

class ChannelTabUIHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("hidden", false)
            || !sPrefs.getBoolean("add_channel", false)
        ) return
        "com.bilibili.pegasus.channelv2.home.category.HomeCategoryFragment"
            .from(mClassLoader)?.hookAfterMethod(
                "onViewCreated", View::class.java, Bundle::class.java
            ) { param ->
                val root = param.args[0] as ViewGroup
                if (root.context.javaClass == instance.splashActivityClass) {
                    root.getChildAt(0).visibility = View.GONE
                    root.clipToPadding = false
                    root.setPadding(0, 0, 0, 48.dp)
                }
            }
    }
}
