package me.iacn.biliroaming.hook

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import me.iacn.biliroaming.BiliBiliPackage
import me.iacn.biliroaming.hook.SubtitleHook.Companion.currentSubtitles
import me.iacn.biliroaming.utils.*
import me.iacn.biliroaming.utils.SubtitleHelper.convertToSrt
import me.iacn.biliroaming.utils.SubtitleHelper.reSort
import me.iacn.biliroaming.utils.SubtitleHelper.removeSubAppendedInfo
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy
import java.net.URL

class SubtitleDownloadHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    private var currentVideoTitle: String? = null
    private var preventFinish = false

    override fun startHook() {
        if (!(sPrefs.getBoolean("main_func", false)
                    && sPrefs.getBoolean("enable_download_subtitle", false))
        ) return

        "com.bapis.bilibili.app.view.v1.ViewMoss".from(mClassLoader)?.run {
            hookBeforeMethod(
                "view",
                "com.bapis.bilibili.app.view.v1.ViewReq"
            ) { currentSubtitles = listOf() }
            hookAfterMethod(
                "view",
                "com.bapis.bilibili.app.view.v1.ViewReq"
            ) { param ->
                val result = param.result ?: return@hookAfterMethod
                currentVideoTitle = result.callMethod("getArc")
                    ?.callMethodAs("getTitle")
            }
        }

        val onActivityResultHook = fun(param: MethodHookParam, video: Boolean) {
            val thiz = param.thisObject as Activity
            val requestCode = param.args[0] as Int
            val resultCode = param.args[1] as Int
            val data = (param.args[2] as Intent?)?.data
            if (data == null || resultCode != Activity.RESULT_OK) return
            val titleDir = if (video) currentVideoTitle ?: return
            else BangumiSeasonHook.lastSeasonInfo["title"] ?: return
            val epId = BangumiSeasonHook.lastSeasonInfo["epid"]
            val epTitleDir = BangumiSeasonHook.lastSeasonInfo["ep_title_$epId"]
            if (!video && epTitleDir == null) return
            val exportJson = requestCode == reqCodeJson
            val ext = if (exportJson) "json" else "srt"
            val mimeType = if (exportJson) "application/json"
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                "application/x-subrip"
            else
                "application/octet-stream"
            SubtitleHelper.executor.execute {
                val titleDirDoc = DocumentFile.fromTreeUri(thiz, data)
                    ?.findOrCreateDir(titleDir) ?: return@execute
                currentSubtitles.forEach { item ->
                    val lan = item.lan
                    val lanDoc = item.lanDoc
                    val url = item.subtitleUrl
                    val fileName = if (video) "$titleDir-$lan-$lanDoc.$ext"
                    else "$titleDir-$epTitleDir-$lan-$lanDoc.$ext"
                    val subFileDoc = titleDirDoc
                        .run { if (!video) findOrCreateDir(epTitleDir ?: "") else this }
                        ?.findOrCreateFile(mimeType, fileName)
                        ?: return@forEach
                    thiz.contentResolver.openOutputStream(subFileDoc.uri, "wt")?.use { os ->
                        runCatching {
                            var text = URL(url).readText()
                            if (url.contains("zh_converter=t2cn"))
                                text = SubtitleHelper.convert(text)
                            val json = JSONObject(text)
                            val body = json.getJSONArray("body")
                                .removeSubAppendedInfo().reSort()
                            json.put("body", body)
                            if (exportJson) {
                                val prettyJson = json.toString(2)
                                os.write(prettyJson.toByteArray())
                            } else {
                                os.write(body.convertToSrt().toByteArray())
                            }
                            Log.toast("字幕 $fileName 下载完成", force = true)
                        }.onFailure {
                            Log.toast("字幕 $fileName 下载失败", force = true)
                        }
                    }
                }
            }
        }

        "com.bilibili.multitypeplayerV2.MultiTypeVideoContentActivity".from(mClassLoader)?.run {
            hookAfterMethod(
                "onActivityResult",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Intent::class.java
            ) { onActivityResultHook(it, true) }
            hookAfterMethod("onDestroy") {
                currentSubtitles = listOf()
                preventFinish = false
            }
        }

        "com.bilibili.video.videodetail.VideoDetailsActivity".from(mClassLoader)?.run {
            hookAfterMethod(
                "onActivityResult",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Intent::class.java
            ) { onActivityResultHook(it, true) }
            hookAfterMethod("onDestroy") {
                currentSubtitles = listOf()
                preventFinish = false
            }
        }

        "com.bilibili.bangumi.ui.page.detail.BangumiDetailActivityV3".from(mClassLoader)?.run {
            hookAfterMethod(
                "onActivityResult",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Intent::class.java
            ) { onActivityResultHook(it, false) }
            hookAfterMethod("onDestroy") {
                currentSubtitles = listOf()
                preventFinish = false
            }
        }

        BiliBiliPackage.instance.toolbarServiceClass?.hookBeforeMethod(
            BiliBiliPackage.instance.miniPlayMethod, Context::class.java
        ) { param ->
            if (preventFinish) {
                preventFinish = false
                param.result = null
            }
        }

        val superMenuClass =
            "com.bilibili.app.comm.supermenu.SuperMenu".from(mClassLoader) ?: return
        val menuItemClickListenerClass =
            "com.bilibili.app.comm.supermenu.core.listeners.OnMenuItemClickListenerV2"
                .from(mClassLoader) ?: return
        val menuItemClickListenerField =
            superMenuClass.findFieldByExactType(menuItemClickListenerClass) ?: return
        val menuItemImplClass = "com.bilibili.app.comm.supermenu.core.MenuItemImpl"
            .from(mClassLoader) ?: return
        superMenuClass.hookBeforeMethod("show") { param ->
            if (currentSubtitles.isEmpty())
                return@hookBeforeMethod
            val thiz = param.thisObject
            val activityRef =
                thiz.getFirstFieldByExactTypeAs<WeakReference<Activity>>(WeakReference::class.java)
            val activity = activityRef?.get() ?: return@hookBeforeMethod
            if (activity.isFinishing || activity.isDestroyed) return@hookBeforeMethod
            val menu = thiz.callMethodAs<List<*>>("getMenus").last() ?: return@hookBeforeMethod
            val menuItems = menu.getFirstFieldByExactTypeAs<MutableList<Any>>(List::class.java)
                ?.also { l ->
                    l.find {
                        it.callMethodAs<String?>("getItemId")
                            .let { id -> id == settingsItemId || id == settingsItemId2 }
                    } ?: return@hookBeforeMethod
                } ?: return@hookBeforeMethod
            val subDownloadItem = menuItemImplClass.new(
                currentContext,
                subDownloadItemId,
                downloadIconResId,
                "字幕下载"
            )
            menuItems.add(0, subDownloadItem)
            val menuItemClickListener = menuItemClickListenerField.get(thiz)
            val proxyClickListener = Proxy.newProxyInstance(
                menuItemClickListener.javaClass.classLoader,
                arrayOf(menuItemClickListenerClass),
            ) { _, m, args ->
                if (m.name == "onItemClick" && args[0].callMethod("getItemId") == subDownloadItemId) {
                    showFormatChoiceDialog(activity)
                    true
                } else {
                    m.invoke(menuItemClickListener, *args)
                }
            }
            menuItemClickListenerField.set(thiz, proxyClickListener)
        }
    }

    @SuppressLint("InlinedApi")
    private fun showFormatChoiceDialog(activity: Activity) {
        val appDialogTheme = getResId("AppTheme.Dialog.Alert", "style")
        AlertDialog.Builder(activity, appDialogTheme)
            .setTitle("格式选择")
            .setItems(arrayOf("json", "srt")) { _, which ->
                if (windowAlertPermissionGranted())
                    preventFinish = true
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    putExtra(
                        DocumentsContract.EXTRA_INITIAL_URI,
                        Uri.parse("content://com.android.externalstorage.documents/document/primary:Download")
                    )
                }
                activity.startActivityForResult(intent, if (which == 0) reqCodeJson else reqCodeSrt)
            }
            .create().apply {
                setOnShowListener {
                    window?.let { w ->
                        @Suppress("DEPRECATION")
                        val screenWidth = w.windowManager.defaultDisplay.width
                        w.attributes = w.attributes.also {
                            it.width = (screenWidth * 0.5).toInt()
                        }
                    }
                }
            }
            .show()
    }

    companion object {
        private const val reqCodeJson = 6666
        private const val reqCodeSrt = 8888
        private const val settingsItemId = "menu_settings" // pgc/bangumi
        private const val settingsItemId2 = "PLAY_SETTING" // ugc/video
        private const val subDownloadItemId = "menu_download_subtitles"
        private val downloadIconResId by lazy { getResId("bangumi_sheet_ic_downloads", "drawable") }
    }
}
