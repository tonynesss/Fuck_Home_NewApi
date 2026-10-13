package cn.fuckhome.xiaowine.hook

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.Log
import cn.fuckhome.xiaowine.R
import cn.fuckhome.xiaowine.hook.module.add.Info
import cn.fuckhome.xiaowine.hook.module.modify.*
import cn.fuckhome.xiaowine.utils.HookEnv
import cn.fuckhome.xiaowine.utils.Utils.XConfig
import cn.fuckhome.xiaowine.utils.hookBefore
import cn.fuckhome.xiaowine.utils.moduleRes
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

private const val PACKAGE_MIUI_HOME = "com.miui.home"
private const val PACKAGE_POCO_HOME = "com.mi.android.globallauncher"
val homeList = arrayOf(PACKAGE_POCO_HOME, PACKAGE_MIUI_HOME)

private const val PACKAGE_SYSTEM_UI = "com.android.systemui"

/**
 * Modern (libxposed) module entry point.
 *
 * Registered through `META-INF/xposed/java_init.list`; the framework instantiates this
 * class and attaches the framework interface before any lifecycle callback runs.
 * The legacy `IXposedHookZygoteInit` / `IXposedHookLoadPackage` pair has no equivalent
 * here: zygote injection is gone and system_server is handled by
 * [onSystemServerStarting] instead.
 */
class MainHook : XposedModule() {

    @Volatile
    private var featuresInstalled = false

    @Volatile
    private var contextHookInstalled = false

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        HookEnv.attach(this)
        HookEnv.log(
            Log.INFO,
            "loaded in ${param.processName} (systemServer=${param.isSystemServer}, " +
                "api=${apiVersion}, framework=$frameworkName $frameworkVersion)"
        )
    }

    /**
     * Fires as soon as the default class loader of a package exists.
     *
     * `getDefaultClassLoader()` is API 29+, so older devices rely on [onPackageReady].
     */
    override fun onPackageLoaded(param: PackageLoadedParam) {
        HookEnv.attach(this)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        setupForPackage(param.packageName, param.defaultClassLoader)
    }

    /**
     * Fires once the package's real class loader is ready, just before the Application is
     * created. This is the preferred entry point because it hands over the class loader
     * that actually loads the application code.
     */
    override fun onPackageReady(param: PackageReadyParam) {
        HookEnv.attach(this)
        setupForPackage(param.packageName, param.classLoader)
    }

    /** Replaces the legacy `handleLoadPackage` branch for the `android` (system_server) package. */
    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        HookEnv.attach(this)
        if (!isModuleEnabled()) return
        HookEnv.classLoader = param.classLoader
        if (XConfig.getBoolean("RemoveSmallWindowRestriction")) {
            runCatching { RemoveSmallWindowRestriction1.init() }
                .onFailure { HookEnv.log(Log.ERROR, HookEnv.TAG, "system_server hooks failed", it) }
        }
    }

    private fun setupForPackage(packageName: String, classLoader: ClassLoader) {
        if (!isModuleEnabled()) {
            HookEnv.log(Log.INFO, "总开关未打开")
            return
        }
        HookEnv.classLoader = classLoader
        when (packageName) {
            in homeList -> installHomeHooks()

            PACKAGE_SYSTEM_UI -> if (XConfig.getBoolean("RemoveSmallWindowRestriction")) {
                runCatching { RemoveSmallWindowRestriction3.init() }
                    .onFailure { HookEnv.log(Log.ERROR, HookEnv.TAG, "systemui hooks failed", it) }
            }

            else -> Unit
        }
    }

    private fun isModuleEnabled(): Boolean = runCatching { XConfig.getBoolean("MainSwitch") }
        .onFailure { HookEnv.log(Log.ERROR, HookEnv.TAG, "Unable to read the module configuration", it) }
        .getOrDefault(false)

    /**
     * The feature hooks need the application [Context] to build views and to read the
     * module's own resources, so they are installed from inside the application's
     * context attachment.
     *
     * `Application.attach(Context)` is a hidden API; `ContextWrapper.attachBaseContext` is
     * exactly what it delegates to and is public API, so it is hooked instead and filtered
     * down to Application instances.
     */
    private fun installHomeHooks() {
        if (contextHookInstalled) return
        contextHookInstalled = true
        runCatching {
            val attachBaseContext = ContextWrapper::class.java
                .getDeclaredMethod("attachBaseContext", Context::class.java)
            attachBaseContext.isAccessible = true
            attachBaseContext.hookBefore { param ->
                if (param.thisObject !is Application) return@hookBefore
                val context = param.args.firstOrNull() as? Context ?: return@hookBefore
                onApplicationAttached(context)
            }
        }.onFailure {
            HookEnv.log(Log.ERROR, HookEnv.TAG, "Unable to hook Application#attachBaseContext", it)
        }
    }

    private fun onApplicationAttached(context: Context) {
        if (featuresInstalled) return
        synchronized(this) {
            if (featuresInstalled) return
            HookEnv.appContext = context
            HookEnv.moduleResources = HookEnv.loadModuleResources(context, moduleApplicationInfo)
            runCatching {
                if (XConfig.getBoolean("Pad")) {
                    UnlockPad.init()
                }
                if (XConfig.getBoolean("Shortcuts")) {
                    ShortcutItemCount.init()
                }
                if (XConfig.getBoolean("UnlockGrids")) {
                    UnlockGrids.init()
                }
                if (XConfig.getBoolean("HideAppName")) {
                    HideAppName.init()
                }
                if (XConfig.getBoolean("HideAppIcon")) {
                    HideAppIcon.init()
                }
                if (XConfig.getBoolean("UnlockHotseat")) {
                    UnlockHotseatIcon.init()
                }
                if (XConfig.getBoolean("ShortcutSmallWindow")) {
                    ShortcutSmallWindow.init()
                }
                if (XConfig.getBoolean("HideSmallWindow")) {
                    HideSmallWindow.init()
                }
                if (XConfig.getBoolean("RemoveSmallWindowRestriction")) {
                    RemoveSmallWindowRestriction2.init()
                }
                if (XConfig.getBoolean("UnlockNavType")) {
                    UnlockNavType.init()
                }
                Info.init()
                HideStatusBarWhenEnterResents.init()
                featuresInstalled = true
                HookEnv.log(Log.INFO, moduleString(R.string.HookSuccess, "hook success"))
            }.onFailure {
                HookEnv.log(Log.ERROR, HookEnv.TAG, moduleString(R.string.HookFailed, "hook failed"), it)
            }
        }
    }

    private fun moduleString(id: Int, fallback: String): String =
        runCatching { moduleRes.getString(id) }.getOrDefault(fallback)
}
