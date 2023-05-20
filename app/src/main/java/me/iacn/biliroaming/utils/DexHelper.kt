package me.iacn.biliroaming.utils

import java.lang.reflect.Field
import java.lang.reflect.Member

class DexHelper(private val classLoader: ClassLoader) : AutoCloseable {

    private val token = load(classLoader)

    external fun findMethodUsingString(
        str: String,
        matchPrefix: Boolean = false,
        returnType: Long = -1,
        parameterCount: Short = -1,
        parameterShorty: String? = null,
        declaringClass: Long = -1,
        parameterTypes: LongArray? = null,
        containsParameterTypes: LongArray? = null,
        dexPriority: IntArray? = null,
        findFirst: Boolean = true
    ): LongArray

    external fun findMethodInvoking(
        methodIndex: Long,
        returnType: Long = -1,
        parameterCount: Short = -1,
        parameterShorty: String? = null,
        declaringClass: Long = -1,
        parameterTypes: LongArray? = null,
        containsParameterTypes: LongArray? = null,
        dexPriority: IntArray? = null,
        findFirst: Boolean = true
    ): LongArray

    external fun findMethodInvoked(
        methodIndex: Long,
        returnType: Long = -1,
        parameterCount: Short = -1,
        parameterShorty: String? = null,
        declaringClass: Long = -1,
        parameterTypes: LongArray? = null,
        containsParameterTypes: LongArray? = null,
        dexPriority: IntArray? = null,
        findFirst: Boolean = true
    ): LongArray

    external fun findMethodSettingField(
        fieldIndex: Long,
        returnType: Long = -1,
        parameterCount: Short = -1,
        parameterShorty: String? = null,
        declaringClass: Long = -1,
        parameterTypes: LongArray? = null,
        containsParameterTypes: LongArray? = null,
        dexPriority: IntArray? = null,
        findFirst: Boolean = true
    ): LongArray

    external fun findMethodGettingField(
        fieldIndex: Long,
        returnType: Long = -1,
        parameterCount: Short = -1,
        parameterShorty: String? = null,
        declaringClass: Long = -1,
        parameterTypes: LongArray? = null,
        containsParameterTypes: LongArray? = null,
        dexPriority: IntArray? = null,
        findFirst: Boolean = true
    ): LongArray

    external fun findField(
        type: Long,
        dexPriority: IntArray? = null,
        findFirst: Boolean = true
    ): LongArray

    external fun decodeMethodIndex(methodIndex: Long): Member?

    external fun encodeMethodIndex(method: Member): Long

    external fun decodeFieldIndex(fieldIndex: Long): Field?

    external fun encodeFieldIndex(field: Field): Long

    external fun encodeClassIndex(clazz: Class<*>): Long

    external fun decodeClassIndex(classIndex: Long): Class<*>?

    external fun createFullCache()

    external override fun close()

    protected fun finalize() = close()

    private external fun load(classLoader: ClassLoader): Long
}
