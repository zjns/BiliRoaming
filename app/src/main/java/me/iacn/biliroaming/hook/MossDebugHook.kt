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
            && instance.mossResponseHandlerClass?.isInstance(param.args.last()) == true
        ) {
            val handler = param.args.last()
            Log.d("call async moss method ${param.method}, handler type: ${handler.javaClass.name}")
            if (param.args.size > 1) {
                Log.d("async moss method ${param.method} req:\n${param.args[0]}")
            }
            param.args[param.args.lastIndex] = Proxy.newProxyInstance(
                handler.javaClass.classLoader,
                //handler.javaClass.interfaces,
                arrayOf(instance.mossResponseHandlerClass)
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
            if (element.methodName.startsWith("blockingUnaryCall")) {
                stackTrace.getOrNull(index + 1)?.let {
                    mossMethod = "${it.className}#${it.methodName}"
                }
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
            if (element.methodName.startsWith("asyncUnaryCall")) {
                stackTrace.getOrNull(index + 1)?.let {
                    mossMethod = "${it.className}#${it.methodName}"
                }
            }
        }
        val handler = param.args[2]
        Log.d("call async moss method $mossMethod, handler type: ${handler.javaClass.name}")
        Log.d("async moss method $mossMethod req:\n${param.args[1]}")
        param.args[2] = Proxy.newProxyInstance(
            handler.javaClass.classLoader,
            //handler.javaClass.interfaces,
            arrayOf(instance.mossResponseHandlerClass)
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
        var mossServiceClass = "com.bilibili.lib.moss.api.MossService".from(mClassLoader)
        if (mossServiceClass == null || mossServiceClass.isInterface)
            mossServiceClass = "com.bilibili.lib.moss.api.MossServiceImp".from(mClassLoader)
        mossServiceClass?.declaredMethods?.run {
            find { it.name == "asyncUnaryCall" }?.hookBeforeMethod(asyncMossLogHooker)
            find { it.name == "blockingUnaryCall" }?.hookAfterMethod(blockingMossLogHooker)
        }

        val messageLiteToStringClass = "com.google.protobuf.MessageLiteToString".from(mClassLoader)
        val byteStringClass = "com.google.protobuf.ByteString".from(mClassLoader)
        val generatedMessageLiteClass =
            "com.google.protobuf.GeneratedMessageLite".from(mClassLoader)
        val anyClass = "com.google.protobuf.Any".from(mClassLoader)
        messageLiteToStringClass?.hookBeforeMethod(
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
                } else if (obj?.javaClass == byteStringClass) {
                    sb.append(": \"")
                    sb.append(obj.callMethod("toString", Charsets.UTF_8))
                    sb.append('"')
                } else if (obj?.javaClass?.superclass == generatedMessageLiteClass) {
                    sb.append(" {")
                    val realObj = if (obj.javaClass == anyClass) {
                        val typeUrl = obj.callMethodAs<String>("getTypeUrl")
                        val type = typeUrl.substringAfter('/')
                        val realClass = "com.bapis.$type".from(mClassLoader)
                            ?: findType(typeUrl)?.let { it on mClassLoader }
                        realClass?.callStaticMethod("parseFrom", obj.callMethod("getValue"))
                            ?: obj
                    } else obj
                    messageLiteToStringClass
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
                    sb.append(obj?.toString())
                }
            }
            param.result = null
        }
    }
}
