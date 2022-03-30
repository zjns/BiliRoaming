package me.iacn.biliroaming.hook

import android.app.AlertDialog
import android.content.Context
import me.iacn.biliroaming.BiliBiliPackage
import me.iacn.biliroaming.utils.*

class DarkSwitchHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    override fun startHook() {
        BiliBiliPackage.instance.userFragmentClass?.run {
            BiliBiliPackage.instance.switchDarkModeMethod?.let {
                hookBeforeMethod(it, Boolean::class.javaPrimitiveType) { param ->
                    val activity = param.thisObject
                        .callMethodOrNullAs<Context>("getActivity")
                        ?: return@hookBeforeMethod
                    val themeUtils =
                        BiliBiliPackage.instance.themeUtilsClass ?: return@hookBeforeMethod
                    val isDarkFollowSystem = themeUtils.callStaticMethodOrNullAs<Boolean>(
                        BiliBiliPackage.instance.isDarkFollowSystemMethod, activity
                    ) ?: return@hookBeforeMethod
                    if (isDarkFollowSystem) {
                        AlertDialog.Builder(activity)
                            .setMessage("将关闭深色跟随系统，确定切换？")
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                param.invokeOriginalMethod()
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                        param.result = null
                    }
                }
            }
        }
    }
}
