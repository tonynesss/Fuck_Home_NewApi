package cn.fuckhome.xiaowine.hook.module.modify

import cn.fuckhome.xiaowine.R
import cn.fuckhome.xiaowine.hook.BaseHook
import cn.fuckhome.xiaowine.utils.LogUtils
import cn.fuckhome.xiaowine.utils.findMethod
import cn.fuckhome.xiaowine.utils.hookReturnConstant
import cn.fuckhome.xiaowine.utils.moduleRes

object RemoveSmallWindowRestriction3 : BaseHook() {
    override fun init() {
        LogUtils.i(moduleRes.getString(R.string.RemoveSmallWindowRestriction))
        findMethod("com.android.systemui.statusbar.notification.NotificationSettingsManager") { name == "canSlide" }.hookReturnConstant(true)
    }

}
