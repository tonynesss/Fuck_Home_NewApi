package cn.fuckhome.xiaowine.hook.module.modify

import android.content.Context
import cn.fuckhome.xiaowine.R
import cn.fuckhome.xiaowine.hook.BaseHook
import cn.fuckhome.xiaowine.utils.LogUtils
import cn.fuckhome.xiaowine.utils.findMethod
import cn.fuckhome.xiaowine.utils.hookAfter
import cn.fuckhome.xiaowine.utils.hookReturnConstant
import cn.fuckhome.xiaowine.utils.moduleRes

object RemoveSmallWindowRestriction1 : BaseHook() {
    override fun init() {
        LogUtils.i(moduleRes.getString(R.string.RemoveSmallWindowRestriction))
        findMethod("com.android.server.wm.Task") { name == "isResizeable" }.hookReturnConstant(true)
        findMethod("android.util.MiuiMultiWindowAdapter") { name == "getFreeformBlackList" }.hookAfter { it.result = (it.result as MutableList<*>).apply { clear() } }
        findMethod("android.util.MiuiMultiWindowAdapter") { name == "getFreeformBlackListFromCloud" && parameterTypes[0] == Context::class.java }.hookAfter { it.result = (it.result as MutableList<*>).apply { clear() } }
        findMethod("android.util.MiuiMultiWindowUtils") { name == "supportFreeform" }.hookReturnConstant(true)

    }

}
