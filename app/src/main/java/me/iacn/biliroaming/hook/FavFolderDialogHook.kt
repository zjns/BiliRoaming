package me.iacn.biliroaming.hook

import android.app.Dialog
import android.content.Context
import android.widget.CheckBox
import me.iacn.biliroaming.from
import me.iacn.biliroaming.hookInfo
import me.iacn.biliroaming.orNull
import me.iacn.biliroaming.utils.*

class FavFolderDialogHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!sPrefs.getBoolean("disable_auto_subscribe", false)) return
        var hooked = false
        hookInfo.favFolderDialog.class_.from(mClassLoader)?.hookAfterAllConstructors { param ->
            if (hooked) return@hookAfterAllConstructors
            val apiCallbackClass = param.thisObject.javaClass.declaredFields
                .firstOrNull { f -> f.type.let { it != Context::class.java && !it.isInterface && it.isAbstract } }
                ?.also { it.isAccessible = true }?.get(param.thisObject)?.javaClass
                ?: return@hookAfterAllConstructors
            val onSuccessMethod = apiCallbackClass.declaredMethods.firstOrNull {
                !it.isSynthetic && it.parameterTypes.size == 1
            } ?: return@hookAfterAllConstructors
            val dialogFieldName = apiCallbackClass.declaredFields.firstOrNull {
                Dialog::class.java.isAssignableFrom(it.type)
            }?.name ?: return@hookAfterAllConstructors
            onSuccessMethod.hookAfterMethod { param2 ->
                val checkBox = param2.thisObject.getObjectField(dialogFieldName)
                    ?.getObjectFieldAs<CheckBox>(hookInfo.favFolderDialog.checkBox.orNull)
                    ?: return@hookAfterMethod
                if (checkBox.isChecked)
                    checkBox.toggle()
            }
            hooked = true
        }
    }
}
