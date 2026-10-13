package cn.fuckhome.xiaowine.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.Gravity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cn.fuckhome.xiaowine.BuildConfig
import cn.fuckhome.xiaowine.R
import cn.fuckhome.xiaowine.ui.effect.AboutBackground
import cn.fuckhome.xiaowine.ui.effect.ColorBlendToken
import cn.fuckhome.xiaowine.ui.effect.logoBlendColors
import cn.fuckhome.xiaowine.utils.ActivityUtils
import cn.fuckhome.xiaowine.utils.ModulePrefs
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.window.WindowDialog
import java.text.SimpleDateFormat
import java.util.Locale

/** Broadcast consumed by MiuiHome to copy / delete the custom font. */
private const val MIUI_HOME_ACTION = "MIUIHOME_Server"

/** Above this width Miuix uses the pinned (small) top bar instead of the collapsing large one. */
private const val WIDE_SCREEN_DP = 600

/** Message surfaced to the user as a dialog. */
data class ScreenMessage(val text: String, val restart: Boolean = false)

/**
 * Settings destinations. A Kotlin `enum` is [java.io.Serializable], so it doubles as a saveable
 * [NavKey] without pulling in kotlinx.serialization.
 */
private enum class Page(val titleRes: Int) : NavKey {
    MAIN(R.string.AppName),
    INFO(R.string.AddInformation),
    STYLE(R.string.AddInformationStyle),
    UNRESTRICTED(R.string.FunModify),
    ABOUT(R.string.About),
}

private sealed interface MainDialog {
    data object Reset : MainDialog
}

private class TextDialogSpec(
    val titleRes: Int,
    val summaryRes: Int?,
    val initial: String,
    val errorRes: Int,
    val validate: (String) -> Boolean,
    val onAccept: (String) -> Unit,
)

@Composable
fun SettingsScreen(
    message: ScreenMessage?,
    onDismissMessage: () -> Unit,
    onRestartApp: () -> Unit,
    onPickFont: () -> Unit,
    onBackup: () -> Unit,
    onRecovery: () -> Unit,
    onRestartHome: () -> Unit,
    onHideLauncherIcon: (Boolean) -> Unit,
) {
    val preferences = remember { ModulePrefs.preferences() }
    val store = remember(preferences) { SettingsStore(preferences) }

    // Miuix navigation: the back stack drives the HyperOS slide transition, and NavDisplay only
    // claims the system back gesture while there is more than one entry — at the root the event
    // falls through to the system and the activity finishes, exactly like the platform.
    val backStack = remember { navBackStackOf(Page.MAIN) }
    val navigate: (Page) -> Unit = { page -> if (page !in backStack) backStack.add(page) }
    val goBack: () -> Unit = { if (backStack.size > 1) backStack.removeLastOrNull() }

    // Matches the Miuix / KernelSU setup: input is NOT blocked while a page animates, so a
    // transition can be interrupted and the edge swipe below tracks the finger 1:1.
    val effects = NavDisplayEffects(cornerClipRadius = rememberNavSystemCornerRadius())

    // The Miuix transition presets declare no dismiss direction, so the interactive back swipe has
    // to be enabled per entry (physical direction, mirrored for RTL).
    val swipeDismiss = if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
        NavSwipeDirection.RightToLeft
    } else {
        NavSwipeDirection.LeftToRight
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            onBack = goBack,
            transition = NavTransitions.MiuixDefault,
            effects = effects,
        ) {
            entry<Page>(swipeDismiss = swipeDismiss) { page ->
                when (page) {
                    Page.MAIN -> MainPage(
                        store = store,
                        onNavigate = navigate,
                        onBackup = onBackup,
                        onRecovery = onRecovery,
                        onRestartHome = onRestartHome,
                        onHideLauncherIcon = onHideLauncherIcon,
                    )

                    Page.INFO -> InfoPage(store = store, onBack = goBack)
                    Page.STYLE -> StylePage(store = store, onBack = goBack, onPickFont = onPickFont)
                    Page.UNRESTRICTED -> UnrestrictedPage(store = store, onBack = goBack)
                    Page.ABOUT -> AboutPage(onBack = goBack)
                }
            }
        }

        // Window-level dialog: usable from any page without an enclosing Scaffold.
        WindowDialog(
            show = message != null,
            title = stringResource(R.string.Tips),
            summary = message?.text,
            onDismissRequest = onDismissMessage,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (message?.restart == true) {
                    TextButton(
                        text = stringResource(R.string.Restart),
                        onClick = onRestartApp,
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(
                    text = stringResource(R.string.Ok),
                    onClick = onDismissMessage,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Pages
// ---------------------------------------------------------------------------

@Composable
private fun MainPage(
    store: SettingsStore,
    onNavigate: (Page) -> Unit,
    onBackup: () -> Unit,
    onRecovery: () -> Unit,
    onRestartHome: () -> Unit,
    onHideLauncherIcon: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<MainDialog?>(null) }

    PageScaffold(titleRes = R.string.AppName, onBack = null) { innerPadding, scrollBehavior ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().pageScroll(scrollBehavior),
            contentPadding = innerPadding,
            // pageScroll() already installs Miuix's MIUI overscroll, so the default factory
            // effect is switched off to avoid a doubled stretch.
            overscrollEffect = null,
        ) {
            item {
                Card(modifier = Modifier.cardPadding()) {
                    SwitchPreference(
                        title = stringResource(R.string.MainSwitch),
                        checked = store.bool("MainSwitch"),
                        onCheckedChange = { store.set("MainSwitch", it) },
                    )
                }
            }

            item {
                Card(modifier = Modifier.cardPadding()) {
                    ArrowPreference(
                        title = stringResource(R.string.AddInformation),
                        onClick = { onNavigate(Page.INFO) },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.AddInformationStyle),
                        onClick = { onNavigate(Page.STYLE) },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.FunModify),
                        onClick = { onNavigate(Page.UNRESTRICTED) },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.About),
                        onClick = { onNavigate(Page.ABOUT) },
                    )
                }
            }

            item {
                Card(modifier = Modifier.cardPadding()) {
                    SwitchPreference(
                        title = stringResource(R.string.HideDeskIcon),
                        checked = store.bool("hLauncherIcon"),
                        onCheckedChange = { hidden ->
                            store.set("hLauncherIcon", hidden)
                            onHideLauncherIcon(hidden)
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.DebugMode),
                        checked = store.bool("Debug"),
                        onCheckedChange = { store.set("Debug", it) },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.ResetModule),
                        onClick = { dialog = MainDialog.Reset },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.ReStartHome),
                        summary = stringResource(R.string.NeedsRoot),
                        onClick = onRestartHome,
                    )
                    ArrowPreference(
                        title = stringResource(R.string.Backup),
                        onClick = onBackup,
                    )
                    ArrowPreference(
                        title = stringResource(R.string.Recovery),
                        onClick = onRecovery,
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        ConfirmDialog(
            show = dialog is MainDialog.Reset,
            title = stringResource(R.string.ResetModuleDialog),
            summary = stringResource(R.string.ResetModuleDialogTips),
            confirmText = stringResource(R.string.Ok),
            cancelText = stringResource(R.string.Cancel),
            onConfirm = {
                store.clear()
                dialog = null
                ActivityUtils.showToastOnLooper(context, context.getString(R.string.ResetSuccess))
            },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun InfoPage(store: SettingsStore, onBack: () -> Unit) {
    PageScaffold(titleRes = R.string.AddInformation, onBack = onBack) { innerPadding, scrollBehavior ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().pageScroll(scrollBehavior),
            contentPadding = innerPadding,
            // pageScroll() already installs Miuix's MIUI overscroll, so the default factory
            // effect is switched off to avoid a doubled stretch.
            overscrollEffect = null,
        ) {
            item {
                Card(modifier = Modifier.cardPadding()) {
                    SwitchPreference(
                        title = stringResource(R.string.Memory),
                        checked = store.bool("MemoryView"),
                        onCheckedChange = { store.set("MemoryView", it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.Zarm),
                        checked = store.bool("ZarmView"),
                        onCheckedChange = { store.set("ZarmView", it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.Storage),
                        checked = store.bool("StorageView"),
                        onCheckedChange = { store.set("StorageView", it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.Uptime),
                        checked = store.bool("Uptime"),
                        onCheckedChange = { store.set("Uptime", it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.RunningAppTotal),
                        checked = store.bool("RunningAppTotal"),
                        onCheckedChange = { store.set("RunningAppTotal", it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.RunningServiceTotal),
                        checked = store.bool("RunningServiceTotal"),
                        onCheckedChange = { store.set("RunningServiceTotal", it) },
                    )
                }
            }

            item {
                Card(modifier = Modifier.cardPadding()) {
                    SwitchPreference(
                        title = stringResource(R.string.warning),
                        checked = store.bool("Warning"),
                        onCheckedChange = { store.set("Warning", it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.CleanMode),
                        checked = store.bool("CleanMode"),
                        onCheckedChange = { store.set("CleanMode", it) },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun StylePage(store: SettingsStore, onBack: () -> Unit, onPickFont: () -> Unit) {
    val context = LocalContext.current
    var textDialog by remember { mutableStateOf<TextDialogSpec?>(null) }
    var fontDialog by remember { mutableStateOf(false) }

    PageScaffold(titleRes = R.string.AddInformationStyle, onBack = onBack) { innerPadding, scrollBehavior ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().pageScroll(scrollBehavior),
            contentPadding = innerPadding,
            // pageScroll() already installs Miuix's MIUI overscroll, so the default factory
            // effect is switched off to avoid a doubled stretch.
            overscrollEffect = null,
        ) {
            item {
                val colorHint = stringResource(R.string.LyricColorTips)
                Card(modifier = Modifier.cardPadding()) {
                    ArrowPreference(
                        title = stringResource(R.string.Color),
                        summary = store.string("Color").ifEmpty { colorHint },
                        onClick = {
                            textDialog = TextDialogSpec(
                                titleRes = R.string.Color,
                                summaryRes = R.string.LyricColorTips,
                                initial = store.string("Color"),
                                errorRes = R.string.LyricColorError,
                                validate = { isValidColor(it) },
                                onAccept = { store.set("Color", it) },
                            )
                        },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.BackgroundColor),
                        summary = store.string("BgColor", "#00000000"),
                        onClick = {
                            textDialog = TextDialogSpec(
                                titleRes = R.string.BackgroundColor,
                                summaryRes = R.string.LyricColorTips,
                                initial = store.string("BgColor", "#00000000"),
                                errorRes = R.string.LyricColorError,
                                validate = { isValidColor(it) },
                                onAccept = { store.set("BgColor", it) },
                            )
                        },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.BgCorners),
                        summary = store.int("BgCorners", 30).toString(),
                        onClick = {
                            textDialog = TextDialogSpec(
                                titleRes = R.string.BgCorners,
                                summaryRes = R.string.BgCornersTips,
                                initial = store.int("BgCorners", 30).toString(),
                                errorRes = R.string.InputError,
                                validate = { it.toIntOrNull()?.let { value -> value in 0..100 } == true },
                                onAccept = { store.set("BgCorners", it.toInt()) },
                            )
                        },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.CustomFont),
                        onClick = { fontDialog = true },
                    )
                }
            }

            item {
                val gravityKeys = listOf(Gravity.START, Gravity.CENTER, Gravity.END)
                val gravityLabels = listOf(
                    stringResource(R.string.START),
                    stringResource(R.string.CENTER),
                    stringResource(R.string.END),
                )
                val unitLabels = listOf(
                    stringResource(R.string.Scale),
                    stringResource(R.string.Pixel),
                )
                val useScale = store.bool("Unit", true)

                Card(modifier = Modifier.cardPadding()) {
                    OverlayDropdownPreference(
                        title = stringResource(R.string.Gravity),
                        items = gravityLabels,
                        selectedIndex = gravityKeys.indexOf(store.int("Gravity", Gravity.START)).coerceAtLeast(0),
                        onSelectedIndexChange = { index -> store.set("Gravity", gravityKeys[index]) },
                    )
                    OverlayDropdownPreference(
                        title = stringResource(R.string.UnitMargin),
                        items = unitLabels,
                        selectedIndex = if (useScale) 0 else 1,
                        onSelectedIndexChange = { index -> store.set("Unit", index == 0) },
                    )
                }
            }

            item {
                Card(modifier = Modifier.cardPadding()) {
                    marginPreference(store, { textDialog = it }, "LeftMargin0", R.string.LeftMargin0, 0)
                    marginPreference(store, { textDialog = it }, "TopMargin0", R.string.TopMargin0, 4)
                    marginPreference(store, { textDialog = it }, "LeftMargin1", R.string.LeftMargin1, 0)
                    marginPreference(store, { textDialog = it }, "TopMargin1", R.string.TopMargin1, 4)
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        val spec = textDialog
        if (spec != null) {
            TextInputDialog(
                show = true,
                title = stringResource(spec.titleRes),
                summary = spec.summaryRes?.let { stringResource(it) },
                initial = spec.initial,
                errorMessage = stringResource(spec.errorRes),
                validate = spec.validate,
                onAccept = { value ->
                    spec.onAccept(value)
                    textDialog = null
                },
                onDismiss = { textDialog = null },
            )
        }

        FontDialog(
            show = fontDialog,
            onChoose = {
                fontDialog = false
                onPickFont()
            },
            onReset = {
                fontDialog = false
                context.sendBroadcast(Intent(MIUI_HOME_ACTION).apply { putExtra("Type", "delete_font") })
            },
            onDismiss = { fontDialog = false },
        )
    }
}

@Composable
private fun marginPreference(
    store: SettingsStore,
    onShow: (TextDialogSpec) -> Unit,
    key: String,
    titleRes: Int,
    default: Int,
) {
    ArrowPreference(
        title = stringResource(titleRes),
        summary = store.int(key, default).toString(),
        onClick = {
            onShow(
                TextDialogSpec(
                    titleRes = titleRes,
                    summaryRes = if (store.bool("Unit", true)) R.string.MarginTips1 else R.string.MarginTips2,
                    initial = store.int(key, default).toString(),
                    errorRes = R.string.InputError,
                    validate = { raw ->
                        val value = raw.toIntOrNull()
                        value != null && value in if (store.bool("Unit", true)) -100..100 else -2000..2000
                    },
                    onAccept = { raw -> store.set(key, raw.toInt()) },
                )
            )
        },
    )
}

@Composable
private fun UnrestrictedPage(store: SettingsStore, onBack: () -> Unit) {
    PageScaffold(titleRes = R.string.FunModify, onBack = onBack) { innerPadding, scrollBehavior ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().pageScroll(scrollBehavior),
            contentPadding = innerPadding,
            // pageScroll() already installs Miuix's MIUI overscroll, so the default factory
            // effect is switched off to avoid a doubled stretch.
            overscrollEffect = null,
        ) {
            item {
                Card(modifier = Modifier.cardPadding()) {
                    SwitchPreference(
                        title = stringResource(R.string.Pad),
                        checked = store.bool("Pad"),
                        onCheckedChange = { store.set("Pad", it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.Shortcuts),
                        checked = store.bool("Shortcuts"),
                        onCheckedChange = { store.set("Shortcuts", it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.UnlockGrids),
                        checked = store.bool("UnlockGrids"),
                        onCheckedChange = { store.set("UnlockGrids", it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.UnlockHotseat),
                        checked = store.bool("UnlockHotseat"),
                        onCheckedChange = { store.set("UnlockHotseat", it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.ShortcutSmallWindow),
                        checked = store.bool("ShortcutSmallWindow"),
                        onCheckedChange = { store.set("ShortcutSmallWindow", it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.HideStatusBar),
                        checked = store.bool("HideStatusBar"),
                        onCheckedChange = { store.set("HideStatusBar", it) },
                    )
                }
            }

            item {
                Card(modifier = Modifier.cardPadding()) {
                    SwitchPreference(
                        title = stringResource(R.string.HideAppName),
                        checked = store.bool("HideAppName"),
                        onCheckedChange = { store.set("HideAppName", it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.HideAppIcon),
                        checked = store.bool("HideAppIcon"),
                        onCheckedChange = { store.set("HideAppIcon", it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.HideSmallWindow),
                        checked = store.bool("HideSmallWindow"),
                        onCheckedChange = { store.set("HideSmallWindow", it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.RemoveSmallWindowRestriction),
                        checked = store.bool("RemoveSmallWindowRestriction"),
                        onCheckedChange = { store.set("RemoveSmallWindowRestriction", it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.UnlockNavType),
                        checked = store.bool("UnlockNavType"),
                        onCheckedChange = { store.set("UnlockNavType", it) },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

/**
 * The About page's logo block: app icon, name and version.
 *
 * Pinned behind the scrolling list — it does NOT move with the page. The list's content slides up
 * over it and each element fades out on its own schedule as it gets covered, which is exactly how
 * KernelSU's About screen behaves (its logo lives in the background layer, with a list spacer
 * reserving the same height).
 */
@Composable
private fun AboutHeader(
    progress: Float,
    contentPadding: PaddingValues,
    blur: AboutBlur,
    onHeightChanged: (Int) -> Unit,
) {
    Box(modifier = Modifier.padding(top = contentPadding.calculateTopPadding())) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { onHeightChanged(it.height) }
                .padding(top = ABOUT_HEADER_TOP_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        // The icon is the last to disappear.
                        val faded = 1f - fadeOut(progress, ABOUT_ICON_FADE_START, ABOUT_ICON_FADE_END)
                        alpha = 1f - faded
                        scaleX = 1f - faded * 0.05f
                        scaleY = 1f - faded * 0.05f
                    }
                    .size(96.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                // The launcher icon is an adaptive icon (white background + a sparse vector
                // foreground), so the foreground is drawn oversized inside a clipped white tile to
                // reproduce how the launcher renders it.
                Image(
                    painter = painterResource(R.drawable.ic_launcher_fore),
                    contentDescription = null,
                    modifier = Modifier.requiredSize(140.dp),
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.AppName),
                style = MiuixTheme.textStyles.title3,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier
                    .graphicsLayer {
                        val faded = 1f - fadeOut(progress, ABOUT_NAME_FADE_START, ABOUT_NAME_FADE_END)
                        alpha = 1f - faded
                        scaleX = 1f - faded * 0.05f
                        scaleY = 1f - faded * 0.05f
                    }
                    // Glass name, exactly as the Miuix reference app draws it: the blurred backdrop
                    // shows through the glyphs (DstIn masks it with the text's own alpha) using the
                    // dedicated logo blend recipe, not the card one. The icon is deliberately left
                    // alone — the reference does not treat it either.
                    .then(
                        if (blur.active) {
                            Modifier.textureBlur(
                                backdrop = blur.backdrop,
                                shape = RoundedCornerShape(16.dp),
                                blurRadius = ABOUT_NAME_BLUR_RADIUS,
                                colors = BlurColors(blendColors = blur.logoColors),
                                contentBlendMode = BlendMode.DstIn,
                                enabled = true,
                            )
                        } else {
                            Modifier
                        },
                    ),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.graphicsLayer {
                    val faded = 1f - fadeOut(progress, ABOUT_VERSION_FADE_START, ABOUT_VERSION_FADE_END)
                    alpha = 1f - faded
                    scaleX = 1f - faded * 0.05f
                    scaleY = 1f - faded * 0.05f
                },
            )
        }
    }
}

@Composable
private fun AboutPage(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val listState = rememberLazyListState()
    var headerHeightPx by remember { mutableIntStateOf(0) }

    // KernelSU-style header: the avatar block fades and shrinks as the list scrolls away.
    val headerProgress by remember {
        derivedStateOf {
            when {
                headerHeightPx <= 0 -> 0f
                listState.firstVisibleItemIndex > 0 -> 1f
                else -> (listState.firstVisibleItemScrollOffset.toFloat() / headerHeightPx).coerceIn(0f, 1f)
            }
        }
    }

    // Frosted glass, wired the way KernelSU's About screen does it: the animated light is recorded
    // into a backdrop, and the cards blur that backdrop instead of painting an opaque surface.
    val backdrop = rememberLayerBackdrop()
    val isDarkTheme = isSystemInDarkTheme()
    val aboutBlur = AboutBlur(
        backdrop = backdrop,
        colors = if (isDarkTheme) {
            ColorBlendToken.Overlay_Thin_Light
        } else {
            ColorBlendToken.Pured_Regular_Light
        },
        logoColors = logoBlendColors(isDarkTheme),
        active = isRuntimeShaderSupported(),
    )

    PageScaffold(
        titleRes = R.string.About,
        onBack = onBack,
        // KernelSU's arrangement: while the header is on screen the bar is fully transparent so
        // the animated light shows through, and it turns opaque once the header has scrolled away.
        // The title fades in over the same range instead of being painted over the light.
        barColor = if (headerProgress >= 1f) MiuixTheme.colorScheme.surface else Color.Transparent,
        titleAlpha = ((headerProgress - 0.35f) / 0.65f).coerceIn(0f, 1f),
        // No large collapsing title here: the app icon + name in the list ARE the title, exactly
        // like KernelSU's About screen. The small bar only shows the back arrow and, once scrolled,
        // the page name.
        forceSmallBar = true,
    ) { innerPadding, scrollBehavior ->
        // Full-page animated light behind the list, fading out as the header scrolls away.
        // PageScaffold stacks page content in a Box, so this is a sibling of the list.
        AboutBackground(
            progress = headerProgress,
            backdrop = backdrop,
            modifier = Modifier.fillMaxSize(),
        )
        // Pinned logo block, drawn before the list so the list scrolls over it.
        AboutHeader(
            progress = headerProgress,
            contentPadding = innerPadding,
            blur = aboutBlur,
            onHeightChanged = { headerHeightPx = it },
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().pageScroll(scrollBehavior),
            contentPadding = innerPadding,
            overscrollEffect = null,
        ) {
            // Reserves the height of the pinned [AboutHeader] above, so the first card starts below
            // the logo block and slides up over it as the page scrolls.
            item {
                Spacer(
                    modifier = Modifier.height(
                        maxOf(
                            with(LocalDensity.current) { headerHeightPx.toDp() },
                            ABOUT_HEADER_MIN_HEIGHT,
                        ),
                    ),
                )
            }

            item { SmallTitle(text = stringResource(R.string.Author)) }

            item {
                AboutCard(blur = aboutBlur, modifier = Modifier.cardPadding()) {
                    ArrowPreference(
                        title = "xiaowine",
                        summary = stringResource(R.string.OriginalAuthor),
                        startAction = { AuthorAvatar(R.drawable.avatar_xiaowine) },
                        onClick = { uriHandler.openUri("https://github.com/xiaowine") },
                    )
                    ArrowPreference(
                        title = "xiangfa-test",
                        summary = stringResource(R.string.Maintainer),
                        startAction = { AuthorAvatar(R.drawable.avatar_xiangfa) },
                        onClick = { uriHandler.openUri("https://github.com/xiangfa-test") },
                    )
                    ArrowPreference(
                        title = "Coolapk",
                        onClick = { uriHandler.openUri("https://www.coolapk.com/apk/cn.fuckhome.xiaowine") },
                    )
                }
            }

            item {
                val buildTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(BuildConfig.BUILD_TIME.toLong())
                AboutCard(blur = aboutBlur, modifier = Modifier.cardPadding()) {
                    BasicComponent(
                        title = stringResource(R.string.ModulePackName),
                        summary = BuildConfig.APPLICATION_ID,
                    )
                    BasicComponent(
                        title = stringResource(R.string.ModuleVersion),
                        summary = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})-${BuildConfig.BUILD_TYPE}",
                    )
                    BasicComponent(title = stringResource(R.string.BuildTime), summary = buildTime)
                }
            }

            item { SmallTitle(text = stringResource(R.string.ThkListTips)) }

            item {
                AboutCard(blur = aboutBlur, modifier = Modifier.cardPadding()) {
                    ArrowPreference(
                        title = "LSPosed",
                        onClick = { uriHandler.openUri("https://github.com/LSPosed/LSPosed") },
                    )
                    ArrowPreference(
                        title = "libxposed（现代 Xposed API）",
                        onClick = {
                            uriHandler.openUri("https://central.sonatype.com/artifact/io.github.libxposed/api")
                        },
                    )
                    ArrowPreference(
                        title = "Miuix（界面组件库）",
                        onClick = { uriHandler.openUri("https://github.com/compose-miuix-ui/miuix") },
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.XposedDesc),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                )
            }

            // Blank filler so the page always has enough scroll range to push the header (logo +
            // app name) completely off screen. Without it a short page cannot scroll at all, the
            // header never reaches progress 1, and the collapsed top bar is unreachable.
            // Sized from the measured header so it stays correct if the header changes.
            item {
                Spacer(
                    modifier = Modifier.height(
                        with(LocalDensity.current) { headerHeightPx.toDp() } + ABOUT_BOTTOM_FILLER,
                    ),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Page shell
// ---------------------------------------------------------------------------

/**
 * One page = one Scaffold + one large-title top bar, matching how the Miuix reference app builds
 * pages: the whole page (bar included) is what slides during a navigation transition, and the
 * large title collapses into the pinned bar as its list scrolls.
 */
@Composable
private fun PageScaffold(
    titleRes: Int,
    onBack: (() -> Unit)?,
    barColor: Color? = null,
    titleAlpha: Float = 1f,
    forceSmallBar: Boolean = false,
    content: @Composable (PaddingValues, ScrollBehavior) -> Unit,
) {
    val isWideScreen = LocalConfiguration.current.screenWidthDp >= WIDE_SCREEN_DP
    val scrollBehavior = MiuixScrollBehavior()
    // `null` keeps the stock opaque bar, so pages that do not draw behind the bar are unaffected.
    val resolvedBarColor = barColor ?: MiuixTheme.colorScheme.surface
    val resolvedTitleColor = MiuixTheme.colorScheme.onSurface.copy(alpha = titleAlpha)
    val navigationIcon: @Composable () -> Unit = {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = MiuixIcons.Back,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        }
    }

    Scaffold(
        topBar = {
            if (isWideScreen || forceSmallBar) {
                SmallTopAppBar(
                    title = stringResource(titleRes),
                    color = resolvedBarColor,
                    titleColor = resolvedTitleColor,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = navigationIcon,
                )
            } else {
                TopAppBar(
                    title = stringResource(titleRes),
                    color = resolvedBarColor,
                    titleColor = resolvedTitleColor,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = navigationIcon,
                )
            }
        },
    ) { innerPadding ->
        // A Box gives every page a single layout host, so pages may emit their dialogs as
        // additional children without overlapping the scrollable content.
        Box(modifier = Modifier.fillMaxSize()) {
            content(innerPadding, scrollBehavior)
        }
    }
}

private fun Modifier.pageScroll(scrollBehavior: ScrollBehavior): Modifier = this
    .overScrollVertical()
    .nestedScroll(scrollBehavior.nestedScrollConnection)

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------

@Composable
private fun TextInputDialog(
    show: Boolean,
    title: String,
    summary: String?,
    initial: String,
    errorMessage: String,
    validate: (String) -> Boolean,
    onAccept: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var text by remember(show, initial) { mutableStateOf(TextFieldValue(initial)) }

    OverlayDialog(
        show = show,
        title = title,
        summary = summary,
        onDismissRequest = onDismiss,
    ) {
        Column {
            TextField(
                value = text,
                onValueChange = { text = it },
                label = title,
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = stringResource(R.string.Cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.Ok),
                    onClick = {
                        val value = text.text
                        if (validate(value)) {
                            onAccept(value)
                        } else {
                            ActivityUtils.showToastOnLooper(context, errorMessage)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    show: Boolean,
    title: String,
    summary: String?,
    confirmText: String,
    cancelText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = title,
        summary = summary,
        onDismissRequest = onDismiss,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                text = cancelText,
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = confirmText,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FontDialog(
    show: Boolean,
    onChoose: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.CustomFont),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextButton(
                text = stringResource(R.string.ChooseFont),
                onClick = onChoose,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                text = stringResource(R.string.Reset),
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                text = stringResource(R.string.Cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Round avatar shown at the start of an author row. */
@Composable
private fun AuthorAvatar(drawableRes: Int) {
    Image(
        painter = painterResource(drawableRes),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .padding(end = 12.dp)
            .size(40.dp)
            .clip(CircleShape),
    )
}


/** Blur radius KernelSU uses for the About page cards. */
private const val ABOUT_BLUR_RADIUS = 60f

/** Distance between the top bar and the pinned About logo block. */
private val ABOUT_HEADER_TOP_PADDING = 32.dp

/** Height reserved for the logo block before it has been measured, so the cards never jump. */
private val ABOUT_HEADER_MIN_HEIGHT = 260.dp

// The logo block fades in stages — version first, then the name, then the icon — so it reads as
// being covered by the content sliding up over it. The windows are the Miuix reference's.
private const val ABOUT_VERSION_FADE_START = 0.05f
private const val ABOUT_VERSION_FADE_END = 0.20f
private const val ABOUT_NAME_FADE_START = 0.20f
private const val ABOUT_NAME_FADE_END = 0.35f
private const val ABOUT_ICON_FADE_START = 0.35f
private const val ABOUT_ICON_FADE_END = 0.50f

/** Blur radius the Miuix reference uses for the glass app name. */
private const val ABOUT_NAME_BLUR_RADIUS = 150f

/** 1 while [progress] is at or below [start], 0 at or above [end], linear in between. */
private fun fadeOut(progress: Float, start: Float, end: Float): Float =
    1f - ((progress - start) / (end - start)).coerceIn(0f, 1f)

/**
 * Extra blank space below the About page content, on top of one measured header height. Enough for
 * the header to scroll away on any phone-sized screen while not leaving a full empty screen behind.
 */
private val ABOUT_BOTTOM_FILLER = 200.dp

/**
 * What the About page's cards need to render as frosted glass over the animated light: the recorded
 * backdrop plus the colour-blend recipe, both taken from the Miuix reference implementation.
 */
private class AboutBlur(
    val backdrop: LayerBackdrop,
    val colors: List<BlendColorEntry>,
    val logoColors: List<BlendColorEntry>,
    val active: Boolean,
)

/**
 * Frosted card used on the About page: a transparent surface whose backdrop is the animated light,
 * blurred and colour-blended exactly as KernelSU's About screen does it. Falls back to the opaque
 * card colour when the effect is unavailable (below Android 15), so the content stays readable.
 */
@Composable
private fun AboutCard(
    blur: AboutBlur,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.then(
            if (blur.active) {
                Modifier.textureBlur(
                    backdrop = blur.backdrop,
                    shape = RoundedCornerShape(CardDefaults.CornerRadius),
                    blurRadius = ABOUT_BLUR_RADIUS,
                    colors = BlurColors(blendColors = blur.colors),
                )
            } else {
                Modifier
            },
        ),
        colors = CardDefaults.defaultColors(
            color = if (blur.active) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
            // The reference passes a transparent content colour: the components inside set their own
            // title/summary colours from the theme.
            contentColor = Color.Transparent,
        ),
        content = content,
    )
}

private fun Modifier.cardPadding(): Modifier = padding(horizontal = 12.dp, vertical = 6.dp)

private fun isValidColor(value: String): Boolean = runCatching {
    android.graphics.Color.parseColor(value)
    true
}.getOrDefault(false)
