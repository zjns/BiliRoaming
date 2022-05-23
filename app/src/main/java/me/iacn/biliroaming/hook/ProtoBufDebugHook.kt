package me.iacn.biliroaming.hook

import de.robv.android.xposed.XC_MethodHook
import me.iacn.biliroaming.utils.*
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class ProtoBufDebugHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    private val mossLogHooker = fun(param: XC_MethodHook.MethodHookParam, after: Boolean) {
        if (after && (param.method as Method).returnType != Void.TYPE && !param.args.isNullOrEmpty()) {
            Log.d("call moss method ${param.method}")
            Log.d("moss method ${param.method} req:\n${param.args[0]}")
            Log.d("moss method ${param.method} reply:\n${param.result}")
        } else if (!after && !param.args.isNullOrEmpty()
            && "com.bilibili.lib.moss.api.MossResponseHandler".on(mClassLoader)
                .isInstance(param.args.last())
        ) {
            val handler = param.args.last()
            Log.d("call moss method ${param.method}, handler type: ${handler.javaClass.name}")
            if (param.args.size > 1) {
                Log.d("moss method ${param.method} req:\n${param.args[0]}")
            }
            param.args[param.args.lastIndex] = Proxy.newProxyInstance(
                handler.javaClass.classLoader,
                handler.javaClass.interfaces
            ) { _, m, args ->
                if (m.name == "onNext") {
                    Log.d("moss method ${param.method} reply:\n${args[0]}")
                }
                if (args == null) {
                    m.invoke(handler)
                } else {
                    m.invoke(handler, *args)
                }
            }
        }
    }

    private fun logMoss(className: String) {
        className.on(mClassLoader).declaredMethods.forEach { m ->
            m.hookAfterMethod { mossLogHooker(it, true) }
            m.hookBeforeMethod { mossLogHooker(it, false) }
        }
    }

    override fun startHook() {

        logMoss("com.bapis.bilibili.app.view.v1.ViewMoss")

        logMoss("com.bapis.bilibili.pgc.gateway.player.v1.PlayURLMoss")

        logMoss("com.bapis.bilibili.pgc.gateway.player.v2.PlayURLMoss")

        logMoss("com.bapis.bilibili.community.service.dm.v1.DMMoss")

        logMoss("com.bapis.bilibili.main.community.reply.v1.ReplyMoss")

        logMoss("com.bapis.bilibili.app.dynamic.v2.DynamicMoss")

        "com.google.protobuf.MessageLiteToString".hookBeforeMethod(
            mClassLoader,
            "printField",
            StringBuilder::class.java,
            Int::class.javaPrimitiveType,
            String::class.java,
            Any::class.java
        ) { param ->
            val sb = param.args[0] as StringBuilder
            val indent = param.args[1] as Int
            val name = param.args[2] as String
            val obj = param.args[3]
            val printField = fun(sb: StringBuilder, indent: Int, name: String, obj: Any?) {
                (param.method as Method).invoke(null, sb, indent, name, obj)
            }
            if (obj is List<*>) {
                obj.forEach { printField(sb, indent, name, it) }
            } else if (obj is Map<*, *>) {
                obj.forEach { printField(sb, indent, name, it) }
            } else {
                sb.appendLine()
                repeat(indent) { sb.append(' ') }
                sb.append(name)
                if (obj is String) {
                    sb.append(": \"")
                    sb.append(obj)
                    sb.append('\"')
                } else if (obj.javaClass.name == "com.google.protobuf.ByteString") {
                    sb.append(": \"")
                    sb.append(obj.callMethod("toString", Charsets.UTF_8))
                    sb.append('\"')
                } else if (obj.javaClass.superclass?.name == "com.google.protobuf.GeneratedMessageLite") {
                    sb.append(" {")
                    val realObj = if (obj.javaClass.name == "com.google.protobuf.Any") {
                        val type = obj.callMethodAs<String>("getTypeUrl").substringAfter("/")
                        "com.bapis.$type".on(mClassLoader)
                            .callStaticMethod("parseFrom", obj.callMethod("getValue"))
                    } else obj
                    "com.google.protobuf.MessageLiteToString".on(mClassLoader)
                        .callStaticMethod("reflectivePrintWithIndent", realObj, sb, indent + 2)
                    sb.appendLine()
                    repeat(indent) { sb.append(' ') }
                    sb.append('}')
                } else if (obj is Map.Entry<*, *>) {
                    sb.append(" {")
                    printField(sb, indent + 2, "key", obj.key)
                    printField(sb, indent + 2, "value", obj.value)
                    sb.appendLine()
                    repeat(indent) { sb.append(' ') }
                    sb.append('}')
                } else {
                    sb.append(": ")
                    sb.append(obj.toString())
                }
            }
            param.result = null
        }
    }
}
