package cn.fuckhome.xiaowine.hook.module.modify

import cn.fuckhome.xiaowine.hook.BaseHook
import cn.fuckhome.xiaowine.utils.Utils
import cn.fuckhome.xiaowine.utils.findMethod
import cn.fuckhome.xiaowine.utils.hookBefore

object HideAppIcon:BaseHook() {
    override fun init() {
        Utils.catchNoClass {
            findMethod("com.miui.home.launcher.ItemIcon") { name == "setIconImageView" }.hookBefore {
                it.result = null
            }
        }
    }
}