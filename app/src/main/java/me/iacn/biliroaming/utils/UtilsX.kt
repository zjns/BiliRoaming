package me.iacn.biliroaming.utils

import android.content.Context
import android.content.SharedPreferences
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.hookInfo
import me.iacn.biliroaming.orNull
import java.io.File

@Suppress("DEPRECATION")
fun blkvPrefsByName(name: String, multiProcess: Boolean = true): SharedPreferences {
    return instance.blkvClass?.callStaticMethodAs(
        hookInfo.blkv.getByName.orNull, currentContext, name, multiProcess, 0
    ) ?: currentContext.getSharedPreferences(name, Context.MODE_MULTI_PROCESS)
}

@Suppress("DEPRECATION")
fun blkvPrefsByFile(file: File, multiProcess: Boolean = true): SharedPreferences {
    return instance.blkvClass?.callStaticMethodAs(
        hookInfo.blkv.getByFile.orNull, currentContext, file, multiProcess, 0
    ) ?: currentContext.getSharedPreferences(file.nameWithoutExtension, Context.MODE_MULTI_PROCESS)
}

val abPrefs by lazy {
    val abPath = "prod/blconfig/ab.sp"
    val file = File(currentContext.getDir("foundation", Context.MODE_PRIVATE), abPath)
    blkvPrefsByFile(file, multiProcess = true)
}
