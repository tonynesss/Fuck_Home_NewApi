package cn.fuckhome.xiaowine.hook.module.modify

import cn.fuckhome.xiaowine.R
import cn.fuckhome.xiaowine.hook.BaseHook
import cn.fuckhome.xiaowine.utils.LogUtils
import cn.fuckhome.xiaowine.utils.Utils
import cn.fuckhome.xiaowine.utils.callMethod
import cn.fuckhome.xiaowine.utils.findMethod
import cn.fuckhome.xiaowine.utils.hookAfter
import cn.fuckhome.xiaowine.utils.moduleRes

object ShortcutItemCount : BaseHook() {
    override fun init() {
        Utils.catchNoClass {
            LogUtils.i(moduleRes.getString(R.string.Shortcuts))
            findMethod("com.miui.home.launcher.shortcuts.AppShortcutMenu") { name == "getMaxCountInCurrentOrientation" }.hookAfter { it.result = 20 }
            findMethod("com.miui.home.launcher.shortcuts.AppShortcutMenu") { name == "getMaxShortcutItemCount" }.hookAfter { it.result = 20 }
            findMethod("com.miui.home.launcher.shortcuts.AppShortcutMenu") { name == "getMaxVisualHeight" }.hookAfter { it.result = it.thisObject.callMethod("getItemHeight") }
        }
    }
}