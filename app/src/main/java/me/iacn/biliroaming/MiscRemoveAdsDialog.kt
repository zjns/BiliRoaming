package me.iacn.biliroaming

import android.app.Activity
import android.content.SharedPreferences
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.dp

class MiscRemoveAdsDialog(activity: Activity, prefs: SharedPreferences) :
    BaseWidgetDialog(activity) {
    init {
        val scrollView = ScrollView(context).apply {
            scrollBarStyle = ScrollView.SCROLLBARS_OUTSIDE_OVERLAY
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        scrollView.addView(root)

        val removeSearchAdsSwitch = string(R.string.remove_search_ads_title).let {
            switchPrefsItem(it).let { p -> root.addView(p.first); p.second }
        }
        removeSearchAdsSwitch.isChecked = prefs.getBoolean("remove_search_ads", false)

        val removeCommentCmSwitch = string(R.string.remove_comment_cm_title).let {
            switchPrefsItem(it).let { p -> root.addView(p.first); p.second }
        }
        removeCommentCmSwitch.isChecked = prefs.getBoolean("remove_comment_cm", false)

        val blockDmFeedbackSwitch = string(R.string.block_dm_feedback_title).let {
            switchPrefsItem(it).let { p -> root.addView(p.first); p.second }
        }
        blockDmFeedbackSwitch.isChecked = prefs.getBoolean("block_dm_feedback", false)

        setTitle(string(R.string.misc_remove_ads_title))

        setPositiveButton(android.R.string.ok) { _, _ ->
            prefs.edit().apply {
                putBoolean("remove_search_ads", removeSearchAdsSwitch.isChecked)
                putBoolean("remove_comment_cm", removeCommentCmSwitch.isChecked)
                putBoolean("block_dm_feedback", blockDmFeedbackSwitch.isChecked)
            }.apply()
            Log.toast(string(R.string.prefs_save_success_and_reboot))
        }
        setNegativeButton(android.R.string.cancel, null)

        root.setPadding(16.dp, 10.dp, 16.dp, 10.dp)

        setView(scrollView)
    }
}
