package cn.fuckhome.xiaowine.hook.module.modify

import cn.fuckhome.xiaowine.hook.BaseHook
import cn.fuckhome.xiaowine.utils.Utils
import cn.fuckhome.xiaowine.utils.findMethod
import cn.fuckhome.xiaowine.utils.hookBefore


object UnlockNavType : BaseHook() {
    override fun init() {
        Utils.catchNoClass {
            findMethod("com.miui.home.launcher.DeviceConfig") { name == "isShowSystemNavTypePreferenceInMiuiSettings" }.hookBefore {
                it.result = true
            }
        }
    }
}