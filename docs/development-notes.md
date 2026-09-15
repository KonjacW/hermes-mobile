# Hermes Mobile 开发知识库（本 fork 维护）

> 本文件是**架构知识沉淀**，不是改动流水账。记录这个代码库的关键架构事实、扩展路径和坑，供未来继续工作（构建 APK、真机调参、回馈上游）时直接复用，不必重新摸一遍源码。
>
> 本次具体改动见 [`blackgold-wallpaper-clarify.md`](blackgold-wallpaper-clarify.md)。

---

## 1. 项目概况

- 上游：`Hy4ri/hermes-mobile`，本 fork：`KonjacW/hermes-mobile`（默认分支 `dev`，release 走 tag `v*`）。
- 技术栈：Kotlin 2.4.10 + Jetpack Compose + Material 3，包名 `com.m57.hermescontrol`，minSdk 26 / targetSdk 37 / compileSdk 37。
- 图片加载：**Coil 3**（`coil3.compose.AsyncImage`，不是 coil2）。
- 导航：`androidx.navigation3`（NavKey / NavBackStack），路由一律走 `NavigationController.navigateTo()`。
- 数据库：Room（ChatMessageEntity / HermesDatabase），`getMessagesForSession()` 是 `suspend` 不是 `Flow`。
- 持久化：DataStore（`ServerStore`）+ EncryptedSharedPreferences（token/cookie）。
- WS：`HermesWsClient` 单例，JSON-RPC over WebSocket（`/api/ws`）。

## 2. 构建环境（重要）

- **要求**：JDK 21+、Android SDK（`ANDROID_HOME`）、compileSdk 37、Gradle wrapper 自带。
- **本机现状**：只有 Java 8（`C:/Program Files/Java/jre1.8.0_501`）+ 单独 adb（`D:/software/platform-tools-latest-windows`），**无 JDK 21 / SDK / gradle** ⇒ 本机无法 `./gradlew`。
- **CI**：`.github/workflows/android.yml` 已配全流程（ktlint / android-lint / unit-tests / build / release-compile / instrumented-tests / ci-summary），push 分支即跑；release APK 由 tag 触发 `.github/workflows/release.yml`（需 keystore env var）。
- **构建命令**：
  ```bash
  ./gradlew assembleDebug
  ./gradlew ktlintCheck testDebugUnitTest
  ```
- **ktlint 1.8.0** 强制：ASCII 字典序 import（大写 < 小写）、trailing comma、120 字符行长、`const val` 用 SCREAMING_SNAKE_CASE。`./ktlint --format` 自动修。

## 3. 主题体系（新增 preset 的扩展路径）

```
theme/
├── Theme.kt            # dispatcher：ThemePreset 枚举 + themeFor() lookup + mode fallback
├── PaletteTemplate.kt  # 唯一模板：PaletteColors(29 slot) + HermesStatusColors + ThemePalette
├── HermesStatusColors.kt
└── presets/            # 每个 preset 一个文件，同一骨架
    ├── DefaultScheme.kt / MonochromeScheme.kt / GruvboxScheme.kt
    ├── CatppuccinScheme.kt / AmoledScheme.kt（dark-only）/ NordScheme.kt
    └── BlackGoldScheme.kt（本 fork 新增，dark-only）
```

**新增 preset 的 5 步**（`THEMES.md` 官方路径）：
1. 新建 `presets/<Name>Scheme.kt`：`buildTheme`（FULL）/ `buildThemeDarkOnly` / `buildThemeLightOnly` 填 `PaletteColors` + `HermesStatusColors`。
2. `Theme.kt`：`ThemePreset` 枚举加一项 + `themeFor()` 加一行 + import。
3. `ui/settings/components/AppearanceSection.kt`：两处 `when(preset)` label 映射（OutlinedButton 里的紧凑版 + DropdownMenuItem 里的展开版）。
4. `res/values/strings.xml` + `res/values-zh/strings.xml`：加 `theme_preset_<name>` 字符串（`&` 要写 `&amp;`）。
5. 门禁：`ThemePaletteTest` 有两个对比度测试，见下。

**ThemePaletteTest 门禁**（`app/src/test/.../theme/ThemePaletteTest.kt`）：
- `fullBleedTextPairsMeetContrastInEveryShippedMode` 遍历 `ThemePreset.entries`（**新 preset 必被测**）：`onSurface/background ≥ 4.5:1`、`onSurfaceVariant/background ≥ 3:1`。
- `errorSlotPairsMeetContrastInEveryShippedMode` 用**显式 `themes` 列表**（只有 DEFAULT/MONOCHROME/GRUVBOX/CATPPUCCIN/AMOLED，**滞后，不含 NORD**）——新 preset 不会被它测，但建议手算保证 error 对 ≥ 3:1。

**checkColorLiterals 守卫**（issue #622）：`theme/` 与 `*Preview.kt` 之外禁 `Color(0x…)` 字面量和 `Color.White/Black` 等。用 `MaterialTheme.colorScheme.<token>`、`LocalHermesStatusColors.current.<semantic>`、`Color.Transparent`。

**动态色**：`HermesControlTheme(useDynamicColors=…)` 在 API 31+ 覆盖 preset scheme；但语义状态色永远从 preset 的 `LocalHermesStatusColors` 解析。

## 4. 持久化（新增设置的扩展路径）

```
data/config/ServerStoreState.kt   # @Serializable data class，加字段带默认值（兼容旧数据）
data/config/ServerStore.kt        # DataStore 包装：getLatestState() + update(transform)
data/local/AuthManager.kt         # object 单例：get/set + StateFlow 供 UI collectAsState
```

**加一个设置项的模式**（照 `chatFontScale`）：
1. `ServerStoreState` 加字段（带默认值）。
2. `AuthManager` 加：`private val _xxxFlow = MutableStateFlow(...)` + `val xxxFlow`；`getXxx()` / `setXxx()`（set 里 `serverStore.update { it.copy(...) }` + `_xxxFlow.value = ...`）；`serverStore.stateFlow.collect` 里同步 `_xxxFlow.value = state.xxx`。
3. `SettingsUiState` 加字段 + `SettingsViewModel.loadSettings()` 读 + `onXxxChange()` 写。
4. UI：`SettingsSubPages.kt` 对应 sub-page 里加 section 调用。
5. 测试：`SettingsViewModelTest` 用 `mockkObject(AuthManager)` **strict mock**，新增 `AuthManager.getXxx()/setXxx()` 调用**必须补 stub**（否则 loadSettings 抛异常）。

**SettingsViewModel 是无参构造**（`viewModel { SettingsViewModel() }`，不是 AndroidViewModel）⇒ 需要 Context 的 IO 操作（如选图 copy）放 UI 层用 `LocalContext` + `Dispatchers.IO`，或另传 context。

## 5. UI Scaffold 与全局背景

- `ui/common/HermesScaffold.kt`：统一入口（drawer + TopAppBar + `paddingValues`）。**`HermesScaffold` 内部已用 Box 处理了 topBar 偏移，content 里不要再 `.padding(paddingValues)`**（#1 反复踩的坑）。
- 全局背景/壁纸：`MainActivity` 里 `Surface(color = background)` 包 `Box { WallpaperLayer(...); MainNavigation() }`；`HermesScaffold.containerColor` 改成 `Color.Transparent` 让壁纸从「卡片覆盖不到的背景」透出，卡片/TopAppBar 仍是纯色 ⇒ 文字对比度稳定。
- 遮罩色取 `MaterialTheme.colorScheme.background`（跟随主题），**不硬编码 hex**（既跟随黑金底色，也避开 checkColorLiterals）。

## 6. clarify / approval 的 pending 重放模式（重连重放参照模板）

`ui/chat/ChatApprovalsDelegate.kt` 是「重连后重放 pending 提示」的**参照实现**，任何新交互提示要支持重连重放就照它抄：

- `handleApprovalRequest(event)`：渲染提示 + ack。
- `replayPendingApproval(sessionId)`：发 `approval.pending` RPC 主动拉队列。
- `maybeSurfacePendingApproval(map, sessionId)`：从 `session.info` / `session.resume` 的 `pending_approval` 字段直接 surface（**去重**：`requestId != null && 已显示` 则跳过）。
- `parseApprovalMap(map, sessionId)`：snake_case backend map → 类型化事件。

`ChatViewModel` 两处消费：`WsEvent.SessionInfo` 处理（约 864 行）读 `info["pending_approval"]`，`SESSION_RESUME` 处理（约 1211 行）读 `resultMap["pending_approval"]` + `replayPendingApproval`。

**clarify 的 bug 与修法**（本 fork）：clarify 只监听 `clarify.request` 事件（只在首次弹框推一次），重连不重推；但服务端 `session.resume` 带 `pending_clarify` 字段（`request_id` / `questions[qid,question,choices,multi_select]` / `answers`）。修法 = 照 approval 补 `parseClarifyUi` + `maybeSurfacePendingClarify`，在 SessionInfo + SESSION_RESUME 两处读 `pending_clarify`。字段映射对齐 `EventParser` 的 `clarify.request` 解析（`EventParser.kt:134`），渲染态是 `ChatUiState.clarifyRequest: ClarifyUi?`（不是 `WsEvent.ClarifyRequest`）。

## 7. 关键 API 速查

- **亮度**：`ColorMatrix().apply { setToScale(b,b,b,1f) }` + `ColorFilter.colorMatrix(m)`，传给 `AsyncImage(colorFilter=...)`。
- **模糊**：`Modifier.blur(radius.dp)`（`androidx.compose.ui.draw.blur`）。
- **选图**：`rememberLauncherForActivityResult(ActivityResultContracts.GetContent())`，MIME `"image/*"`；copy 到 `context.filesDir` 持久化（相册 Uri 会失效）。
- **壁纸/背景图**：`AsyncImage(model=filePath, contentScale=Crop, colorFilter=..., modifier=Modifier.blur(...))`。

## 8. 本次改动落点（速查）

| 功能 | 落点 |
|---|---|
| 黑金主题 | `theme/presets/BlackGoldScheme.kt` + `Theme.kt` + `AppearanceSection.kt` + strings.xml |
| 壁纸 | `data/config/WallpaperConfig.kt` + `ui/common/Wallpaper.kt`（WallpaperLayer）+ `WallpaperSection.kt` + `HermesScaffold.kt`（containerColor 透明）+ `MainActivity.kt` |
| clarify 修复 | `ChatClarifyDelegate.kt`（parseClarifyUi + maybeSurfacePendingClarify）+ `ChatViewModel.kt` 两处 |

## 9. 待办（构建后必做）

- 跑 `./gradlew ktlintCheck testDebugUnitTest` 过门禁（尤其 import 排序手排可能疏漏）。
- 真机调参：遮罩默认 α=0.85、黑金容器阶梯色，未实测，需构建后肉眼/截图复核。
- `pending_clarify.answers` 未处理（只渲染待答 questions），该边界需要时再补。
