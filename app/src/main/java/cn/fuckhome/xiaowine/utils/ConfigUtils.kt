/*
 * StatusBarLyric
 * Copyright (C) 2021-2022 fkj@fkj233.cn
 * https://github.com/577fkj/StatusBarLyric
 *
 * This software is free opensource software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either
 * version 3 of the License, or any later version and our eula as published
 * by 577fkj.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * and eula along with this software.  If not, see
 * <https://www.gnu.org/licenses/>
 * <https://github.com/577fkj/StatusBarLyric/blob/main/LICENSE>.
 */

package cn.fuckhome.xiaowine.utils

import android.content.SharedPreferences

/**
 * Thin wrapper around a [SharedPreferences] instance.
 *
 * The legacy `XSharedPreferences` backend is gone: the settings UI uses the mirrored
 * local/remote store and the hooks read the framework's remote preferences. A `null`
 * store simply yields the supplied default for every read.
 */
class ConfigUtils(private val sp: SharedPreferences?) {

    /** Remote preferences are refreshed by the framework, nothing to reload locally. */
    fun update() = Unit

    fun put(key: String?, value: Any?) {
        if (key == null) return
        val editor = sp?.edit() ?: return
        editor.putValue(key, value)
        editor.apply()
    }

    fun optInt(key: String, def: Int): Int = runCatching { sp?.getInt(key, def) ?: def }.getOrDefault(def)

    fun optBoolean(key: String, def: Boolean): Boolean =
        runCatching { sp?.getBoolean(key, def) ?: def }.getOrDefault(def)

    fun optString(key: String, def: String): String =
        runCatching { sp?.getString(key, def) ?: def }.getOrDefault(def)

    fun optFloat(key: String, def: Float): Float = runCatching { sp?.getFloat(key, def) ?: def }.getOrDefault(def)

    fun clearConfig() {
        runCatching { sp?.edit()?.clear()?.apply() }
    }
}
