package me.iacn.biliroaming.hook.api

interface ApiHook {
    val enabled: Boolean

    fun canHandler(api: String): Boolean
    fun hook(response: String): String
}
