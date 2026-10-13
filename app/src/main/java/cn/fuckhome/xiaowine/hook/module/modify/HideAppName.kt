package cn.fuckhome.xiaowine.hook.module.modify

import cn.fuckhome.xiaowine.R
import cn.fuckhome.xiaowine.hook.BaseHook
import cn.fuckhome.xiaowine.utils.LogUtils
import cn.fuckhome.xiaowine.utils.Utils
import cn.fuckhome.xiaowine.utils.findMethod
import cn.fuckhome.xiaowine.utils.hookBefore
import cn.fuckhome.xiaowine.utils.moduleRes

object HideAppName : BaseHook() {
    override fun init() {
        Utils.catchNoClass {
            findMethod("com.miui.home.launcher.ItemIcon") { name == "setTitle" }.hookBefore {
                LogUtils.i(moduleRes.getString(R.string.HideAppName))
                it.result = null
            }
        }
    }
}