### MIUI桌面增强模块

这个是基于原版所制作的新版本LSPosedApi适配版本

把最近任务（Recents）里的内存信息换成真实数值，并追加一些信息；同时提供一组 MIUI 桌面 / 小窗限制解除开关。

---

#### 环境要求

- 已激活本模块的 **LSPosed**，且该 LSPosed 需支持现代 Xposed API（`io.github.libxposed`，API 101 / 102）。
  只支持旧 `de.robv.android.xposed` 的框架**不再兼容**。
- 作用域：`com.miui.home`、`com.mi.android.globallauncher`、`com.android.systemui`、系统框架（`android`）。

#### 组成

| 部分 | 说明 |
| --- | --- |
| 模块入口 | `cn.fuckhome.xiaowine.hook.MainHook`（`io.github.libxposed.api.XposedModule`） |
| 模块元数据 | `app/src/main/resources/META-INF/xposed/`（`module.prop` / `java_init.list` / `scope.list`） |
| 配置共享 | 通过 libxposed service 的 **Remote Preferences** 在设置界面与 Hook 进程之间同步 |
| 设置界面 | Jetpack Compose + [Miuix](https://github.com/compose-miuix-ui/miuix) 0.9.4（`miuix-ui` / `miuix-preference` / `miuix-icons` / `miuix-nav`） |
| 页面转场 | `miuix-nav` 的 `NavDisplay` + `NavTransitions.MiuixDefault`（HyperOS 同款滑动转场 + 预测式返回） |

#### 构建

```bash
./gradlew assembleDebug      # 调试包
./gradlew assembleRelease    # 发布包（已开启 R8 + 资源压缩）
```

Android Studio 直接 Open 本工程根目录即可（`local.properties` 里的 `sdk.dir` 请改成你自己的 SDK 路径）。

#### 自定义签名

发布包默认**不签名**（`assembleRelease` 产出 unsigned APK）。要换成自己的签名，二选一：

**方式一：`keystore.properties`（推荐，Android Studio 里也能直接用）**

1. 先生成一个 keystore（只做一次，**务必备份**，丢了就没法覆盖安装升级）：

   ```bash
   keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 \
           -alias fuckhome -storetype PKCS12
   ```

2. 把 `keystore.properties.example` 复制成仓库根目录下的 `keystore.properties`，填好四项：

   ```properties
   storeFile=release.jks        # 相对路径按仓库根目录解析
   storePassword=你的库密码
   keyAlias=fuckhome
   keyPassword=你的密钥密码
   ```

3. 重新构建即可，`app/build/outputs/apk/release/` 下的包就是已签名的：

   ```bash
   ./gradlew assembleRelease
   ```

`keystore.properties`、`*.jks`、`*.keystore` 都在 `.gitignore` 里，不会被提交。

**方式二：环境变量（CI 用）**

不用改任何文件，设置下面四个变量即可，Gradle 会优先读 `keystore.properties`、没有再用环境变量：

```bash
export KEYSTORE_PATH=/path/to/release.jks
export KEYSTORE_PASSWORD=...
export KEY_ALIAS=fuckhome
export KEY_PASSWORD=...
./gradlew assembleRelease
```

CI 里可以把 keystore 存成 base64 的 secret，构建前解码成文件再设这几个变量。当前仓库的 CI 用的是 `r0adkll/sign-android-release`（对未签名的 release 包二次签名），两种方式不冲突。

**只想手动签一次**：Android Studio 的 *Build → Generate Signed App Bundle / APK* 也可以，但每次都要重选 keystore；用上面的 `signingConfigs` 后 AS 直接 Run/Signing 都会用你的签名。

**用 MT 管理器手动签名？** 可以。`./gradlew assembleRelease` 产出的未签名包（`app/build/outputs/apk/release/*.apk`）用 MT 的「APK 签名」签完就能装。但：

- **不要用工具内置的「默认签名 / 测试签名」**。那是一把公共密钥（不止你有），既没有防伪意义，还可能随工具版本变化；一旦用它发布，之后换自己的密钥就只能让所有用户卸载重装（配置全丢）。
- 正确做法：**自己新建一把密钥**（MT 里同样可以新建/导出 keystore），按上面的方式接进 Gradle，并把 keystore 与密码备份好。
- MT 若导出的是 `.pk8` + `.pem`（Android 平台格式），Gradle 的 `signingConfigs` 不支持，需要先转成 PKCS12：

  ```bash
  openssl pkcs12 -export -in cert.pem -inkey key.pk8 -out release.p12 -name fuckhome
  ```

- 另外：原版 Fuck_Home 的发布包是用原作者（xiaowine）的密钥签的，那把密钥只有他有。所以换签名后，**原版用户升级到这个包必须卸载重装**；如果能让原作者提供原 keystore，就可以无缝升级。

> **本分支说明**：这是维护者（[xiangfa-test](https://github.com/xiangfa-test)）的个人分支，使用维护者自己的密钥签名，
> 与原版包**签名不同**，因此从原版升上来需要先卸载（会丢配置，建议先备份）。
> 本地的 `release.p12` 与 `keystore.properties` 都已被 `.gitignore` 忽略，不会进入仓库。

> 注意：Xposed 模块本身不校验签名，但**同一个包名覆盖安装时签名必须一致**，否则只能先卸载（会丢配置）。换签名前记得先备份配置。

#### 第三方代码

「关于」页背景的光效（`app/src/main/java/cn/fuckhome/xiaowine/ui/effect/`）取自
[compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix) 示例工程，**Apache-2.0** 许可。
KernelSU 里的同一效果也是从该示例镜像过去的，但 KernelSU 整体是 GPL-3.0，所以这里取的是 Apache-2.0 的
**上游原版**，不引入 GPL 义务。每个文件都保留了原始的版权与 SPDX 标识，
**分发本项目时需一并保留这些声明，并附上 Apache-2.0 许可证副本**。

与上游的差异（集中记录在 `ui/effect/EffectSupport.kt`）：

- 包名 `component.effect` → `cn.fuckhome.xiaowine.ui.effect`
- `isInDarkTheme()` → `isSystemInDarkTheme()`；`shouldShowSplitPane()` 复制进同一包（来自示例的 `utils/AdaptiveUtils.kt`）
- shader 辅助函数（`RuntimeShader` / `asBrush` / `isRuntimeShaderSupported`）直接用 `miuix-blur` 提供的，与上游一致

**关于页的毛玻璃**同样来自 Miuix：`miuix-blur` 提供 `rememberLayerBackdrop()` / `layerBackdrop()` /
`textureBlur()` / `BlurColors`，卡片用它把背景光效模糊后再合成（`ColorBlendToken` 里的
`Pured_Regular_Light` / `Overlay_Thin_Light` 两套混合配方，浅色/深色各一套），顶栏在页头可见时完全透明、
标题随滚动淡入——这些都是 KernelSU「关于」页的做法。

⚠️ `miuix-blur` 的 AAR 声明 **`minSdkVersion 33`**，所以本模块的 `minSdk` 也是 **33（Android 13+）**，
Android 12 及以下无法安装。

效果依赖 AGSL 运行时着色器（API 33+），并按上游取舍只在 Android 15+ 启用（见 `AboutBackground.kt`），
低版本下卡片自动回退成不透明底色、背景为纯色。

#### 工具链

| 工具链 | 版本 |
| --- | --- |
| Gradle | 9.8.1 |
| Android Gradle Plugin | 9.4.1（AGP 9 内置 Kotlin，KGP 提升到 2.4.21） |
| Kotlin / Compose 编译器插件 | 2.4.21 |
| compileSdk / buildTools | 37 / 37.0.0（minSdk 33，targetSdk 34） |
| JDK | 21+（Miuix 以 JVM target 21 发布，消费方需匹配，否则内联其代码会编译失败） |

#### 架构说明

- **Hook 入口**：现代 API 没有 `IXposedHookZygoteInit` / `IXposedHookLoadPackage`，改为
  `onModuleLoaded` / `onPackageLoaded` / `onPackageReady` / `onSystemServerStarting`。
  普通应用走 `onPackageReady`（拿到真正的 App ClassLoader），system_server 走 `onSystemServerStarting`。
- **Hook 模型**：现代 API 是拦截器链（`HookBuilder.intercept(Hooker)` + `Chain.proceed()`），
  不是旧的 before/after 回调。`utils/XposedHelper.kt` 在链模型之上还原了
  `findMethod { } / hookBefore { } / hookAfter { } / it.result = ...` 这套写法，Hook 模块代码基本保持原样。
- **反射工具**：不再依赖 EzXHelper，`utils/XposedHelper.kt` 提供自带的查找与反射帮助函数。
- **模块资源**：Hook 进程里用 `HookEnv.loadModuleResources()` 加载模块 APK 自身资源（`moduleRes`）。
- **配置**：设置界面把配置写入本地存储并镜像到框架的 Remote Preferences；Hook 侧只读 Remote Preferences。
  配置文件仍是设备加密存储下的 `Fuck_Home_Config`，旧配置可直接沿用。
- **设置界面导航**：用 `miuix-nav` 的 `navBackStackOf` + `NavDisplay` 管理二级页面，转场为 `NavTransitions.MiuixDefault`；
  `NavDisplay` 只在栈深 > 1 时接管系统返回手势，因此二级页返回是滑动回退、主页面返回才退出应用（与原版一致）。
  每个页面各自持有 `Scaffold` + 可折叠 `TopAppBar`（`MiuixScrollBehavior` + `nestedScroll`），列表用
  `overScrollVertical()` 提供 MIUI 橡皮筋回弹，短页面也能拖拽回弹。
- **沉浸式**：`SettingsActivity` 用 `enableEdgeToEdge()` 让界面画到状态栏与小白条之下，系统栏全透明，
  并显式关掉 HyperOS 默认的导航栏蒙层（`window.isNavigationBarContrastEnforced = false`，
  主题里同时设了 `android:enforceNavigationBarContrast=false`，避免首帧闪一下）。
- **关于页**：参考 [KernelSU](https://github.com/tiann/KernelSU) 的 About 设计——作者头像 + 应用名 + 版本号作为头部，
  随列表滚动淡出缩小，下面是链接卡片（用 `LocalUriHandler` 打开链接）。
- **服务清单**：`io.github.libxposed:service` 的 AAR 自带 `XposedProvider` 声明，**不要在
  `AndroidManifest.xml` 里重复声明**，否则清单合并会因 provider 冲突而失败。
