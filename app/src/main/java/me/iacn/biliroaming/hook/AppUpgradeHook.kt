package me.iacn.biliroaming.hook

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.BuildConfig
import me.iacn.biliroaming.utils.*
import org.json.JSONArray
import java.io.File
import java.lang.reflect.Method
import java.net.URL

data class BUpgradeInfo(
    var versionSum: String,
    var url: String,
    var changelog: String,
) {
    val version get() = versionSum.split(' ')[0]
    val versionCode get() = versionSum.split(' ')[1].toLong()
    val moduleVersion get() = versionSum.split(' ')[2]
    val myVerCode get() = versionSum.split(' ')[3].toInt()
    val sn get() = versionSum.split(' ')[4].toLong()
    val size get() = versionSum.split(' ')[5].toLong()
    val md5 get() = versionSum.split(' ')[6]
    val buildTime get() = versionSum.split(' ')[7].toLong()
}

class AppUpgradeHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val biliUpgradeApi = "https://app.bilibili.com/x/v2/version/fawkes/upgrade"
    private val upgradeCheckApi = "https://api.github.com/repos/zjns/BiliRoamingX/releases"

    private val noUpdateResponse: String
        get() = json {
            "code" by -304
            "message" by "木有改动"
        }.toString()

    override fun startHook() {
        instance.helpFragmentClass?.hookAfterMethod(
            "onActivityCreated", Bundle::class.java
        ) { param ->
            val preference = param.thisObject
                .callMethodOrNull("findPreference", "CheckUpdate")
                ?: return@hookAfterMethod
            val pm = currentContext.packageManager
            val verName = pm.getPackageInfo(packageName, 0).versionName
            val buildSn = pm.getApplicationInfo(
                packageName, PackageManager.GET_META_DATA
            ).metaData.getInt("BUILD_SN")
            val mVerName = BuildConfig.VERSION_NAME
            val mVerCode = BuildConfig.VERSION_CODE
            val summary = "当前版本: $verName (release-b$buildSn)\n当前内置漫游版本: $mVerName ($mVerCode)"
            preference.callMethodOrNull("setSummary", summary)
        }
        if (platform == "android") {
            instance.upgradeUtilsClass?.run {
                instance.writeChannelMethod?.let {
                    replaceMethod(it, File::class.java, String::class.java) { null }
                }
            }
            instance.realCallClass?.hookBeforeMethod(instance.executeCall()) { param ->
                val request = param.thisObject.getObjectField(instance.realCallRequestField())
                    ?: return@hookBeforeMethod
                val url = request.getObjectField(instance.urlField())?.toString()
                    ?: return@hookBeforeMethod
                if (url.contains(biliUpgradeApi)) {
                    val protocol = instance.protocolClass?.fields?.get(0)?.get(null)
                        ?: return@hookBeforeMethod
                    val mediaType = instance.mediaTypeClass
                        ?.callStaticMethod(
                            instance.getMediaType(),
                            "application/json; charset=UTF-8"
                        ) ?: return@hookBeforeMethod
                    val content = runCatchingOrNull { checkUpgrade(url) } ?: noUpdateResponse
                    val responseBody = instance.responseBodyClass
                        ?.callStaticMethod(
                            instance.createResponseBody(),
                            mediaType,
                            content
                        ) ?: return@hookBeforeMethod
                    val responseBuildFields = instance.responseBuildFields()
                        .takeIf { it.isNotEmpty() } ?: return@hookBeforeMethod
                    instance.responseBuilderClass?.new()
                        ?.setObjectField(responseBuildFields[0], request)
                        ?.setObjectField(responseBuildFields[1], protocol)
                        ?.setIntField(responseBuildFields[2], 200)
                        ?.setObjectField(responseBuildFields[3], "OK")
                        ?.setObjectField(responseBuildFields[4], responseBody)
                        ?.let { (param.method as Method).returnType.new(it) }
                        ?.let { param.result = it }
                }
            }
        }
    }

    private fun checkUpgrade(biliUrl: String): String {
        val sn = Uri.parse(biliUrl).getQueryParameter("sn")?.toLongOrNull()
            ?: return noUpdateResponse
        val myVerCode = BuildConfig.VERSION_CODE
        val response = JSONArray(URL(upgradeCheckApi).readText())
        for (data in response) {
            if (!data.optString("tag_name").startsWith("bili"))
                continue
            if (data.optBoolean("draft", true))
                continue
            val versionSum = data.optString("name")
            val changelog = data.optString("body").replace("\\n", "\n")
            val url = data.optJSONArray("assets")
                ?.optJSONObject(0)?.optString("browser_download_url") ?: break
            val info = BUpgradeInfo(versionSum, url, changelog)
            if (sn < info.sn || (sn == info.sn && myVerCode < info.myVerCode)) {
                val newMy = sn == info.sn
                var newChangelog =
                    "${info.changelog}\n\nAPP版本：${info.versionCode} b${info.sn}\n内置漫游版本：${info.moduleVersion}"
                val triggeredBy = if (newMy) "本次更新由漫游更新触发" else "本次更新由APP更新触发"
                newChangelog = newChangelog + "\n\n" + triggeredBy
                return json {
                    "code" by 0
                    "message" by "0"
                    "ttl" by 1
                    //"data" by { // not working now
                    "data" by json {
                        "title" by "新版漫游内置包"
                        "content" by newChangelog
                        "version" by info.version
                        "version_code" by if (newMy) info.versionCode + 1 else info.versionCode
                        "url" by "https://ghproxy.com/${info.url}"
                        "size" by info.size
                        "md5" by info.md5
                        "silent" by 0
                        "upgrade_type" by 1
                        "cycle" by 1
                        "policy" by 0
                        "policy_url" by ""
                        "ptime" by info.buildTime
                    }
                }.toString()
            }
            break
        }
        return noUpdateResponse
    }
}
