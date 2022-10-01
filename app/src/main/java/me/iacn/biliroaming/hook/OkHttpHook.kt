package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.hook.api.*
import me.iacn.biliroaming.utils.*
import java.net.HttpURLConnection

class OkHttpHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    private val apiHooks = mutableListOf<ApiHook>()

    init {
        apiHooks.add(SeasonRcmdHook)
        apiHooks.add(CardsHook)
        apiHooks.add(BannerV3AdHook)
        apiHooks.add(SkinHook)
        apiHooks.add(BannerV8AdHook)
    }

    override fun startHook() {
        if (apiHooks.all { !it.enabled }) return

        instance.responseClass?.hookAfterAllConstructors out@{ param ->
            val response = param.thisObject ?: return@out
            val requestField = instance.requestField() ?: return@out
            val urlField = instance.urlField() ?: return@out
            val request = response.getObjectField(requestField) ?: return@out
            val url = request.getObjectField(urlField)?.toString() ?: return@out
            for (hook in apiHooks) {
                if (!hook.enabled || !hook.canHandler(url))
                    continue
                val okioClass = instance.okioClass ?: return@out
                val bufferedSourceClass = instance.bufferedSourceClass ?: return@out
                val codeField = instance.codeField() ?: return@out
                val bodyField = instance.bodyField() ?: return@out
                val stringMethod = instance.string() ?: return@out
                val sourceMethod = instance.source() ?: return@out
                val bufferMethod = instance.sourceBuffer() ?: return@out
                response.getIntField(codeField).takeIf { it == HttpURLConnection.HTTP_OK }
                    ?: return@out

                val responseBody = response.getObjectField(bodyField)
                val sourceField = responseBody?.javaClass
                    ?.findFieldByExactTypeOrNull(bufferedSourceClass) ?: return@out
                val longType = Long::class.javaPrimitiveType!!
                val contentLengthField = responseBody.javaClass.findFieldByExactTypeOrNull(longType)
                    ?: return@out
                val respString = responseBody.callMethod(stringMethod)?.toString() ?: return@out
                val newResponse = hook.hook(respString)
                val stream = newResponse.byteInputStream()
                val length = stream.available()
                val source = okioClass.callStaticMethod(sourceMethod, stream) ?: return@out
                val bufferedSource = okioClass.callStaticMethod(bufferMethod, source) ?: return@out
                sourceField.set(responseBody, bufferedSource)
                contentLengthField.set(responseBody, length)
                break
            }
        }
    }
}
