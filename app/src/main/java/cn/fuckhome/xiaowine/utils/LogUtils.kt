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

import android.util.Log

/**
 * Logger used by the hook side. Messages go to the Xposed log through the modern
 * [io.github.libxposed.api.XposedInterface.log] instead of the removed `XposedBridge`.
 *
 * Informational messages follow the module's `Debug` switch; errors are always logged so
 * hook failures stay diagnosable even with debugging disabled.
 */
object LogUtils {
    private const val maxLength = 4000
    const val TAG = "Fuck_Home"

    private fun isEnabled(): Boolean = runCatching { Utils.XConfig.getBoolean("Debug") }.getOrDefault(false)

    private fun log(obj: Any?, toXposed: Boolean = false, toLogd: Boolean = false, force: Boolean = false) {
        if (!force && !isEnabled()) return
        val content = if (obj is Throwable) Log.getStackTraceString(obj) else obj.toString()
        if (content.length > maxLength) {
            val chunkCount = content.length / maxLength
            for (i in 0..chunkCount) {
                val max = maxLength * (i + 1)
                val chunk = if (max >= content.length) content.substring(maxLength * i) else content.substring(maxLength * i, max)
                if (toXposed) HookEnv.log(Log.INFO, TAG, "$TAG: $chunk")
                if (toLogd) Log.i(TAG, chunk)
            }
        } else {
            if (toXposed) HookEnv.log(Log.INFO, TAG, "$TAG: $content")
            if (toLogd) Log.i(TAG, content)
        }
    }

    fun i(obj: Any?) {
        log(obj, toXposed = true)
    }

    /** Always logged, regardless of the module's `Debug` switch. */
    fun e(obj: Any?) {
        log(obj, toXposed = true, toLogd = true, force = true)
    }

    fun d(obj: Any?) {
        log(obj, toLogd = true)
    }
}
