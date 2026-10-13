@file:Suppress("DEPRECATION")

package cn.fuckhome.xiaowine.utils

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.SystemClock
import android.text.format.Formatter
import android.util.Log
import android.widget.TextView
import cn.fuckhome.xiaowine.config.Config
import cn.fuckhome.xiaowine.hook.module.add.Info


object Utils {
    /** Remote preference group shared between the settings UI and the hooked processes. */
    const val PREF_NAME = "Fuck_Home_Config"

    /**
     * Hook side configuration. Backed by the framework's remote preferences, which are
     * read-only inside the hooked app. When the framework has no remote capability every
     * lookup falls back to its default value (see [Config]).
     */
    val XConfig: Config by lazy { Config(HookEnv.remotePreferences(PREF_NAME)) }


    fun viewColor(view: TextView, info: MemoryUtils?) {
        if (info.isNotNull() && XConfig.getBoolean("Warning") && info!!.percentValue < Info.threshold) {
            view.setTextColor(Color.RED)
        } else {
            if (XConfig.getColor().isEmpty()) {
                view.setTextColor(Info.textColors)
            } else {
                view.setTextColor(Color.parseColor(XConfig.getColor()))
            }

        }
    }

    object Uptime {
        fun get(): String {
            val time = SystemClock.elapsedRealtime() / 1000
            var temp: Int
            val sb = StringBuffer()
            if (time > 3600) {
                temp = (time / 3600).toInt()
                sb.append(if (time / 3600 < 10) "0$temp:" else "$temp:")
                temp = (time % 3600 / 60).toInt()
                changeSeconds(time, temp, sb)
            } else {
                temp = (time % 3600 / 60).toInt()
                changeSeconds(time, temp, sb)
            }
            return sb.toString()
        }

        private fun changeSeconds(seconds: Long, temp: Int, sb: StringBuffer) {
            var temps = temp
            sb.append(if (temps < 10) "0$temps:" else "$temps:")
            temps = (seconds % 3600 % 60).toInt()
            sb.append(if (temps < 10) "0$temps" else "" + temps)
        }

    }

    fun catchNoClass(callback: () -> Unit) {
        runCatching { callback() }.exceptionOrNull().let {
            Log.i(LogUtils.TAG, "${callback.javaClass.simpleName}错误")
        }
    }

    fun Any.formatSize(): String = Formatter.formatFileSize(appContext, this as Long)


    fun Any?.isNull(callback: () -> Unit) {
        if (this == null) callback()
    }

    fun Any?.isNotNull(callback: () -> Unit) {
        if (this != null) callback()
    }

    fun Any?.isNull() = this == null

    fun Any?.isNotNull() = this != null

    /** The settings UI preferences: local storage mirrored into the remote store. */
    @SuppressLint("WorldReadableFiles")
    fun preferences() = ModulePrefs.preferences()
}
