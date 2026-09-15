# 黑金主题 + 壁纸 + clarify 修复

本分支 `feat/blackgold-wallpaper-clarify-fix`（基于上游 `v1.27.0`）对 Hermes Mobile 做了三处改动，均只改代码/文档，**尚未构建 APK**。

---

## 一、改动总览

| # | 功能 | 涉及文件 |
|---|---|---|
| 1 | 黑金主题预设（dark-only） | `theme/presets/BlackGoldScheme.kt`（新增）、`theme/Theme.kt`、`ui/settings/components/AppearanceSection.kt`、`res/values*/strings.xml` |
| 2 | 壁纸（单照片 + 亮度/遮罩/模糊度） | `data/config/WallpaperConfig.kt`（新增）、`data/config/ServerStoreState.kt`、`data/local/AuthManager.kt`、`ui/common/Wallpaper.kt`（新增）、`ui/common/HermesScaffold.kt`、`ui/settings/components/WallpaperSection.kt`（新增）、`ui/settings/SettingsViewModel.kt`、`ui/settings/SettingsSubPages.kt`、`MainActivity.kt`、`res/values*/strings.xml` |
| 3 | 修复 clarify 确认框切回会话后消失 | `ui/chat/ChatClarifyDelegate.kt`、`ui/chat/ChatViewModel.kt` |
| — | 测试跟进 | `app/src/test/.../SettingsViewModelTest.kt`（补 `getWallpaper`/`setWallpaper` stub） |

---

## 二、黑金主题

**新增预设 `BLACK_GOLD`（深色专属）**，照 AMOLED 的 dark-only 模式。浅色模式下回退默认主题。

色值（对齐桌面端黑金 token 表）：

| 角色 | 值 |
|---|---|
| 底 `background/surface` | `#0a182a` |
| 次级条 `surfaceVariant` | `#081422` |
| 主文字 `onBackground/onSurface` | `#f5efe2` |
| 二级 `onSurfaceVariant` | `#d8d2c2` |
| 三级 | `#b4b0a4` |
| 四级（占位） | `#8f8c82` |
| 金 `primary` | `#c9a05c` |
| 提亮金 `secondary` | `#d9b87a` |
| 成功 | `#8aa68f` |
| 警告（复用金） | `#c9a05c` |
| 错误 | `#c07a6e` |

- 容器阶梯（`surfaceContainerLow→Highest`）用同 hue 提亮：`#0c1a2d` → `#162c48`，不引入第二底色。
- **零渐变 / 发光 / 霓虹 / 紫 / 亮蓝**，金只做点缀。
- 使用方式：设置 → 外观 → 预设主题 → 选「黑金（仅深色）」。

对比度核验（`ThemePaletteTest` 的两个门禁都能过）：
- `onSurface/background ≈ 14.5:1`（≥4.5 ✅）
- `onSurfaceVariant/background ≈ 11.2:1`（≥3 ✅）
- `onError/error ≈ 5.4:1`、`onErrorContainer/errorContainer ≈ 12.9:1`（≥3 ✅）

---

## 三、壁纸

**单张照片**，App 内从相册选图，持久化到 App 私有目录 `filesDir/wallpaper.jpg`。三个可调参数：

| 参数 | 范围 | 默认 |
|---|---|---|
| 亮度 | 0.5–1.5 | 1.0 |
| 遮罩不透明度 | 0–1 | 0.85 |
| 模糊半径 | 0–40 dp | 0 |

**实现要点**：
- 壁纸图层 `WallpaperLayer` 在 `MainActivity` 全局画一次（`AsyncImage` + `ColorMatrix` 亮度 + `Modifier.blur` 模糊 + 深海军蓝遮罩）。
- 遮罩色取 `MaterialTheme.colorScheme.background`（黑金下即 `#0a182a`），**不硬编码 hex** —— 既跟随主题，也避开 `checkColorLiterals` 守卫。
- `HermesScaffold.containerColor` 改为 `Color.Transparent`，让壁纸从「卡片覆盖不到的背景」透出；TopAppBar / 卡片仍是纯深蓝，文字对比度稳定。
- 设置入口：设置 → 外观 → 壁纸（选图 + 移除 + 三个滑条）。

> 明确不含：换图轮换、多图管理、跨设备同步。设计上就是「一张照片 + 三个旋钮」。

---

## 四、clarify 确认框修复

**根因**：App 只监听 `clarify.request` **事件**，该事件只在确认框首次弹出时推一次；重连/切回会话后服务端不重推。但服务端在 `session.resume` 响应里一直带 `pending_clarify` 字段（`request_id` / `questions[qid,question,choices,multi_select]` / `answers`），App 没读。

**修法**（照 `ChatApprovalsDelegate` 的 pending-approval 重放模式）：
1. `ChatClarifyDelegate.kt` 新增：
   - `parseClarifyUi(map)`：把 snake_case 的 `pending_clarify` map 转成 `ClarifyUi`（对齐 `EventParser` 的 `clarify.request` 解析）。
   - `maybeSurfacePendingClarify(map)`：去重（`clarifyId` 相同则跳过）后写入 `uiState.clarifyRequest`。
2. `ChatViewModel.kt` 两处读 `pending_clarify` 并 surface：
   - `SessionInfo` 处理（`session.info` 携带，重连 reconcile）。
   - `SESSION_RESUME` 处理（`session.resume` 响应携带，切回会话）。

---

## 五、构建（推迟，未执行）

本机当前无 JDK 21 + Android SDK，尚未构建。构建需：

```bash
# 前提：JDK 21+、Android SDK（ANDROID_HOME）、compileSdk 37
cd hermes-mobile
./gradlew assembleDebug                      # debug APK
./gradlew ktlintCheck testDebugUnitTest      # 风格 + 单测门禁
# release（需 keystore 环境变量）走 .github/workflows/release.yml 的 tag 触发
```

CI（fork 后 `.github/workflows/android.yml`）已配好全流程，push 分支即跑 ktlint / lint / 单测 / build。

---

## 六、已知限制与待办

1. **未真机验证**：所有改动未编译、未在真机运行。以下 α 值 / 视觉参数需构建后实测微调：遮罩默认 0.85、黑金容器阶梯色。
2. **`pending_clarify.answers` 未处理**：本次只渲染「待答的 questions」；若服务端在 `answers` 已填满时仍推 `pending_clarify`（理论上不该），会显示一个已答过的框——该边界未覆盖，需要时再补。
3. **import 排序**：新增文件的 import 已按 ASCII 字典序手排，但未跑 ktlint 验证；构建前先 `./gradlew ktlintCheck` 或 `./ktlint --format`。
4. **上游不回馈**：这是个人 fork，不打算提 PR 回馈 Hy4ri/hermes-mobile（除非你后续要求）。
