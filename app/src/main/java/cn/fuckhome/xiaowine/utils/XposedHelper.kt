@file:Suppress("unused", "UNCHECKED_CAST")

package cn.fuckhome.xiaowine.utils

import android.util.Log
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Kotlin helpers on top of the modern (libxposed) Xposed API.
 *
 * The modern API replaces the old `XC_MethodHook` before/after callbacks with an
 * interceptor chain, and it no longer ships anything similar to `XposedHelpers`.
 * These helpers keep the familiar `hookBefore { it.result = ... }` / `findMethod { ... }`
 * style so the hook modules stay readable, while everything underneath is the new chain API.
 */

// ---------------------------------------------------------------------------
// Hook parameter
// ---------------------------------------------------------------------------

/**
 * Parameter object handed to a hooker, modelled after the legacy
 * `XC_MethodHook.MethodHookParam` (`thisObject`, `args`, `result`, `method`).
 *
 * For a before hook, assigning [result] skips the original method and returns that value.
 * For an after hook, [result] initially holds whatever the original method returned and
 * assigning it replaces that value.
 */
class HookParam internal constructor(
    private val chain: XposedInterface.Chain,
    val isBeforeHook: Boolean,
) {
    private var resultValue: Any? = null
    private var overridden = false

    /** The hooked method or constructor. */
    val method: Executable get() = chain.executable

    /**
     * The `this` pointer of the call. Kept non-null to match the legacy `thisObject`
     * platform type; it throws when a hooker asks for it on a static method.
     */
    val thisObject: Any
        get() = chain.thisObject ?: throw IllegalStateException("${chain.executable.name} is static")

    /**
     * The arguments of the call. Typed as non-null (like the legacy `Object[]`) so hookers
     * can chain calls on an argument without extra assertions; individual entries may still
     * be `null` at runtime, exactly as before.
     */
    @Suppress("UNCHECKED_CAST", "USELESS_CAST")
    val args: Array<Any>
        get() = chain.args.toTypedArray() as Array<Any>

    var result: Any?
        get() = resultValue
        set(value) {
            resultValue = value
            overridden = true
        }

    /** `true` when a hooker assigned [result]. */
    val isOverridden: Boolean get() = overridden

    internal fun seedResult(value: Any?) {
        resultValue = value
    }

    /** Calls the rest of the hook chain, i.e. the original method. */
    fun proceed(): Any? = chain.proceed()

    /** Calls the rest of the hook chain with replaced arguments. */
    fun proceed(newArgs: Array<Any?>): Any? = chain.proceed(newArgs)

    /** Calls the rest of the hook chain as if the original returns [value]. */
    fun returnConstant(value: Any?) {
        result = value
    }

    override fun toString(): String = "HookParam(${method.name}, before=$isBeforeHook, overridden=$overridden)"
}

// ---------------------------------------------------------------------------
// Hooking
// ---------------------------------------------------------------------------

private fun installHook(
    member: Member,
    before: Boolean,
    hooker: (HookParam) -> Unit,
): XposedInterface.HookHandle? {
    if (member !is Executable) {
        LogUtils.e("Not a hookable executable: $member")
        return null
    }
    return try {
        HookEnv.xposed.hook(member)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val param = HookParam(chain, before)
                    if (before) {
                        hooker(param)
                        return if (param.isOverridden) param.result else chain.proceed()
                    }
                    val proceeded = chain.proceed()
                    param.seedResult(proceeded)
                    hooker(param)
                    return if (param.isOverridden) param.result else proceeded
                }
            })
    } catch (t: Throwable) {
        LogUtils.e("Failed to hook ${member.declaringClass.name}#${member.name}")
        LogUtils.e(t)
        null
    }
}

/** Runs [hooker] before the original method. Assign `it.result` to skip the original. */
fun Member.hookBefore(hooker: (HookParam) -> Unit): XposedInterface.HookHandle? =
    installHook(this, before = true, hooker = hooker)

/** Runs [hooker] after the original method. Assign `it.result` to replace the return value. */
fun Member.hookAfter(hooker: (HookParam) -> Unit): XposedInterface.HookHandle? =
    installHook(this, before = false, hooker = hooker)

/** Replaces the method entirely, returning [value] without ever calling the original. */
fun Member.hookReturnConstant(value: Any?): XposedInterface.HookHandle? =
    installHook(this, before = true) { it.result = value }

/** Replaces the method entirely with the value produced by [replacer]. */
fun Member.hookReplace(replacer: (HookParam) -> Any?): XposedInterface.HookHandle? =
    installHook(this, before = true) { it.result = replacer(it) }

fun Iterable<Member>.hookBefore(hooker: (HookParam) -> Unit): List<XposedInterface.HookHandle?> =
    map { it.hookBefore(hooker) }

fun Iterable<Member>.hookAfter(hooker: (HookParam) -> Unit): List<XposedInterface.HookHandle?> =
    map { it.hookAfter(hooker) }

fun Iterable<Member>.hookReturnConstant(value: Any?): List<XposedInterface.HookHandle?> =
    map { it.hookReturnConstant(value) }

// ---------------------------------------------------------------------------
// Class based hooking helpers
// ---------------------------------------------------------------------------

fun Class<*>.hookBeforeMethod(
    method: String,
    vararg args: Any?,
    hooker: (HookParam) -> Unit,
): XposedInterface.HookHandle? =
    findMethodExactOrNull(this, method, *args)?.hookBefore(hooker)
        ?: run { LogUtils.e("No method $method in ${name}"); null }

fun Class<*>.hookAfterMethod(
    method: String,
    vararg args: Any?,
    hooker: (HookParam) -> Unit,
): XposedInterface.HookHandle? =
    findMethodExactOrNull(this, method, *args)?.hookAfter(hooker)
        ?: run { LogUtils.e("No method $method in ${name}"); null }

fun Class<*>.hookAllMethods(
    methodName: String,
    hooker: (HookParam) -> Unit,
): List<XposedInterface.HookHandle?> = findAllMethodsInHierarchy(this, methodName).hookBefore(hooker)

fun Class<*>.hookBeforeAllMethods(
    methodName: String,
    hooker: (HookParam) -> Unit,
): List<XposedInterface.HookHandle?> = findAllMethodsInHierarchy(this, methodName).hookBefore(hooker)

fun Class<*>.hookAfterAllMethods(
    methodName: String,
    hooker: (HookParam) -> Unit,
): List<XposedInterface.HookHandle?> = findAllMethodsInHierarchy(this, methodName).hookAfter(hooker)

fun Class<*>.hookReturnConstantAllMethods(
    methodName: String,
    value: Any?,
): List<XposedInterface.HookHandle?> = findAllMethodsInHierarchy(this, methodName).hookReturnConstant(value)

fun Class<*>.hookConstructor(
    vararg args: Any?,
    hooker: (HookParam) -> Unit,
): XposedInterface.HookHandle? =
    findConstructorOrNull(this, *args)?.hookBefore(hooker)
        ?: run { LogUtils.e("No constructor in ${name}"); null }

fun Class<*>.hookBeforeConstructor(
    vararg args: Any?,
    hooker: (HookParam) -> Unit,
): XposedInterface.HookHandle? = hookConstructor(*args, hooker = hooker)

fun Class<*>.hookAfterConstructor(
    vararg args: Any?,
    hooker: (HookParam) -> Unit,
): XposedInterface.HookHandle? =
    findConstructorOrNull(this, *args)?.hookAfter(hooker)
        ?: run { LogUtils.e("No constructor in ${name}"); null }

fun Class<*>.hookAllConstructors(hooker: (HookParam) -> Unit): List<XposedInterface.HookHandle?> =
    declaredConstructors.toList().hookBefore(hooker)

// ---------------------------------------------------------------------------
// Class / member lookup
// ---------------------------------------------------------------------------

fun String.findClass(classLoader: ClassLoader = hookClassLoader): Class<*> =
    findClassOrNull(classLoader) ?: throw ClassNotFoundException(this)

fun String.findClassOrNull(classLoader: ClassLoader = hookClassLoader): Class<*>? =
    runCatching { Class.forName(this, false, classLoader) }.getOrNull()

private fun parameterTypesOf(args: Array<out Any?>): Array<Class<*>>? =
    if (args.isNotEmpty() && args.all { it is Class<*> }) {
        args.map { it as Class<*> }.toTypedArray()
    } else {
        null
    }

fun findMethodExactOrNull(clazz: Class<*>, name: String, vararg args: Any?): Method? {
    val types = parameterTypesOf(args)
    var current: Class<*>? = clazz
    while (current != null) {
        val match = current.declaredMethods.firstOrNull {
            it.name == name && if (types != null) it.parameterTypes.contentEquals(types) else it.parameterCount == args.size
        }
        if (match != null) return match.also { it.isAccessible = true }
        current = current.superclass
    }
    return null
}

private fun findConstructorOrNull(clazz: Class<*>, vararg args: Any?): Constructor<*>? {
    val types = parameterTypesOf(args)
    val match = clazz.declaredConstructors.firstOrNull {
        if (types != null) it.parameterTypes.contentEquals(types) else it.parameterCount == args.size
    }
    return match?.also { it.isAccessible = true }
}

private fun findAllMethodsInHierarchy(clazz: Class<*>, name: String): List<Method> {
    val found = LinkedHashMap<String, Method>()
    var current: Class<*>? = clazz
    while (current != null && current != Any::class.java) {
        for (method in current.declaredMethods) {
            if (method.name != name) continue
            val key = method.name + method.parameterTypes.joinToString(",", "(", ")") { it.name }
            if (found.containsKey(key)) continue
            method.isAccessible = true
            found[key] = method
        }
        current = current.superclass
    }
    return found.values.toList()
}

/** Finds the first method of [className] (walking up the hierarchy) matching [predicate]. */
fun findMethod(
    className: String,
    classLoader: ClassLoader = hookClassLoader,
    predicate: Method.() -> Boolean = { true },
): Method = findMethod(className.findClass(classLoader), predicate)

fun findMethod(clazz: Class<*>, predicate: Method.() -> Boolean = { true }): Method {
    var current: Class<*>? = clazz
    while (current != null && current != Any::class.java) {
        for (method in current.declaredMethods) {
            if (predicate(method)) return method.also { it.isAccessible = true }
        }
        current = current.superclass
    }
    throw NoSuchMethodError("${clazz.name}: no method matching the given condition")
}

fun findMethodOrNull(clazz: Class<*>, predicate: Method.() -> Boolean = { true }): Method? =
    runCatching { findMethod(clazz, predicate) }.getOrNull()

/** Finds every method of [className] (walking up the hierarchy) matching [predicate]. */
fun findAllMethods(
    className: String,
    classLoader: ClassLoader = hookClassLoader,
    predicate: Method.() -> Boolean = { true },
): List<Method> {
    val clazz = className.findClass(classLoader)
    val found = LinkedHashMap<String, Method>()
    var current: Class<*>? = clazz
    while (current != null && current != Any::class.java) {
        for (method in current.declaredMethods) {
            if (!predicate(method)) continue
            val key = method.name + method.parameterTypes.joinToString(",", "(", ")") { it.name }
            if (found.containsKey(key)) continue
            method.isAccessible = true
            found[key] = method
        }
        current = current.superclass
    }
    if (found.isEmpty()) throw NoSuchMethodError("${clazz.name}: no method matching the given condition")
    return found.values.toList()
}

/** Finds the first constructor of [className] matching [predicate]. */
fun findConstructor(
    className: String,
    classLoader: ClassLoader = hookClassLoader,
    predicate: Constructor<*>.() -> Boolean = { true },
): Constructor<*> {
    val clazz = className.findClass(classLoader)
    for (constructor in clazz.declaredConstructors) {
        if (predicate(constructor)) return constructor.also { it.isAccessible = true }
    }
    throw NoSuchMethodError("${clazz.name}: no constructor matching the given condition")
}

// ---------------------------------------------------------------------------
// Reflection helpers
// ---------------------------------------------------------------------------

private fun findFieldInHierarchy(clazz: Class<*>, name: String): Field {
    var current: Class<*>? = clazz
    while (current != null) {
        for (field in current.declaredFields) {
            if (field.name == name) return field.also { it.isAccessible = true }
        }
        current = current.superclass
    }
    throw NoSuchFieldError("${clazz.name}#$name")
}

private fun isParameterCompatible(type: Class<*>, value: Any?): Boolean = when {
    value == null -> !type.isPrimitive
    type.isPrimitive -> when (type) {
        java.lang.Boolean.TYPE -> value is Boolean
        java.lang.Byte.TYPE -> value is Byte
        java.lang.Character.TYPE -> value is Char
        java.lang.Short.TYPE -> value is Short
        Integer.TYPE -> value is Int
        java.lang.Long.TYPE -> value is Long
        java.lang.Float.TYPE -> value is Float
        java.lang.Double.TYPE -> value is Double
        else -> false
    }
    else -> type.isInstance(value)
}

private fun findCompatibleMethod(
    clazz: Class<*>,
    name: String,
    args: Array<out Any?>,
    static: Boolean,
): Method? {
    var current: Class<*>? = clazz
    while (current != null) {
        for (method in current.declaredMethods) {
            if (method.name != name) continue
            if (Modifier.isStatic(method.modifiers) != static) continue
            if (method.parameterCount != args.size) continue
            if (!method.parameterTypes.indices.all { isParameterCompatible(method.parameterTypes[it], args[it]) }) continue
            return method.also { it.isAccessible = true }
        }
        current = current.superclass
    }
    return null
}

private fun invokeReflectively(receiver: Any?, method: Method, args: Array<out Any?>): Any? = try {
    method.invoke(receiver, *args)
} catch (e: InvocationTargetException) {
    throw e.targetException ?: e
}

fun Any.getObjectField(field: String?): Any? {
    requireNotNull(field) { "Field name must not be null" }
    return findFieldInHierarchy(javaClass, field).get(this)
}

fun <T> Any.getObjectAs(field: String?): T {
    requireNotNull(field) { "Field name must not be null" }
    return findFieldInHierarchy(javaClass, field).get(this) as T
}

fun Any.putObject(field: String?, value: Any?) {
    requireNotNull(field) { "Field name must not be null" }
    findFieldInHierarchy(javaClass, field).set(this, value)
}

fun Any.getIntField(field: String?): Int = getObjectField(field) as Int
fun Any.getLongField(field: String?): Long = getObjectField(field) as Long
fun Any.getBooleanField(field: String?): Boolean = getObjectField(field) as Boolean

fun <T : Any> T.setObjectField(field: String?, value: Any?): T = apply { putObject(field, value) }

fun Any.callMethod(methodName: String?, vararg args: Any?): Any? {
    requireNotNull(methodName) { "Method name must not be null" }
    val method = findCompatibleMethod(javaClass, methodName, args, static = false)
        ?: throw NoSuchMethodError("${javaClass.name}#$methodName(${args.joinToString { it?.javaClass?.name ?: "null" }})")
    return invokeReflectively(this, method, args)
}

fun Class<*>.callStaticMethod(methodName: String?, vararg args: Any?): Any? {
    requireNotNull(methodName) { "Method name must not be null" }
    val method = findCompatibleMethod(this, methodName, args, static = true)
        ?: throw NoSuchMethodError("$name#$methodName(${args.joinToString { it?.javaClass?.name ?: "null" }})")
    return invokeReflectively(null, method, args)
}

fun Class<*>.getStaticObjectField(field: String?): Any? {
    requireNotNull(field) { "Field name must not be null" }
    return findFieldInHierarchy(this, field).get(null)
}

fun Class<*>.setStaticObjectField(field: String?, value: Any?) {
    requireNotNull(field) { "Field name must not be null" }
    findFieldInHierarchy(this, field).set(null, value)
}

/** Creates a new instance, bypassing the constructor's visibility. */
fun Class<*>.newInstance(vararg args: Any?): Any {
    val constructor = declaredConstructors.firstOrNull { candidate ->
        candidate.parameterCount == args.size &&
            candidate.parameterTypes.indices.all { isParameterCompatible(candidate.parameterTypes[it], args[it]) }
    } ?: declaredConstructors.firstOrNull { it.parameterCount == args.size }
    ?: throw NoSuchMethodError("$name: no constructor with ${args.size} parameter(s)")
    constructor.isAccessible = true
    return try {
        constructor.newInstance(*args) as Any
    } catch (e: InvocationTargetException) {
        throw e.targetException ?: e
    }
}

/** Reads the value of the first field whose declared type is exactly [type]. */
fun Any.getFirstFieldByExactType(type: Class<*>): Any? {
    var current: Class<*>? = javaClass
    while (current != null) {
        for (field in current.declaredFields) {
            if (field.type == type) return field.also { it.isAccessible = true }.get(this)
        }
        current = current.superclass
    }
    throw NoSuchFieldError("${javaClass.name}: no field of type ${type.name}")
}

fun Any.getFirstFieldByExactTypeOrNull(type: Class<*>?): Any? = runCatchingOrNull {
    getFirstFieldByExactType(type ?: return@runCatchingOrNull null)
}

inline fun <T, R> T.runCatchingOrNull(func: T.() -> R?): R? = try {
    func()
} catch (t: Throwable) {
    null
}

fun Any?.isNull(): Boolean = this == null
fun Any?.isNotNull(): Boolean = this != null

/** Walks the class loader chain to find the dex class loader of a package. */
fun ClassLoader.findDexClassLoader(): dalvik.system.BaseDexClassLoader? {
    var current: ClassLoader? = this
    while (current != null) {
        if (current is dalvik.system.BaseDexClassLoader) return current
        current = current.parent
    }
    return null
}

/** Lists every class name contained in the dex files known to this class loader. */
fun ClassLoader.allClassesList(): List<String> = runCatchingOrNull {
    findDexClassLoader()
        ?.getObjectField("pathList")
        ?.getObjectAs<Array<Any>>("dexElements")
        ?.flatMap { element ->
            element.getObjectField("dexFile")
                ?.callMethod("entries")
                ?.let { enumeration -> (enumeration as java.util.Enumeration<String>).toList() }
                .orEmpty()
        }.orEmpty()
} ?: emptyList()
