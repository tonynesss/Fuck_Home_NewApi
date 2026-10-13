package cn.fuckhome.xiaowine.hook.module.modify

import cn.fuckhome.xiaowine.R
import cn.fuckhome.xiaowine.hook.BaseHook
import cn.fuckhome.xiaowine.utils.LogUtils
import cn.fuckhome.xiaowine.utils.Utils
import cn.fuckhome.xiaowine.utils.findMethod
import cn.fuckhome.xiaowine.utils.hookBefore
import cn.fuckhome.xiaowine.utils.moduleRes

object UnlockGrids : BaseHook() {
    override fun init() {
        Utils.catchNoClass {
            LogUtils.i(moduleRes.getString(R.string.UnlockGrids))
            findMethod("com.miui.home.launcher.DeviceConfig") { name == "getHotseatMaxCount" }.hookBefore { it.result = 15 }
        }
    }
}