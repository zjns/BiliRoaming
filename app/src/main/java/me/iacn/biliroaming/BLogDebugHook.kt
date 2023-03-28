package me.iacn.biliroaming

import me.iacn.biliroaming.hook.BaseHook
import me.iacn.biliroaming.utils.from
import me.iacn.biliroaming.utils.hookBeforeMethod
import java.lang.reflect.Method

class BLogDebugHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        val stringClass = String::class.java
        val throwableClass = Throwable::class.java
        val objectArrayClass = Array<Any>::class.java
        "tv.danmaku.android.log.BLog".from(mClassLoader)?.declaredMethods?.filter { m ->
            m.parameterTypes.let {
                it.size == 2 && it[0] == stringClass && (it[1] == stringClass || it[1] == throwableClass)
                        || it.size == 3 && it[0] == stringClass && it[1] == stringClass
                        && (it[2] == throwableClass || it[2] == objectArrayClass)
            }
        }?.forEach { m ->
            m.hookBeforeMethod { param ->
                val method = param.method as Method
                val methodParamTypes = method.parameterTypes
                val methodParamCount = method.parameterTypes.size

                fun Any?.traceString() =
                    android.util.Log.getStackTraceString(this as? Throwable)

                val tag = "BiliRoaming." + param.args[0] as String
                if (methodParamCount == 2 && methodParamTypes[1] == stringClass) {
                    val messages = param.args[1].toString().chunked(3000)
                    when (method.name) {
                        "i" -> messages.forEach { android.util.Log.i(tag, it) }
                        "d" -> messages.forEach { android.util.Log.d(tag, it) }
                        "w" -> messages.forEach { android.util.Log.w(tag, it) }
                        "e" -> messages.forEach { android.util.Log.e(tag, it) }
                        "v" -> messages.forEach { android.util.Log.v(tag, it) }
                    }
                } else if (methodParamCount == 2 && methodParamTypes[1] == throwableClass) {
                    when (method.name) {
                        "i" -> android.util.Log.i(tag, param.args[1].traceString())
                        "d" -> android.util.Log.d(tag, param.args[1].traceString())
                        "w" -> android.util.Log.w(tag, param.args[1].traceString())
                        "e" -> android.util.Log.e(tag, param.args[1].traceString())
                        "v" -> android.util.Log.v(tag, param.args[1].traceString())
                    }
                } else if (methodParamCount == 3 && methodParamTypes[2] == throwableClass) {
                    val message = (param.args[1] as? String).orEmpty()
                    val throwable = param.args[2] as? Throwable
                    when (method.name) {
                        "i" -> android.util.Log.i(tag, message, throwable)
                        "d" -> android.util.Log.d(tag, message, throwable)
                        "w" -> android.util.Log.w(tag, message, throwable)
                        "e" -> android.util.Log.e(tag, message, throwable)
                        "v" -> android.util.Log.v(tag, message, throwable)
                    }
                } else if (methodParamCount == 3 && methodParamTypes[2] == objectArrayClass
                    && method.name.endsWith("fmt")
                ) {
                    @Suppress("UNCHECKED_CAST")
                    val formats = param.args[2] as Array<Any>
                    val message = (param.args[1] as? String).orEmpty()
                    when (method.name[0].toString()) {
                        "i" -> android.util.Log.i(tag, message.format(*formats))
                        "d" -> android.util.Log.d(tag, message.format(*formats))
                        "w" -> android.util.Log.w(tag, message.format(*formats))
                        "e" -> android.util.Log.e(tag, message.format(*formats))
                        "v" -> android.util.Log.v(tag, message.format(*formats))
                    }
                }
            }
        }
    }
}
