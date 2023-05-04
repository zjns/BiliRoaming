package me.iacn.biliroaming.hook.api

interface ApiHook {
    val enabled: Boolean

    fun canHandler(api: String): Boolean
    fun decodeResponse(): Boolean = true
    fun hook(response: String): String
}
