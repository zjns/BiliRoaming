package me.iacn.biliroaming.hook

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.BuildConfig
import me.iacn.biliroaming.utils.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL

class BUpgradeInfo(
    private val versionSum: String,
    val url: String,
    val changelog: String,
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

@Suppress("DEPRECATION")
class AppUpgradeHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    companion object {
        private const val upgradeCheckApi =
            "https://api.github.com/repos/zjns/BiliRoamingX/releases"
    }

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
        if (platform != "android") return

        instance.upgradeUtilsClass?.run {
            instance.writeChannelMethod?.let {
                replaceMethod(it, File::class.java, String::class.java) { null }
            }
        }
        instance.supplierClass?.hookBeforeMethod(
            instance.checkMethod,
            Context::class.java
        ) { param ->
            val context = param.args[0] as Context
            val result = runCatchingOrNull { checkUpgrade() }
            if (result == null) {
                param.throwable = Exception("检查更新失败，请稍后再试/(ㄒoㄒ)/~~")
                return@hookBeforeMethod
            }
            when (result.optInt("code", -1)) {
                0 -> {
                    val data = result.getJSONObject("data")
                    val upgradeInfo = instance.fastJsonClass?.callStaticMethod(
                        instance.fastJsonParse(),
                        data.toString(),
                        instance.upgradeInfoClass
                    )
                    instance.upgradeUtilsClass?.callStaticMethodOrNull(
                        instance.writeInfoMethod, context, upgradeInfo
                    )
                    param.result = upgradeInfo
                }
                -304 -> {
                    instance.upgradeUtilsClass?.callStaticMethodOrNull(
                        instance.cleanApkDirMethod, context, true
                    )
                    param.throwable = instance.versionExceptionClass
                        ?.new("您当前已经是最新版本了^_^") as Throwable
                }
                else -> {
                    param.throwable = Exception("检查更新失败，请稍后再试/(ㄒoㄒ)/~~")
                }
            }
        }
    }

    private fun checkUpgrade(): JSONObject {
        val sn = currentContext.packageManager.getApplicationInfo(
            packageName, PackageManager.GET_META_DATA
        ).metaData.getInt("BUILD_SN").toLong()
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
                return mapOf(
                    "code" to 0,
                    "message" to "0",
                    "ttl" to 1,
                    "data" to mapOf(
                        "title" to "新版漫游内置包",
                        "content" to newChangelog,
                        "version" to info.version,
                        "version_code" to if (newMy) info.versionCode + 1 else info.versionCode,
                        "url" to "https://ghproxy.com/${info.url}",
                        "size" to info.size,
                        "md5" to info.md5,
                        "silent" to 0,
                        "upgrade_type" to 1,
                        "cycle" to 1,
                        "policy" to 0,
                        "policy_url" to "",
                        "ptime" to info.buildTime,
                    )
                ).toJsonObject()
            }
            break
        }
        return mapOf("code" to -304, "message" to "木有改动").toJsonObject()
    }
}
