@file:Suppress("MatchingDeclarationName")

package com.kairo.reader.data.books

import com.kairo.reader.core.dispatchers.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal object TestDispatchers : DispatcherProvider {
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
}

@Suppress("UNCHECKED_CAST")
internal fun <T> Any.callPrivate(name: String, vararg args: Any): T {
    val method =
        javaClass.declaredMethods.firstOrNull { candidate ->
            candidate.name == name &&
                candidate.parameterTypes.size == args.size &&
                candidate.parameterTypes.zip(args).all { (expected, actual) ->
                    isCompatibleParameter(expected, actual)
                }
        } ?: throw NoSuchMethodException(
            "No matching method: ${javaClass.name}.$name(${args.joinToString { it.javaClass.name }})",
        )
    method.isAccessible = true
    return method.invoke(this, *args) as T
}

private fun isCompatibleParameter(
    expectedType: Class<*>,
    actualValue: Any,
): Boolean {
    val actualType = actualValue.javaClass
    if (!expectedType.isPrimitive) {
        return expectedType.isAssignableFrom(actualType)
    }

    return when (expectedType) {
        java.lang.Boolean.TYPE -> actualType == Boolean::class.javaObjectType
        java.lang.Byte.TYPE -> actualType == Byte::class.javaObjectType
        java.lang.Short.TYPE -> actualType == Short::class.javaObjectType
        java.lang.Integer.TYPE -> actualType == Int::class.javaObjectType
        java.lang.Long.TYPE -> actualType == Long::class.javaObjectType
        java.lang.Float.TYPE -> actualType == Float::class.javaObjectType
        java.lang.Double.TYPE -> actualType == Double::class.javaObjectType
        java.lang.Character.TYPE -> actualType == Char::class.javaObjectType
        else -> false
    }
}
