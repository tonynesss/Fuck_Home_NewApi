package cn.fuckhome.xiaowine.hook.module.modify

import android.view.View
import android.widget.TextView
import cn.fuckhome.xiaowine.R
import cn.fuckhome.xiaowine.hook.BaseHook
import cn.fuckhome.xiaowine.utils.LogUtils
import cn.fuckhome.xiaowine.utils.Utils
import cn.fuckhome.xiaowine.utils.getObjectField
import cn.fuckhome.xiaowine.utils.hookAfter
import cn.fuckhome.xiaowine.utils.findMethod
import cn.fuckhome.xiaowine.utils.moduleRes

object HideSmallWindow: BaseHook() {
    override fun init() {
        Utils.catchNoClass {
            findMethod("com.miui.home.recents.views.RecentsContainer") { name == "onFinishInflate" }.hookAfter {
                LogUtils.i(moduleRes.getString(R.string.HideSmallWindow))
                val mTitle = it.thisObject.getObjectField("mTxtSmallWindow") as TextView
                mTitle.visibility = View.GONE
            }
        }
    }
}