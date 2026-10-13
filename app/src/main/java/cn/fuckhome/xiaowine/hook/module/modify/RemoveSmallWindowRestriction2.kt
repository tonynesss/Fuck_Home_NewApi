package cn.fuckhome.xiaowine.hook.module.modify

import cn.fuckhome.xiaowine.R
import cn.fuckhome.xiaowine.hook.BaseHook
import cn.fuckhome.xiaowine.utils.LogUtils
import cn.fuckhome.xiaowine.utils.findAllMethods
import cn.fuckhome.xiaowine.utils.hookReturnConstant
import cn.fuckhome.xiaowine.utils.moduleRes

object RemoveSmallWindowRestriction2 : BaseHook() {
    override fun init() {
        LogUtils.i(moduleRes.getString(R.string.RemoveSmallWindowRestriction))
        findAllMethods("com.miui.home.launcher.RecentsAndFSGestureUtils") { name == "canTaskEnterSmallWindow" }.hookReturnConstant(true)
        findAllMethods("com.miui.home.launcher.RecentsAndFSGestureUtils") { name == "canTaskEnterMiniSmallWindow" }.hookReturnConstant(true)
    }

}
