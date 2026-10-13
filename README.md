### MIUI桌面增强模块

这个是基于原版所制作的新版本LSPosedApi适配版本

把最近任务（Recents）里的内存信息换成真实数值，并追加一些信息；同时提供一组 MIUI 桌面 / 小窗限制解除开关。

- **Xposed API**：现代 `io.github.libxposed`（API 102）。只支持旧 `de.robv.android.xposed` 的框架**不再兼容**。
- **要求**：LSPosed（API 102+）、**Android 13+**（minSdk 33）。
- **界面**：Jetpack Compose + [Miuix](https://github.com/compose-miuix-ui/miuix)（HyperOS 风格），关于页带动态光效与毛玻璃。
- **作用域**：`com.miui.home`、`com.mi.android.globallauncher`、`com.android.systemui`、`android`。
- **配置**：走框架的 Remote Preferences，旧配置文件可直接沿用。

#### 构建

```bash
./gradlew assembleDebug      # 调试包
./gradlew assembleRelease    # 发布包（R8 + 资源压缩）
```

Android Studio 直接打开工程根目录即可（`local.properties` 里的 `sdk.dir` 改成自己的 SDK 路径）。
发布包默认不签名：复制 `keystore.properties.example` 为 `keystore.properties` 并填好四项即可，
也可以用 `KEYSTORE_PATH` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` 环境变量。

> 本分支由维护者使用自己的密钥签名，**与原版包不同**，因此从原版升级需先卸载（会丢配置，建议先备份）。

#### 工具链

Gradle 9.8.1 · AGP 9.4.1 · Kotlin 2.4.21 · compileSdk 37 · minSdk 33 · JDK 21

#### 第三方代码

关于页的光效与模糊取自 [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix) 示例工程（**Apache-2.0**），
文件头保留了原始版权与 SPDX 标识，分发时需一并保留并附上许可证副本。

**致谢**：[xiaowine](https://github.com/xiaowine)（原作者）· [xiangfa-test](https://github.com/xiangfa-test)（维护者）

---

<details>
<summary>开发与签名细节（点开查看）</summary>

#### 自定义签名

**方式一：`keystore.properties`（推荐）**

1. 生成 keystore（只做一次，**务必备份**，丢了就没法覆盖升级）：

   ```bash
   keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 \
           -alias fuckhome -storetype PKCS12
   ```

2. 复制 `keystore.properties.example` 为仓库根目录下的 `keystore.properties`：

   ```properties
   storeFile=release.jks        # 相对路径按仓库根目录解析
   storePassword=你的库密码
   keyAlias=fuckhome
   keyPassword=你的密钥密码
   ```

3. `./gradlew assembleRelease`，`app/build/outputs/apk/release/` 下即为已签名的包。

**方式二：环境变量（CI 用）**：设置 `KEYSTORE_PATH` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` 即可（优先读 `keystore.properties`）。
CI 里可把 keystore 存成 base64 secret，构建前解码为文件。

**用 MT 管理器手动签名**：可以，但**别用工具内置的「默认签名」**——那是公共密钥，没有防伪意义，还可能随工具版本变化；一旦用它发布，之后换自己的密钥就只能让所有用户卸载重装。自己新建一把并导出即可。MT 若导出 `.pk8` + `.pem`，先转 PKCS12：

```bash
openssl pkcs12 -export -in cert.pem -inkey key.pk8 -out release.p12 -name fuckhome
```

`keystore.properties` / `*.jks` / `*.keystore` / `*.p12` 都在 `.gitignore` 里，不会被提交。
注意：Xposed 模块本身不校验签名，但**同包名覆盖安装时签名必须一致**，换签名前记得先备份配置。

#### 架构说明

- **Hook 入口**：现代 API 没有 `IXposedHookZygoteInit` / `IXposedHookLoadPackage`，改为
  `onModuleLoaded` / `onPackageLoaded` / `onPackageReady` / `onSystemServerStarting`。
  普通应用走 `onPackageReady`（拿到真正的 App ClassLoader），system_server 走 `onSystemServerStarting`。
- **Hook 模型**：现代 API 是拦截器链（`HookBuilder.intercept(Hooker)` + `Chain.proceed()`），不是旧的 before/after 回调。
  `utils/XposedHelper.kt` 在链模型之上还原了 `findMethod { } / hookBefore { } / hookAfter { } / it.result = ...` 这套写法，
  Hook 模块代码基本保持原样。
- **反射工具**：不再依赖 EzXHelper，`utils/XposedHelper.kt` 提供自带的查找与反射帮助函数。
- **模块资源**：Hook 进程里用 `HookEnv.loadModuleResources()` 加载模块 APK 自身资源（`moduleRes`）。
- **配置**：设置界面写入本地存储并镜像到 Remote Preferences；Hook 侧只读 Remote Preferences。
  配置文件仍是设备加密存储下的 `Fuck_Home_Config`，旧配置可直接沿用。
- **设置界面导航**：`miuix-nav` 的 `navBackStackOf` + `NavDisplay`，转场为 `NavTransitions.MiuixDefault`；
  `NavDisplay` 只在栈深 > 1 时接管系统返回手势，因此二级页返回是滑动回退、主页面返回才退出应用。
  每个页面各自持有 `Scaffold` + 可折叠 `TopAppBar`（`MiuixScrollBehavior` + `nestedScroll`），列表用
  `overScrollVertical()` 提供 MIUI 橡皮筋回弹，短页面也能拖拽回弹。
- **沉浸式**：`SettingsActivity` 用 `enableEdgeToEdge()` 让界面画到状态栏与小白条之下，系统栏全透明，
  并显式关掉 HyperOS 默认的导航栏蒙层（`window.isNavigationBarContrastEnforced = false`，
  主题里同时设了 `android:enforceNavigationBarContrast=false`，避免首帧闪一下）。
- **关于页**：参考 [KernelSU](https://github.com/tiann/KernelSU) 的 About 设计——图标 + 应用名 + 版本号作为头部，
  固定不随页面移动、被下方内容盖住时逐个淡出，下面是链接卡片（用 `LocalUriHandler` 打开链接）。
- **服务清单**：`io.github.libxposed:service` 的 AAR 自带 `XposedProvider` 声明，**不要在
  `AndroidManifest.xml` 里重复声明**，否则清单合并会因 provider 冲突而失败。

#### 第三方代码细节

- 光效源码在 `app/src/main/java/cn/fuckhome/xiaowine/ui/effect/`，取自 miuix 示例工程。KernelSU 里的同一效果也是从该示例
  镜像过去的，但 KernelSU 整体是 GPL-3.0，所以这里取的是 Apache-2.0 的**上游原版**，不引入 GPL 义务。
- 与上游的差异（集中记录在 `ui/effect/EffectSupport.kt`）：包名 `component.effect` →
  `cn.fuckhome.xiaowine.ui.effect`；`isInDarkTheme()` → `isSystemInDarkTheme()`；`shouldShowSplitPane()`
  复制进同一包；shader 辅助函数直接用 `miuix-blur` 提供的。
- 关于页的毛玻璃同样来自 Miuix：`miuix-blur` 提供 `rememberLayerBackdrop()` / `layerBackdrop()` / `textureBlur()` /
  `BlurColors`，卡片用它把背景光效模糊后再合成（`ColorBlendToken` 的 `Pured_Regular_Light` / `Overlay_Thin_Light`，
  浅色/深色各一套），顶栏在页头可见时完全透明、标题随滚动淡入。
- ⚠️ `miuix-blur` 的 AAR 声明 `minSdkVersion 33`，所以本模块 minSdk 也是 33，Android 12 及以下无法安装。
- 效果依赖 AGSL 运行时着色器（API 33+），低版本下卡片自动回退成不透明底色、背景为纯色。

</details>
