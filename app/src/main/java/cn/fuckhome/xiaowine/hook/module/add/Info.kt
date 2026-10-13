@file:Suppress("DEPRECATION")

package cn.fuckhome.xiaowine.hook.module.add

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import cn.fuckhome.xiaowine.R
import cn.fuckhome.xiaowine.hook.BaseHook
import cn.fuckhome.xiaowine.utils.FileUtils
import cn.fuckhome.xiaowine.utils.LogUtils
import cn.fuckhome.xiaowine.utils.MemoryUtils
import cn.fuckhome.xiaowine.utils.Utils
import cn.fuckhome.xiaowine.utils.Utils.XConfig
import cn.fuckhome.xiaowine.utils.Utils.formatSize
import cn.fuckhome.xiaowine.utils.Utils.isNull
import cn.fuckhome.xiaowine.utils.appContext
import cn.fuckhome.xiaowine.utils.findConstructor
import cn.fuckhome.xiaowine.utils.findMethod
import cn.fuckhome.xiaowine.utils.getObjectAs
import cn.fuckhome.xiaowine.utils.hookAfter
import cn.fuckhome.xiaowine.utils.moduleRes
import cn.fuckhome.xiaowine.utils.putObject
import java.io.File
import java.util.*
import kotlin.math.roundToInt
import kotlin.system.exitProcess


@SuppressLint("StaticFieldLeak")
object Info : BaseHook() {
    lateinit var textColors: ColorStateList
    private var TextViewMaps = LinkedHashMap<String, TextView>()
    private var TextViewList = arrayListOf<String>()
    private lateinit var mLinearLayout: LinearLayout

    private val metrics = appContext.resources.displayMetrics
    private val widthPixels = metrics.widthPixels
    private val heightPixels = metrics.heightPixels
    private var topMargin = 0
    private var leftMargin = 0

    const val threshold = 20

    private val moduleReceiver by lazy { ModuleReceiver() }

    private var timer: Timer? = null
    private var timerQueue: ArrayList<TimerTask> = arrayListOf()
    private var infoTimer: TimerTask? = null


    private val handler by lazy { Handler(Looper.getMainLooper()) }

    /** Optional information rows, in display order. */
    private val infoKeys = listOf(
        "MemoryView", "ZarmView", "StorageView", "Uptime", "RunningAppTotal", "RunningServiceTotal",
    )

    /** Signature of the last applied style, so the one-second tick can skip unchanged work. */
    private var appliedStyle: String? = null

    /** Last rotation reported by the recents container (0 = portrait). */
    private var lastRotation = 0

    /**
     * Re-applies the configured style to the views that already exist.
     *
     * LSPosed pushes remote-preference changes into the hooked process, so re-reading the values is
     * enough to pick up a settings edit — no MiuiHome restart needed. This runs when the recents
     * panel opens and on every one-second data tick, so a style change shows up immediately while
     * the panel is open, or the next time it is opened.
     */
    private fun applyStyleIfChanged() {
        if (!::mLinearLayout.isInitialized) return

        val enabled = infoKeys.filter { XConfig.getBoolean(it) }
        val signature = buildString {
            append(XConfig.getBgColor()).append('|')
            append(XConfig.getBgCorners()).append('|')
            append(XConfig.getGravity()).append('|')
            append(XConfig.getColor()).append('|')
            append(XConfig.getBoolean("Warning")).append('|')
            append(XConfig.getBoolean("CleanMode")).append('|')
            append(XConfig.getBoolean("Unit")).append('|')
            append(XConfig.getInt("TopMargin0", 4)).append('|')
            append(XConfig.getInt("LeftMargin0")).append('|')
            append(XConfig.getInt("TopMargin1", 5)).append('|')
            append(XConfig.getInt("LeftMargin1")).append('|')
            append(enabled.joinToString(","))
        }
        if (signature == appliedStyle) return
        appliedStyle = signature

        syncInfoViews(enabled)

        mLinearLayout.background = GradientDrawable().apply {
            setColor(Color.parseColor(XConfig.getBgColor()))
            cornerRadius = XConfig.getBgCorners().toFloat()
            // The original passed the (not yet laid out) view width here, i.e. a zero-width stroke:
            // no border. Kept as "no border" explicitly so re-applying after layout cannot draw one.
        }

        TextViewMaps.values.forEach { view ->
            view.gravity = XConfig.getGravity()
            view.textSize = 12f
            view.marqueeRepeatLimit = -1
            view.isSingleLine = true
            view.maxLines = 1
            Utils.viewColor(view, null)
        }

        updateMargins()
    }

    /** Adds / removes information rows so the visible set matches the configuration. */
    private fun syncInfoViews(enabled: List<String>) {
        if (TextViewMaps.keys.toList() == enabled) return
        mLinearLayout.removeAllViews()
        TextViewMaps.clear()
        enabled.forEach { name ->
            val view = TextView(appContext).apply {
                textSize = 12f
                marqueeRepeatLimit = -1
                isSingleLine = true
                maxLines = 1
            }
            mLinearLayout.addView(view)
            TextViewMaps[name] = view
        }
    }

    /** Recomputes the margins for the last known rotation from the current configuration. */
    private fun updateMargins() {
        if (!::mLinearLayout.isInitialized) return
        val useScale = XConfig.getUnit()
        if (lastRotation == 0) {
            if (useScale) {
                topMargin = (10 + XConfig.getInt("TopMargin0", 4) / 100.0 * heightPixels).toInt()
                leftMargin = (10 + XConfig.getInt("LeftMargin0") / 100.0 * widthPixels).roundToInt()
            } else {
                topMargin = 10 + XConfig.getInt("TopMargin0", 4)
                leftMargin = 10 + XConfig.getInt("LeftMargin0")
            }
        } else {
            if (useScale) {
                topMargin = (10 + XConfig.getInt("TopMargin1", 5) / 100.0 * widthPixels).roundToInt()
                leftMargin = (10 + XConfig.getInt("LeftMargin1") / 100.0 * heightPixels).roundToInt()
            } else {
                topMargin = 10 + XConfig.getInt("TopMargin1", 5)
                leftMargin = 10 + XConfig.getInt("LeftMargin1")
            }
        }

        mLinearLayout.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            this.topMargin = this@Info.topMargin
            this.leftMargin = this@Info.leftMargin
        }
    }

    override fun init() {
//        初始化根控件
        Utils.catchNoClass {
            findConstructor("com.miui.home.recents.views.RecentsContainer") { parameterCount == 2 }.hookAfter {
                LogUtils.i(moduleRes.getString(R.string.InitRootView))
                val mView = it.thisObject as FrameLayout
                mLinearLayout = LinearLayout(appContext).apply {
                    orientation = LinearLayout.VERTICAL
                    if (XConfig.getBoolean("optimizeAnimation")) {
                        visibility = View.GONE
                    }
                    val gd = GradientDrawable()
                    gd.setColor(Color.parseColor(XConfig.getBgColor()))
//                    gd.setColor(Color.BLUE)
                    gd.cornerRadius = XConfig.getBgCorners().toFloat()
                    gd.setStroke(width, Color.BLACK)
                    background = gd
                }

                mView.addView(mLinearLayout)
                listOf("MemoryView", "ZarmView", "StorageView", "Uptime", "RunningAppTotal", "RunningServiceTotal").forEach { s ->
                    if (XConfig.getBoolean(s)) {
                        TextViewList.add(s)
                    }
                }
            }
        }

//        初始化添加控件
        Utils.catchNoClass {
            findMethod("com.miui.home.recents.views.RecentsContainer") { name == "onFinishInflate" }.hookAfter {
                LogUtils.i(moduleRes.getString(R.string.InitAddView))
                val mTxtMemoryViewGroup = it.thisObject.getObjectAs<ViewGroup>("mTxtMemoryContainer")
                it.thisObject.putObject("mSeparatorForMemoryInfo", View(appContext))
                for (i in 0 until mTxtMemoryViewGroup.childCount) {
                    mTxtMemoryViewGroup.getChildAt(i).visibility = View.GONE
                }
                val mTxtMemoryInfo1 = it.thisObject.getObjectAs<TextView>("mTxtMemoryInfo1")
                textColors = mTxtMemoryInfo1.textColors

                if (TextViewMaps.size != 0) {
                    try {
                        TextViewMaps.forEach { its ->
                            TextViewMaps.remove(its.key)
                        }
                    } catch (_: ConcurrentModificationException) {
                        exitProcess(0)
                    }
                }
                TextViewMaps.apply {
                    TextViewList.forEach { name ->
                        val view = TextView(appContext).apply {
//                            setBackgroundColor(Color.parseColor(XConfig.getBgColor()))
                            Utils.viewColor(this, null)
                            gravity = XConfig.getGravity()
                            textSize = 12f
                            marqueeRepeatLimit = -1
                            isSingleLine = true
                            maxLines = 1
                        }
                        mLinearLayout.addView(view)
                        this[name] = view
                    }
                }

            }
        }

//        调节边距
        Utils.catchNoClass {
            findMethod("com.miui.home.recents.views.RecentsContainer") { name == "updateRotation" }.hookAfter {
                lastRotation = it.args[0] as Int
                updateMargins()
            }
        }


//        动态隐藏以优化动画 刷新数据
        Utils.catchNoClass {
            findMethod("com.miui.home.recents.views.RecentsContainer") { name == "startRecentsContainerFadeInAnim" }.hookAfter {
                LogUtils.i(moduleRes.getString(R.string.VisibleView))
                // Pick up any settings change before the panel becomes visible.
                applyStyleIfChanged()
                LogUtils.i(moduleRes.getString(R.string.UpdateView))

                val animation = AlphaAnimation(0f, 1f)
                animation.duration = 300
                animation.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation) {}
                    override fun onAnimationRepeat(animation: Animation) {}
                    override fun onAnimationEnd(animation: Animation) {
                        mLinearLayout.clearAnimation()
                    }
                })
                mLinearLayout.startAnimation(animation)
                mLinearLayout.visibility = View.VISIBLE

                startTimer()
            }
        }

        Utils.catchNoClass {
            findMethod("com.miui.home.recents.views.RecentsContainer") { name == "startRecentsContainerFadeOutAnim" }.hookAfter {
                LogUtils.i(moduleRes.getString(R.string.GoneView))
                stopTimer()
                if (mLinearLayout.visibility != View.GONE) {
                    val animation = AlphaAnimation(1f, 0f)
                    animation.duration = 300
                    animation.setAnimationListener(object : Animation.AnimationListener {
                        override fun onAnimationStart(animation: Animation) {}
                        override fun onAnimationRepeat(animation: Animation) {}
                        override fun onAnimationEnd(animation: Animation) {
                            mLinearLayout.clearAnimation()
                        }
                    })
                    mLinearLayout.startAnimation(animation)
                    mLinearLayout.visibility = View.GONE
                }
            }
        }


//        广播
        Utils.catchNoClass { appContext.unregisterReceiver(moduleReceiver) }
        appContext.registerReceiver(moduleReceiver, IntentFilter().apply { addAction("MIUIHOME_Server") })
    }

    fun updateInfoDate() {
        LogUtils.i("更新数据")
        handler.post {
            // Live style: the tick also re-applies a changed configuration to the existing views.
            applyStyleIfChanged()
            val memoryInfo = MemoryUtils().getMemoryInfo(appContext)
            val swapInfo = MemoryUtils().getPartitionInfo("SwapTotal", "SwapFree")
            val storageInfo = MemoryUtils().getStorageInfo(Environment.getExternalStorageDirectory())
            TextViewMaps.forEach { (name, view) ->
                when (name) {
                    "MemoryView" -> {
                        view.text = moduleRes.getString(if (XConfig.getBoolean("CleanMode")) R.string.MemoryView2 else R.string.MemoryView).format(memoryInfo.availMem.formatSize(), memoryInfo.totalMem.formatSize(), memoryInfo.percentValue)
                        Utils.viewColor(view, memoryInfo)
                    }

                    "ZarmView" -> {
                        view.text = moduleRes.getString(if (XConfig.getBoolean("CleanMode")) R.string.ZarmView2 else R.string.ZarmView).format(swapInfo.availMem.formatSize(), swapInfo.totalMem.formatSize(), swapInfo.percentValue)
                        Utils.viewColor(view, swapInfo)
                    }

                    "StorageView" -> {
                        view.text = moduleRes.getString(if (XConfig.getBoolean("CleanMode")) R.string.StorageView2 else R.string.StorageView).format(storageInfo.availMem.formatSize(), storageInfo.totalMem.formatSize(), storageInfo.percentValue)
                        Utils.viewColor(view, storageInfo)
                    }

                    "Uptime" -> {

                        view.text = moduleRes.getString(R.string.BootTimeView).format(Utils.Uptime.get())
                    }

                    "RunningAppTotal" -> {
                        view.text = moduleRes.getString(R.string.RunningAppTotalView).format((appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses.size)
                    }

                    "RunningServiceTotal" -> {
                        view.text = moduleRes.getString(R.string.RunningServiceTotalView).format((appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getRunningServices(999).size)
                    }

                }
                view.width = view.paint.measureText(view.text.toString()).toInt() + 40
            }
        }
    }

    private fun getInfoTimer(): TimerTask {
        if (infoTimer == null) {
            infoTimer = object : TimerTask() {
                override fun run() {
                    updateInfoDate()
                }
            }
        }
        return infoTimer as TimerTask
    }


    private fun startTimer() {
        val timerTask = getInfoTimer()
        timerQueue.forEach { task -> if (task == timerTask) return }
        timerQueue.add(timerTask)
        if (timer.isNull()) timer = Timer()
        timer?.schedule(timerTask, 0, 1000L)
    }

    private fun stopTimer() {
        timerQueue.forEach { task -> task.cancel() }
        infoTimer = null
        timerQueue = arrayListOf()
        timer?.cancel()
        timer = null
    }


    class ModuleReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Utils.catchNoClass {
                when (intent.getStringExtra("Type")) {
                    "copy_font" -> {
                        LogUtils.i(moduleRes.getString(R.string.CustomFont))
                        val path = intent.getStringExtra("Font_Path")
                        if (path.isNullOrEmpty()) return@catchNoClass
                        val file = File(appContext.filesDir.path + "/font")
                        if (file.exists() && file.canWrite()) {
                            file.delete()
                        }
                        val error = FileUtils(appContext).copyFile(File(path), appContext.filesDir.path, "font")
                        if (error.isEmpty()) {
                            TextViewMaps.forEach { (_, view) ->
                                view.typeface = Typeface.createFromFile(appContext.filesDir.path + "/font")
                            }
                            LogUtils.i(moduleRes.getString(R.string.CustomFontSuccess))
                            appContext.sendBroadcast(Intent().apply {
                                action = "MIUIHOME_App_Server"
                                putExtra("app_Type", "CopyFont")
                                putExtra("CopyFont", true)
                            })
                        } else {
                            runCatching {
                                val file1 = File(appContext.filesDir.path + "/font")
                                if (file1.exists() && file1.canWrite()) {
                                    file1.delete()
                                    LogUtils.i(moduleRes.getString(R.string.CustomFontFail))
                                }
                            }
                            appContext.sendBroadcast(Intent().apply {
                                action = "MIUIHOME_App_Server"
                                putExtra("app_Type", "CopyFont")
                                putExtra("font_error", error)
                            })
                        }
                    }

                    "delete_font" -> {
                        LogUtils.i(moduleRes.getString(R.string.DeleteFont))
                        var isOK = false
                        val file = File(appContext.filesDir.path + "/font")
                        if (file.exists() && file.canWrite()) {
                            isOK = file.delete()
                        }
                        TextViewMaps.forEach { (_, view) ->
                            view.typeface = Typeface.createFromFile("")
                        }
                        appContext.sendBroadcast(Intent().apply {
                            action = "MIUIHOME_App_Server"
                            putExtra("app_Type", "DeleteFont")
                            putExtra("DeleteFont", isOK)
                        })
                    }
                }
            }
        }
    }
}