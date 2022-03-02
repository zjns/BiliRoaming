package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage
import me.iacn.biliroaming.BuildConfig
import me.iacn.biliroaming.utils.findClass
import me.iacn.biliroaming.utils.replaceMethod

class AppUpgradeHook(private val classLoader: ClassLoader) : BaseHook(classLoader) {
    private val upgradeApi = "https://www.kofua.top/bapp/version/upgrade/m%s"

    override fun startHook() {
        val updaterOptionsClass = BiliBiliPackage.instance.updaterOptionsClass
        val upgradeApiMethod = BiliBiliPackage.instance.upgradeApiMethod
        updaterOptionsClass?.findClass(classLoader)?.replaceMethod(upgradeApiMethod) {
            upgradeApi.format(BuildConfig.VERSION_CODE)
        }
    }
}
