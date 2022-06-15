@file:Suppress("NOTHING_TO_INLINE")
package me.iacn.biliroaming.utils

import org.json.JSONObject

inline fun Map<String, Any>.toJson() = JSONObject(this).toString()

fun json(build: JSONObject.() -> Unit) = JSONObject().apply(build)

context(JSONObject)
infix fun String.by(build: JSONObject.() -> Unit): JSONObject = put(this, JSONObject().build())

context(JSONObject)
infix fun String.by(value: Any): JSONObject = put(this, value)
