package me.iacn.biliroaming.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.Options
import me.iacn.biliroaming.R
import me.iacn.biliroaming.XposedInit.Companion.moduleRes
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream

class TrieNode<V>(val key: Char, val level: Int = 0) {
    private val children = hashMapOf<Char, TrieNode<V>>()

    val isLeaf get() = value != null
    var value: V? = null

    fun getOrAddChild(k: Char) = children.computeIfAbsent(k) { TrieNode(k, level + 1) }

    fun child(k: Char) = children[k]
}

class Trie<T> {
    private val root = TrieNode<T>(key = '\u0000')

    fun add(w: String, value: T) {
        if (w.isEmpty()) return
        var p = root
        for (c in w.toCharArray())
            p = p.getOrAddChild(c)
        p.value = value
    }

    fun bestMatch(sen: CharArray): TrieNode<T>? {
        var node: TrieNode<T> = root
        var leaf: TrieNode<T>? = null
        for (c in sen) {
            node = node.child(c) ?: break
            if (node.isLeaf) leaf = node
        }
        return leaf
    }
}

class Dictionary(
    private val chars: Map<Char, Char>,
    private val dict: Trie<String>,
    private val maxLen: Int
) {
    private fun convert(reader: Reader, writer: Writer) {
        val `in` = PushbackReader(reader.buffered(), maxLen)
        val buf = CharArray(maxLen)
        var len: Int

        while (true) {
            len = `in`.read(buf)
            if (len == -1) break
            val node = dict.bestMatch(buf)
            if (node != null) {
                val offset = node.level
                node.value?.let { writer.write(it) }
                `in`.unread(buf, offset, len - offset)
            } else {
                `in`.unread(buf, 0, len)
                val ch = `in`.read().toChar()
                writer.write(chars.getOrDefault(ch, ch).code)
            }
        }
    }

    fun convert(str: String) = StringWriter().also {
        convert(str.reader(), it)
    }.toString()

    companion object {
        private const val SHARP = '#'
        private const val EQUAL = '='

        fun loadDictionary(mappingFile: File): Dictionary {
            val charMap = HashMap<Char, Char>(4096)
            val dict = Trie<String>()
            var maxLen = 2
            mappingFile.bufferedReader().useLines { lines ->
                lines.filterNot { it.isBlank() || it.trimStart().startsWith(SHARP) }
                    .map { it.split(EQUAL, limit = 2) }.filter { it.size == 2 }.forEach { (k, v) ->
                        if (k.length == 1 && v.length == 1) {
                            charMap[k[0]] = v[0]
                        } else {
                            maxLen = k.length.coerceAtLeast(maxLen)
                            dict.add(k, v)
                        }
                    }
            }
            return Dictionary(charMap, dict, maxLen)
        }
    }
}

@Suppress("UNUSED")
object SubtitleHelper {
    private val dictFile by lazy { File(currentContext.filesDir, "t2cn.txt") }
    private val dictionary by lazy { Dictionary.loadDictionary(dictFile) }
    private const val dictUrl =
        "https://archive.biliimg.com/bfs/archive/566adec17e127bf92aed21832db0206ccecc8caa.png"
    private const val checkInterval = 60 * 1000

    // !!! Do not remove symbol '\' for "\}", Android need it
    @Suppress("RegExpRedundantEscape")
    private val noStyleRegex =
        Regex("""\{\\?\\an\d+\}|<font\s[^>]*>|<\\?/font>|<i>|<\\?/i>|<b>|<\\?/b>|<u>|<\\?/u>""")
    val dictExist get() = dictFile.isFile
    val executor: ExecutorService by lazy { Executors.newFixedThreadPool(1) }

    @Synchronized
    fun downloadDict(): Boolean {
        if (dictExist) return true
        runCatching {
            val buffer = URL(dictUrl).openStream().buffered().use {
                val options = Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
                val bitmap = BitmapFactory.decodeStream(it, null, options)
                ByteBuffer.allocate(bitmap!!.byteCount).apply {
                    bitmap.let { b -> b.copyPixelsToBuffer(this); b.recycle() }
                    rewind()
                }
            }
            val bytes = ByteArray(buffer.int).also { buffer.get(it) }
            dictFile.outputStream().use { o ->
                GZIPInputStream(bytes.inputStream()).use { it.copyTo(o) }
            }
        }.onSuccess {
            return true
        }.onFailure {
            Log.e(it)
            dictFile.delete()
        }
        return false
    }

    fun checkDictUpdate(): String? {
        val lastCheckTime = sCaches.getLong("subtitle_dict_last_check_time", 0)
        if (System.currentTimeMillis() - lastCheckTime < checkInterval && dictExist)
            return null
        sCaches.edit().putLong("subtitle_dict_last_check_time", System.currentTimeMillis()).apply()
        val url = moduleRes.getString(R.string.subtitle_dict_latest_url)
        val json = runCatchingOrNull {
            JSONObject(URL(url).readText())
        } ?: return null
        val tagName = json.optString("tag_name")
        val latestVer = sCaches.getString("subtitle_dict_latest_version", null) ?: ""
        if (latestVer != tagName || !dictExist) {
            val sha256sum = json.optString("body")
                .takeUnless { it.isNullOrEmpty() } ?: return null
            var dictUrl = json.optJSONArray("assets")
                ?.optJSONObject(0)?.optString("browser_download_url")
                .takeUnless { it.isNullOrEmpty() } ?: return null
            dictUrl = "https://ghproxy.com/$dictUrl"
            runCatching {
                dictFile.outputStream().use { o ->
                    GZIPInputStream(URL(dictUrl).openStream())
                        .use { it.copyTo(o) }
                }
            }.onSuccess {
                if (dictFile.sha256sum == sha256sum) {
                    sCaches.edit().putString("subtitle_dict_latest_version", tagName).apply()
                    return dictFile.path
                }
                dictFile.delete()
            }.onFailure {
                Log.e(it)
                dictFile.delete()
            }
        }
        return null
    }

    fun reloadDict() {
        dict(true)
    }

    @Volatile
    private var dict: Dictionary? = null
    private fun dict(reload: Boolean = false): Dictionary {
        val d = dict
        if (!reload && d != null)
            return d
        synchronized(this) {
            var newD = dict
            if (reload || newD == null)
                newD = Dictionary.loadDictionary(dictFile)
            dict = newD
            return newD
        }
    }

    fun convert(json: String): String {
        val subJson = JSONObject(json)
        var subBody = subJson.optJSONArray("body") ?: return json
        val subText = subBody.asSequence<JSONObject>().map { it.optString("content") }
            .joinToString("\u0000").run {
                // Remove srt style, bilibili not support it
                if (contains("\\an") || contains("<font")
                    || contains("<i>") || contains("<b>") || contains("<u>")
                ) replace(noStyleRegex, "") else this
            }
        val converted = dict().convert(subText)
        val lines = converted.split('\u0000')
        subBody.asSequence<JSONObject>().zip(lines.asSequence()).forEach { (obj, line) ->
            obj.put("content", line)
        }
        subBody = subBody.appendInfo(moduleRes.getString(R.string.subtitle_append_info))
        return subJson.apply {
            put("body", subBody)
        }.toString()
    }

    fun errorResponse(content: String) = JSONObject().apply {
        put("body", JSONArray().apply {
            put(JSONObject().apply {
                put("from", 0)
                put("location", 2)
                put("to", 9999)
                put("content", content)
            })
        })
    }.toString()

    private fun JSONArray.appendInfo(content: String): JSONArray {
        if (length() == 0) return this
        val firstLine = optJSONObject(0)
            ?: return this
        val lastLine = optJSONObject(length() - 1)
            ?: return this
        val firstFrom = firstLine.optDouble("from")
            .takeIf { !it.isNaN() } ?: return this
        val lastTo = lastLine.optDouble("to")
            .takeIf { !it.isNaN() } ?: return this
        val minDuration = 1.0
        val maxDuration = 5.0
        val interval = 0.3
        val appendStart = firstFrom >= minDuration + interval
        val from = if (appendStart) 0.0 else lastTo + interval
        val to = if (appendStart) {
            from + (firstFrom - interval).coerceAtMost(maxDuration)
        } else from + maxDuration
        val info = JSONObject().apply {
            put("from", from)
            put("location", 2)
            put("to", to)
            put("content", content)
        }
        return if (appendStart) {
            JSONArray().apply {
                put(info)
                for (jo in this@appendInfo) {
                    put(jo)
                }
            }
        } else apply { put(info) }
    }

    private const val furrySubInfoT = "「字幕由 富睿字幕組 搬運」\n（禁止在B站宣傳漫遊相關内容，否則拉黑）"
    private const val furrySubInfoS = "「字幕由 富睿字幕组 搬运」\n（禁止在B站宣传漫游相关内容，否则拉黑）"
    private const val furrySubInfoS2 =
        "「字幕由 富睿字幕组 搬运」\n（禁止在B站宣传漫游相关内容，否则拉黑）\n（禁止在泰区评论，禁止在B站任何地方讨论泰区相关内容）"
    private val mineSubInfo by lazy { moduleRes.getString(R.string.subtitle_append_info) }

    fun JSONArray.removeSubAppendedInfo() = apply {
        var maybeHasSame = false
        (5 downTo 0).forEach { idx ->
            optJSONObject(idx)?.let {
                val content = it.optString("content")
                if (content == furrySubInfoT || content == furrySubInfoS || content == furrySubInfoS2 || content == mineSubInfo) {
                    remove(idx)
                } else if (content.contains(furrySubInfoT)
                    || content.contains(furrySubInfoS)
                    || content.contains(furrySubInfoS2)
                ) {
                    maybeHasSame = true
                    val newContent = content
                        .replace("\n$furrySubInfoT", "")
                        .replace("$furrySubInfoT\n", "")
                        .replace("\n$furrySubInfoS2", "")
                        .replace("$furrySubInfoS2\n", "")
                        .replace("\n$furrySubInfoS", "")
                        .replace("$furrySubInfoS\n", "")
                    it.put("content", newContent)
                }
            }
        }
        if (maybeHasSame) {
            var end = -1
            var start = -1
            var content = ""
            var from = 0.0
            var to = 0.0
            (5 downTo 0).forEach { idx ->
                optJSONObject(idx)?.let {
                    val f = it.optDouble("from")
                    val t = it.optDouble("to")
                    val c = it.optString("content")
                    if (end == -1) {
                        end = idx; content = c; to = t; from = f
                    } else {
                        if (c != content) {
                            if (start != -1) {
                                for (i in end downTo start + 1)
                                    remove(i)
                                optJSONObject(start)?.put("to", to)
                            }
                            end = idx; content = c; to = t; from = f; start = -1
                        } else if (t == from) {
                            start = idx; from = t
                            if (start == 0) {
                                for (i in end downTo 1)
                                    remove(i)
                                optJSONObject(0)?.put("to", to)
                            }
                        }
                    }
                }
            }
        }
        val lastIdx = length() - 1
        optJSONObject(lastIdx)?.let {
            val content = it.optString("content")
            if (content == mineSubInfo) {
                remove(lastIdx)
            }
        }
    }

    fun JSONArray.reSort() = apply {
        for (o in this) {
            val content = o.getString("content")
            val from = o.getDouble("from")
            val location = o.getInt("location")
            val to = o.getDouble("to")
            o.remove("content")
            o.remove("from")
            o.remove("location")
            o.remove("to")
            o.put("content", content)
            o.put("from", from)
            o.put("location", location)
            o.put("to", to)
        }
    }

    fun JSONArray.convertToSrt(): String {
        fun timeFormat(time: Double): String {
            val ms = (1000 * (time - time.toInt())).toInt()
            val seconds = time.toInt()
            val sec = seconds % 60
            val minutes = seconds / 60
            val min = minutes % 60
            val hour = minutes / 60
            return "%02d:%02d:%02d,%03d".format(hour, min, sec, ms)
        }

        var lineCount = 1
        val result = StringBuilder()
        for (o in this) {
            val content = o.optString("content")
            val from = o.optDouble("from")
            val to = o.optDouble("to")
            result.appendLine(lineCount++)
            result.appendLine(timeFormat(from) + " --> " + timeFormat(to))
            result.appendLine(content)
            result.appendLine()
        }
        return result.toString()
    }
}
