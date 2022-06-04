package me.iacn.biliroaming.utils

import org.json.JSONObject

fun json(build: JSONObject.() -> Unit) = JSONObject().apply { build() }

context(JSONObject)
infix fun String.by(build: JSONObject.() -> Unit): JSONObject = put(this, JSONObject().build())

context(JSONObject)
infix fun String.by(value: Any): JSONObject = put(this, value)
