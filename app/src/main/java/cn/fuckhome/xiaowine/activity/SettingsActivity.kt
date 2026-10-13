package cn.fuckhome.xiaowine.activity

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import cn.fuckhome.xiaowine.BuildConfig
import cn.fuckhome.xiaowine.R
import cn.fuckhome.xiaowine.ui.ScreenMessage
import cn.fuckhome.xiaowine.ui.SettingsScreen
import cn.fuckhome.xiaowine.ui.theme.AppTheme
import cn.fuckhome.xiaowine.utils.ActivityUtils
import cn.fuckhome.xiaowine.utils.BackupUtils
import cn.fuckhome.xiaowine.utils.FileUtils
import cn.fuckhome.xiaowine.utils.ModulePrefs
import com.jaredrummler.ktsh.Shell
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/** How long the settings screen waits for the framework's service binder at start-up. */
private const val SERVICE_BIND_TIMEOUT_MS = 3000L

private const val MIUI_HOME_ACTION = "MIUIHOME_Server"
private const val MIUI_HOME_CALLBACK_ACTION = "MIUIHOME_App_Server"

class SettingsActivity : ComponentActivity() {

    private val message = mutableStateOf<ScreenMessage?>(null)

    private var appReceiver: BroadcastReceiver? = null

    private val openFont = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        val path = runCatching { FileUtils(this).getFilePathByUri(uri) }.getOrNull()
        if (path.isNullOrEmpty()) {
            ActivityUtils.showToastOnLooper(this, getString(R.string.CustomFontFail))
            return@registerForActivityResult
        }
        sendBroadcast(
            Intent(MIUI_HOME_ACTION).apply {
                putExtra("Type", "copy_font")
                putExtra("Font_Path", path)
            }
        )
    }

    private val createBackup =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri ?: return@registerForActivityResult
            val error = BackupUtils.write(this, uri, ModulePrefs.preferences())
            ActivityUtils.showToastOnLooper(this, error ?: "save ok")
        }

    private val openBackup =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            val error = BackupUtils.read(this, uri, ModulePrefs.preferences())
            ActivityUtils.showToastOnLooper(this, error ?: "load ok")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyEdgeToEdge()
        seedDebugDefaults()
        registerAppReceiver()
        setContent {
            AppTheme {
                SettingsScreen(
                    message = message.value,
                    onDismissMessage = { message.value = null },
                    onRestartApp = ::restartApp,
                    onPickFont = { openFont.launch(arrayOf("*/*")) },
                    onBackup = { createBackup.launch(defaultBackupName()) },
                    onRecovery = { openBackup.launch(arrayOf("application/json")) },
                    onRestartHome = ::restartHome,
                    onHideLauncherIcon = ::setLauncherIconHidden,
                )
            }
        }
        verifyFrameworkAsync()
    }

    /**
     * Draws the UI behind the status bar and the gesture bar ("小白条") with fully transparent
     * system bars, so the Miuix surface shows through and the layout reads as immersive.
     *
     * `SystemBarStyle.auto` picks the light/dark icon tint from the configuration, which is
     * exactly what [cn.fuckhome.xiaowine.ui.theme.AppTheme] follows, so no recomposition-time
     * effect is needed. HyperOS enforces a translucent navigation-bar scrim by default; it is
     * turned off explicitly (the same workaround KernelSU and Miuix use).
     */
    private fun applyEdgeToEdge() {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    override fun onDestroy() {
        appReceiver?.let { runCatching { unregisterReceiver(it) } }
        appReceiver = null
        super.onDestroy()
    }

    /**
     * The framework hands its service binder to this process right after it starts. When the
     * binder never arrives the module is not active, so the settings could never reach the
     * launcher; the user is told instead of silently losing their changes.
     */
    private fun verifyFrameworkAsync() {
        Thread {
            if (!ModulePrefs.awaitService(SERVICE_BIND_TIMEOUT_MS) && !isFinishing) {
                runOnUiThread {
                    if (!isFinishing) {
                        message.value = ScreenMessage(getString(R.string.NotSupport), restart = true)
                    }
                }
            }
        }.start()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerAppReceiver() {
        if (appReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val text = when (intent.getStringExtra("app_Type")) {
                    "CopyFont" -> if (intent.getBooleanExtra("CopyFont", false)) {
                        getString(R.string.CustomFontSuccess)
                    } else {
                        getString(R.string.CustomFontFail) + "\n" + intent.getStringExtra("font_error")
                    }

                    "DeleteFont" -> if (intent.getBooleanExtra("DeleteFont", false)) {
                        getString(R.string.DeleteFontSuccess)
                    } else {
                        getString(R.string.DeleteFontFail)
                    }

                    else -> null
                }
                if (text != null) message.value = ScreenMessage(text)
            }
        }
        val filter = IntentFilter(MIUI_HOME_CALLBACK_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        appReceiver = receiver
    }

    /** Pre-fills a few switches in debug builds so the recents panel is easy to test. */
    private fun seedDebugDefaults() {
        if (!BuildConfig.DEBUG) return
        ModulePrefs.preferences().edit()
            .putBoolean("MemoryView", true)
            .putBoolean("ZarmView", true)
            .putBoolean("MainSwitch", true)
            .putBoolean("Debug", true)
            .apply()
    }

    private fun setLauncherIconHidden(hidden: Boolean) {
        packageManager.setComponentEnabledSetting(
            ComponentName(this, "$packageName.launcher"),
            if (hidden) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    }

    /**
     * Restarting the launcher needs root: MiuiHome lives in its own process and a module app cannot
     * stop another app without it.
     *
     * `su` may be missing or denied; [com.jaredrummler.ktsh.Shell] throws
     * `Shell.NotFoundException` from its constructor in that case. That used to escape this worker
     * thread and kill the process, so the outcome is always captured and reported instead.
     */
    private fun restartHome() {
        Thread {
            val succeeded = runCatching { Shell("su").run("am force-stop com.miui.home").isSuccess }
                .getOrDefault(false)
            if (!succeeded && !isFinishing) {
                runOnUiThread {
                    if (!isFinishing) message.value = ScreenMessage(getString(R.string.RootRequired))
                }
            }
        }.start()
    }

    private fun restartApp() {
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(it)
        }
        exitProcess(0)
    }

    private fun defaultBackupName(): String =
        "Fuck_Home_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())}.json"
}
