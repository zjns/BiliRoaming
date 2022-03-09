package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage
import me.iacn.biliroaming.BuildConfig
import me.iacn.biliroaming.utils.*
import java.io.File

class AppUpgradeHook(private val classLoader: ClassLoader) : BaseHook(classLoader) {
    private val upgradeApi = "https://www.kofua.top/bapp/version/upgrade/m%s"

    override fun startHook() {
        BiliBiliPackage.instance.updaterOptionsClass?.findClass(classLoader)?.run {
            BiliBiliPackage.instance.upgradeApiMethod?.let {
                replaceMethod(it) { upgradeApi.format(BuildConfig.VERSION_CODE) }
            }
        }
        BiliBiliPackage.instance.appendChannelClass?.findClass(classLoader)?.run {
            declaredFields.find { it.type == File::class.java }?.name?.let { field ->
                replaceMethod("call") { it.thisObject.getObjectField(field) }
            }
        }
    }
}
