@file:Suppress("unused")

package cn.fuckhome.xiaowine.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import io.github.libxposed.api.XposedModule

/**
 * Process wide state shared by the hooks of a single hooked process.
 *
 * The modern Xposed API hands the module an [XposedModule] instance instead of a static
 * bridge, so everything that used to be a global (class loader, application context,
 * module resources, logger) lives here.
 */
object HookEnv {
    const val TAG = "Fuck_Home"

    @Volatile
    var module: XposedModule? = null
        private set

    @Volatile
    var classLoader: ClassLoader? = null

    @Volatile
    var appContext: Context? = null

    @Volatile
    var moduleResources: Resources? = null

    val xposed: XposedModule
        get() = module ?: throw IllegalStateException("XposedModule is not attached yet")

    fun attach(instance: XposedModule) {
        module = instance
    }

    fun log(priority: Int, message: String) = log(priority, TAG, message)

    fun log(priority: Int, tag: String?, message: String) {
        val instance = module
        if (instance != null) {
            runCatching { instance.log(priority, tag, message) }.onFailure { Log.println(priority, tag, message) }
        } else {
            Log.println(priority, tag ?: TAG, message)
        }
    }

    fun log(priority: Int, tag: String?, message: String, tr: Throwable?) {
        val instance = module
        if (instance != null) {
            runCatching { instance.log(priority, tag, message, tr) }
                .onFailure { Log.println(priority, tag, "$message\n${Log.getStackTraceString(tr)}") }
        } else {
            Log.println(priority, tag ?: TAG, "$message\n${Log.getStackTraceString(tr)}")
        }
    }

    /**
     * The module configuration exposed by the framework. Read-only inside the hooked app.
     * Returns `null` when the framework has no remote capability, in which case every
     * configuration lookup simply falls back to its default value.
     */
    fun remotePreferences(group: String): SharedPreferences? {
        val instance = module ?: return null
        return runCatching { instance.getRemotePreferences(group) }
            .onFailure { log(Log.WARN, "Remote preferences \"$group\" are unavailable: $it") }
            .getOrNull()
    }

    /**
     * Loads the module APK's own resources so hooks can use the module's strings and
     * drawables inside the hooked app process.
     */
    fun loadModuleResources(context: Context, appInfo: ApplicationInfo): Resources? = runCatching {
        val path = appInfo.sourceDir ?: appInfo.publicSourceDir
        val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
        val addAssetPath = AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
        addAssetPath.isAccessible = true
        addAssetPath.invoke(assets, path)
        val base = context.resources
        Resources(assets, base.displayMetrics, base.configuration)
    }.onFailure { log(Log.ERROR, "Unable to load module resources: $it") }.getOrNull()
}

/** Convenience accessor for the hooked application context. */
val appContext: Context
    get() = HookEnv.appContext ?: throw IllegalStateException("Application context is not ready")

/** Convenience accessor for the module APK resources. */
val moduleRes: Resources
    get() = HookEnv.moduleResources ?: throw IllegalStateException("Module resources are not ready")

/** The class loader of the hooked package. */
val hookClassLoader: ClassLoader
    get() = HookEnv.classLoader ?: HookEnv.xposed.javaClass.classLoader ?: ClassLoader.getSystemClassLoader()
