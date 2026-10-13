package cn.fuckhome.xiaowine.ui

import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import cn.fuckhome.xiaowine.utils.putValue

/**
 * Compose observable view over the module configuration.
 *
 * Reads go through a snapshot state map so the UI recomposes when a value changes, while
 * writes are mirrored into the local + remote preference stores.
 */
@Stable
class SettingsStore(private val preferences: SharedPreferences) {

    private val values = mutableStateMapOf<String, Any?>()

    init {
        values.putAll(preferences.all)
    }

    fun bool(key: String, default: Boolean = false): Boolean = values[key] as? Boolean ?: default

    fun int(key: String, default: Int = 0): Int = values[key] as? Int ?: default

    fun string(key: String, default: String = ""): String = values[key] as? String ?: default

    fun set(key: String, value: Any?) {
        values[key] = value
        preferences.edit().apply { putValue(key, value) }.apply()
    }

    fun clear() {
        values.clear()
        preferences.edit().clear().apply()
    }
}
