package cn.fuckhome.xiaowine.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArrayList

/**
 * App side bridge to the framework's remote preferences.
 *
 * The settings UI keeps a plain local [SharedPreferences] as the single source of truth
 * (so the UI never shows stale or empty values while the framework service is not bound
 * yet), and mirrors every change into the remote preferences so the hooked processes can
 * read the very same configuration.
 *
 * The framework delivers its binder as soon as this app's process starts
 * (LSPosed calls `XposedProvider` right after `uidStarts`), so [onServiceBind] normally
 * fires within a few milliseconds of process start.
 */
object ModulePrefs {
    const val GROUP = "Fuck_Home_Config"
    private const val TAG = "Fuck_Home"

    @Volatile
    private var localPreferences: SharedPreferences? = null

    @Volatile
    private var remotePreferences: SharedPreferences? = null

    @Volatile
    private var xposedService: XposedService? = null

    @Volatile
    private var listenerRegistered = false

    private val bindListeners = CopyOnWriteArrayList<() -> Unit>()

    /** `true` once the framework service has been delivered to this process. */
    val isBound: Boolean get() = remotePreferences != null

    /** Human readable framework identification, or `null` when not bound. */
    val frameworkInfo: String?
        get() = xposedService?.let { service ->
            runCatching { "${service.frameworkName} ${service.frameworkVersion}" }.getOrNull()
        }

    fun init(context: Context) {
        if (localPreferences == null) {
            localPreferences = context.createDeviceProtectedStorageContext()
                .getSharedPreferences(GROUP, Context.MODE_PRIVATE)
        }
        if (listenerRegistered) return
        listenerRegistered = true
        runCatching {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    xposedService = service
                    remotePreferences = runCatching { service.getRemotePreferences(GROUP) }
                        .onFailure { Log.w(TAG, "getRemotePreferences($GROUP) failed", it) }
                        .getOrNull()
                    syncToRemote()
                    bindListeners.forEach { runCatching { it() } }
                }

                override fun onServiceDied(service: XposedService) {
                    if (xposedService === service) {
                        xposedService = null
                        remotePreferences = null
                    }
                }
            })
        }.onFailure { Log.w(TAG, "Unable to register the Xposed service listener", it) }
    }

    fun addOnBindListener(listener: () -> Unit) {
        bindListeners.add(listener)
        if (isBound) runCatching { listener() }
    }

    /**
     * Watches for the framework service for at most [timeoutMs]. Must not be called on the
     * main thread.
     */
    fun awaitService(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!isBound && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        return isBound
    }

    /** The preferences handed to the settings UI: read locally, write to both stores. */
    fun preferences(): SharedPreferences {
        val local = localPreferences
            ?: throw IllegalStateException("ModulePrefs.init() has not been called")
        return DualPreferences(local) { remotePreferences }
    }

    /**
     * Pushes the local configuration into the remote store, or restores the local copy when
     * it is empty (fresh install / cleared data) while the remote store still holds values.
     */
    fun syncToRemote() {
        val local = localPreferences ?: return
        val remote = remotePreferences ?: return
        runCatching {
            val localValues = local.all
            if (localValues.isEmpty()) {
                val remoteValues = remote.all
                if (remoteValues.isNotEmpty()) {
                    local.edit().apply {
                        remoteValues.forEach { (key, value) -> putValue(key, value) }
                        apply()
                    }
                }
            } else {
                remote.edit().apply {
                    localValues.forEach { (key, value) -> putValue(key, value) }
                    apply()
                }
            }
        }.onFailure { Log.w(TAG, "Unable to synchronise preferences", it) }
    }
}

internal fun SharedPreferences.Editor.putValue(key: String, value: Any?) {
    when (value) {
        null -> remove(key)
        is String -> putString(key, value)
        is Int -> putInt(key, value)
        is Long -> putLong(key, value)
        is Float -> putFloat(key, value)
        is Boolean -> putBoolean(key, value)
        is Set<*> -> {
            @Suppress("UNCHECKED_CAST")
            putStringSet(key, value as Set<String>)
        }

        else -> putString(key, value.toString())
    }
}

/**
 * Reads from [local] and writes to both [local] and the remote store, so the settings UI
 * always shows what the user configured while the hook side still receives every change.
 */
class DualPreferences(
    private val local: SharedPreferences,
    private val remoteProvider: () -> SharedPreferences?,
) : SharedPreferences {

    override fun getAll(): MutableMap<String, *> = local.all

    override fun getString(key: String, defValue: String?): String? = local.getString(key, defValue)

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        local.getStringSet(key, defValues)

    override fun getInt(key: String, defValue: Int): Int = local.getInt(key, defValue)

    override fun getLong(key: String, defValue: Long): Long = local.getLong(key, defValue)

    override fun getFloat(key: String, defValue: Float): Float = local.getFloat(key, defValue)

    override fun getBoolean(key: String, defValue: Boolean): Boolean = local.getBoolean(key, defValue)

    override fun contains(key: String): Boolean = local.contains(key)

    override fun edit(): SharedPreferences.Editor = DualEditor(local.edit(), remoteProvider()?.edit())

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        local.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        local.unregisterOnSharedPreferenceChangeListener(listener)
    }
}

private class DualEditor(
    private val local: SharedPreferences.Editor,
    private val remote: SharedPreferences.Editor?,
) : SharedPreferences.Editor {

    override fun putString(key: String, value: String?): SharedPreferences.Editor {
        local.putString(key, value)
        remote?.putString(key, value)
        return this
    }

    override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
        local.putStringSet(key, values)
        remote?.putStringSet(key, values)
        return this
    }

    override fun putInt(key: String, value: Int): SharedPreferences.Editor {
        local.putInt(key, value)
        remote?.putInt(key, value)
        return this
    }

    override fun putLong(key: String, value: Long): SharedPreferences.Editor {
        local.putLong(key, value)
        remote?.putLong(key, value)
        return this
    }

    override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
        local.putFloat(key, value)
        remote?.putFloat(key, value)
        return this
    }

    override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
        local.putBoolean(key, value)
        remote?.putBoolean(key, value)
        return this
    }

    override fun remove(key: String): SharedPreferences.Editor {
        local.remove(key)
        remote?.remove(key)
        return this
    }

    override fun clear(): SharedPreferences.Editor {
        local.clear()
        remote?.clear()
        return this
    }

    override fun commit(): Boolean {
        val remoteResult = runCatching { remote?.commit() ?: true }.getOrDefault(false)
        return local.commit() && remoteResult
    }

    override fun apply() {
        // RemotePreferences.Editor.apply() commits asynchronously on the framework's own
        // single-threaded executor, where anything other than a RemoteException escapes and kills
        // the process (an uncaught exception on any thread is fatal on Android). Committing here
        // keeps the write immediately observable and lets a failing framework call stay contained.
        runCatching { remote?.commit() }
        local.apply()
    }
}
