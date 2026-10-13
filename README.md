### MIUI桌面增强模块

这个是基于原版所制作的新版本LSPosedApi适配版本

把最近任务（Recents）里的内存信息换成真实数值，并追加一些信息；同时提供一组 MIUI 桌面 / 小窗限制解除开关（注：该类内容不维护）

- 基于现代 Xposed API（`io.github.libxposed` API 102）重新构建
- 需要 LSPosed（API 102+）与 Android 13+
- 作用域：`com.miui.home`、`com.mi.android.globallauncher`、`com.android.systemui`、`android`

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

UI 库来自 [Miuix](https://github.com/compose-miuix-ui/miuix)（Apache-2.0）。

原作者 [xiaowine](https://github.com/xiaowine)，维护者 [xiangfa-test](https://github.com/xiangfa-test)。
