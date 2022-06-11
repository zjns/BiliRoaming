package me.iacn.biliroaming.hook

import de.robv.android.xposed.XC_MethodHook
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class MossDebugHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    private val secondMossLogHooker = fun(param: XC_MethodHook.MethodHookParam, after: Boolean) {
        if (after && (param.method as Method).returnType != Void.TYPE && !param.args.isNullOrEmpty()) {
            Log.d("call blocking moss method ${param.method}")
            Log.d("blocking moss method ${param.method} req:\n${param.args[0]}")
            Log.d("blocking moss method ${param.method} reply:\n${param.result}")
        } else if (!after && !param.args.isNullOrEmpty()
            && "com.bilibili.lib.moss.api.MossResponseHandler".on(mClassLoader)
                .isInstance(param.args.last())
        ) {
            val handler = param.args.last()
            Log.d("call async moss method ${param.method}, handler type: ${handler.javaClass.name}")
            if (param.args.size > 1) {
                Log.d("async moss method ${param.method} req:\n${param.args[0]}")
            }
            param.args[param.args.lastIndex] = Proxy.newProxyInstance(
                handler.javaClass.classLoader,
                //handler.javaClass.interfaces,
                arrayOf("com.bilibili.lib.moss.api.MossResponseHandler".on(mClassLoader))
            ) { _, m, args ->
                if (m.name == "onNext") {
                    Log.d("async moss method ${param.method} reply:\n${args[0]}")
                }
                if (args == null) {
                    m.invoke(handler)
                } else {
                    m.invoke(handler, *args)
                }
            }
        }
    }

    private val blockingMossLogHooker = fun(param: XC_MethodHook.MethodHookParam) {
        var mossMethod = ""
        val stackTrace = Thread.currentThread().stackTrace
        stackTrace.forEachIndexed { index, element ->
            if (element.methodName == "blockingUnaryCall") {
                stackTrace.getOrNull(index + 1)?.let {
                    mossMethod = "${it.className}#${it.methodName}"
                }
                return@forEachIndexed
            }
        }
        Log.d("call blocking moss method $mossMethod")
        Log.d("blocking moss method $mossMethod req:\n${param.args[1]}")
        Log.d("blocking moss method $mossMethod reply:\n${param.result}")
    }

    private val asyncMossLogHooker = fun(param: XC_MethodHook.MethodHookParam) {
        var mossMethod = ""
        val stackTrace = Thread.currentThread().stackTrace
        stackTrace.forEachIndexed { index, element ->
            if (element.methodName == "asyncUnaryCall") {
                stackTrace.getOrNull(index + 1)?.let {
                    mossMethod = "${it.className}#${it.methodName}"
                }
                return@forEachIndexed
            }
        }
        val handler = param.args.last()
        Log.d("call async moss method $mossMethod, handler type: ${handler.javaClass.name}")
        Log.d("async moss method $mossMethod req:\n${param.args[1]}")
        param.args[param.args.lastIndex] = Proxy.newProxyInstance(
            handler.javaClass.classLoader,
            //handler.javaClass.interfaces
            arrayOf("com.bilibili.lib.moss.api.MossResponseHandler".on(mClassLoader))
        ) { _, m, args ->
            if (m.name == "onNext") {
                Log.d("async moss method $mossMethod reply:\n${args[0]}")
            }
            if (args == null) {
                m.invoke(handler)
            } else {
                m.invoke(handler, *args)
            }
        }
    }

    private fun logMoss(mossClass: String) {
        mossClass.on(mClassLoader).declaredMethods.forEach { m ->
            m.hookAfterMethod { secondMossLogHooker(it, true) }
            m.hookBeforeMethod { secondMossLogHooker(it, false) }
        }
    }

    private val typeMap = mutableMapOf<String, String>()

    private fun findType(typeUrl: String) = typeMap[typeUrl] ?: run {
        val dexHelper = instance.globalDexHelper
        dexHelper.findMethodUsingString(
            typeUrl,
            false,
            -1,
            0,
            null,
            -1,
            null,
            null,
            null,
            true
        ).asSequence().firstNotNullOfOrNull {
            dexHelper.decodeMethodIndex(it) as? Method
        }?.returnType?.name?.also {
            typeMap[typeUrl] = it
        }
    }

    override fun startHook() {
        "com.bilibili.lib.moss.api.MossService".hookAfterMethod(
            mClassLoader,
            "blockingUnaryCall",
            "io.grpc.MethodDescriptor",
            "com.google.protobuf.GeneratedMessageLite",
            hooker = blockingMossLogHooker
        )
        "com.bilibili.lib.moss.api.MossService".hookBeforeMethod(
            mClassLoader,
            "asyncUnaryCall",
            "io.grpc.MethodDescriptor",
            "com.google.protobuf.GeneratedMessageLite",
            "com.bilibili.lib.moss.api.MossResponseHandler",
            hooker = asyncMossLogHooker
        )

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
            val printField = fun(sb: StringBuilder, indent: Int, name: String, obj: Any?): Any? {
                return (param.method as Method).invoke(null, sb, indent, name, obj)
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
                    sb.append('"')
                } else if (obj.javaClass.name == "com.google.protobuf.ByteString") {
                    sb.append(": \"")
                    sb.append(obj.callMethod("toString", Charsets.UTF_8))
                    sb.append('"')
                } else if (obj.javaClass.superclass?.name == "com.google.protobuf.GeneratedMessageLite") {
                    sb.append(" {")
                    val realObj = if (obj.javaClass.name == "com.google.protobuf.Any") {
                        val typeUrl = obj.callMethodAs<String>("getTypeUrl")
                        val type = typeUrl.substringAfter('/')
                        val realClass = "com.bapis.$type".from(mClassLoader)
                            ?: findType(typeUrl)?.let { it on mClassLoader }
                        realClass?.callStaticMethod("parseFrom", obj.callMethod("getValue"))
                            ?: obj
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
