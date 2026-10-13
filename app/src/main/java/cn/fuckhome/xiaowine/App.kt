package cn.fuckhome.xiaowine

import android.app.Application
import cn.fuckhome.xiaowine.utils.ModulePrefs

/**
 * Registers the modern Xposed service listener as early as possible so the framework can
 * hand this process the binder it uses for remote preferences.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ModulePrefs.init(this)
    }
}
