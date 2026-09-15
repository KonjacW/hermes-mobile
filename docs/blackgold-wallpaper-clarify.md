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

**单张照片**，App 内从相册选图，持久化到 App 私有目录 `filesDir/wallpaper-<时间戳>.jpg`。三个可调参数：

| 参数 | 范围 | 默认 |
|---|---|---|
| 亮度 | 0.5–1.5 | 1.0 |
| 遮罩不透明度 | 0–1 | 0.85 |
| 模糊半径 | 0–40 dp | 0 |

**实现要点**：
- 壁纸图层 `WallpaperLayer` 在 `MainActivity` 全局画一次（`AsyncImage` + `ColorMatrix` 亮度 + `Modifier.blur` 模糊 + 深海军蓝遮罩）。
- **每次选图都写成一个新文件名**（`WallpaperFiles.fileName()`，`wallpaper-<毫秒时间戳>.jpg`，同名则加 `_1`/`_2`），并删掉被替换掉的旧文件。原因见第七节：固定文件名会让 Coil 3 把新图解析成旧图的缓存位图。
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

## 五、构建

本机无 JDK 21 + Android SDK，构建一律走 fork 的 CI（`.github/workflows/android.yml`，手动 `workflow_dispatch`）。**上一批改动（`38b0798`）已构建通过**：run `34929887853`，8 个 job 全绿（ktlint / android-lint / unit-tests / instrumented-tests / build / release-compile / ci-summary），产物走 debug artifact `hermescontrol-debug-apk`。**本文件描述的黑金/壁纸/clarify 改动与第七节的修复另需各自的 CI run 验证，不要把上面这个 run 号当它们的结果。**手动构建时需：

```bash
# 前提：JDK 21+、Android SDK（ANDROID_HOME）、compileSdk 37
cd hermes-mobile
./gradlew assembleDebug                      # debug APK
./gradlew ktlintCheck testDebugUnitTest      # 风格 + 单测门禁
# release（需 keystore 环境变量）走 .github/workflows/release.yml 的 tag 触发
```

**签名（fork 专属）**：CI 构建出的 debug APK 用**一份固定 keystore** 签名，否则 runner 每次新生成随机 debug 密钥、每个包的签名都不同、新包无法覆盖安装（装一次就得卸载重装一次）。workflow 里先把它落到 `~/.android/debug.keystore`（AGP debug 签名配置认的路径），再用 keytool 断言证书 SHA-256 必须等于 `7C:CC:A9:61:AA:71:57:A4:AC:9C:B2:E8:51:2F:3A:47:B6:C7:43:07:87:03:CD:6B:96:62:FD:C2:90:E1:8E:B7`，不匹配就直接失败（避免「签名静默变了、装不上才发现」）。keystore 本体是 `secret DEBUG_KEYSTORE_BASE64`（alias `androiddebugkey`、口令 `android` —— AGP debug 签名配置写死的值）；生成/轮换命令、离线备份位置、核验方法写在仓库外的 `hermes-mobile-signing/README.md`（签名私钥不进 git，故意的）。

CI（fork 后 `.github/workflows/android.yml`）已配好全流程，push 分支即跑 ktlint / lint / 单测 / build。

---

## 六、已知限制与待办

1. **未真机验证**：所有改动未编译、未在真机运行。以下 α 值 / 视觉参数需构建后实测微调：遮罩默认 0.85、黑金容器阶梯色。
2. **`pending_clarify.answers` 未处理**：本次只渲染「待答的 questions」；若服务端在 `answers` 已填满时仍推 `pending_clarify`（理论上不该），会显示一个已答过的框——该边界未覆盖，需要时再补。
3. **ktlint/import 排序**：`WallpaperSection.kt` 的 import 已由 run `34931178059`（commit `d8b0e3a`）的 ktlint job 覆盖验证通过；`WallpaperFiles.kt` / `WallpaperFilesTest.kt` 是本次新增文件，要等本修复自己的 CI run 才算验证过。
4. **上游不回馈**：这是个人 fork，不打算提 PR 回馈 Hy4ri/hermes-mobile（除非你后续要求）。
5. **选图落盘的两个残留窗口**（都是 `ServerStore` 既有设计带来的，本次没有改它）：
   - `ServerStore.update` 的落盘是异步的（`scope.launch(Dispatchers.IO)` + 吞异常），所以「删掉旧照片」与「新配置落盘」之间有极小窗口。进程正好在这个窗口里被杀 ⇒ 重启后配置仍指向已删除的旧文件（表现为壁纸空白，重新选一张即恢复）。
   - 选图协程被取消（例如拷云端大图时退出设置页）只会留下一个孤儿 `wallpaper-*.jpg`（几 MB），目前没有自动回收；每次正常选图都会删掉被替换的那一张。**这种情况下这次选择会被静默丢弃**（没有提示、配置不变、壁纸仍是旧的）——因为协程已经随着页面销毁被取消了。本机相册照片几乎瞬间完成，主要影响云端大图。
   两者的取舍都是「宁可留孤儿文件，也不要留下指向不存在照片的配置」——这个方向是刻意的。孤儿回收（启动时清扫不等于当前壁纸的 `wallpaper-*.jpg`）留作可选后续，不做是因为要碰 App 启动路径，收益只是几 MB 存储。

---

## 七、修复：更换壁纸不生效（2026-09-15，真机反馈后补）

**症状**：真机上选定第二张照片后，界面显示的还是上一张（滑条调亮度/遮罩/模糊同样没反应）。

**根因（两层，第一层是主因）**：

1. **Coil 3 改了缓存键的默认行为**。Coil 2 会把文件的「最后写入时间」并进缓存键；**Coil 3 默认不再并**（官方升级文档原话：*"A file's last write timestamp is no longer added to its cache key by default… This can be re-enabled with `ImageRequest.Builder.addLastModifiedToFileCacheKey(true)`"*，移除原因是避免在主线程读磁盘）。而改动前的实现**永远把新照片写到同一个路径** `filesDir/wallpaper.jpg` ⇒ 加载用的路径字符串两次完全一样 ⇒ Coil 直接把上一次解码的位图还回来 ⇒ 显示永远是第一张。
2. **配置对象在结构上也没变**。`WallpaperConfig` 是 data class，两次选图的 `uri` 字符串相同 ⇒ `ServerStore` / `AuthManager` 的 `MutableStateFlow` 认为值没变、**不发射**（StateFlow 按 `equals` 去重）⇒ 图层连重组都不会发生。即便重组了，第 1 层仍然会给出旧位图。

**修法**（一处改动同时解掉两层）：让「持久化文件的身份」每次选图都变。

| 改动 | 内容 |
|---|---|
| 新增 `data/config/WallpaperFiles.kt` | `fileName(timestampMillis, exists)` → `wallpaper-<毫秒>.jpg`；重名则退化为 `…_1.jpg` / `…_2.jpg` |
| 同文件 `store(filesDir, timestampMillis, open)` | 把「选图落盘」抽成**不含 Context 的纯函数**（这样 JVM 单测能真跑它）：写新名字，取不到流或拷贝失败就 `null` 且**不碰**任何已有文件，半截文件当场删掉 |
| `WallpaperSection` 调用方 | 拿到新路径 → 先提交配置（`onWallpaperChange`）→ **之后**才删被替换的旧照片。定序理由是失败方向：中途被打断只可能留下一个孤儿文件（无害），不会留下「配置指向已删除照片」（会导致壁纸空白） |
| `WallpaperSection` 的「移除」 | 先把配置置空、再删文件，同一个定序原则 |
| 选图失败提示 | 不再静默：弹 `settings_wallpaper_pick_failed`（EN *Could not read that photo. Try another one.* / ZH「无法读取这张照片，换一张试试」）。静默失败在界面上与「缓存没刷新」无法区分，这正是本次难定位的直接原因 |

**为什么不是「保留一个文件名 + 打开 `addLastModifiedToFileCacheKey`」**：那样只解掉第 1 层，第 2 层（StateFlow 不发射 ⇒ 不重组）仍在，模型字符串也没变，图还是旧的。换文件名是唯一一处同时让两层失效的改动。

**验证（如实分级）**：

- **单测**：`app/src/test/.../data/config/WallpaperFilesTest.kt` 覆盖「每次选图文件名必不同 / 重名 bump 到 `_1`/`_2`」+ `store()` 的三条路径（写入成功且不碰旧文件、取不到流返回 null 且旧文件仍在、拷贝中途抛异常不留半截文件）。
- **未完成**：本修复自身的 CI 结果——写这段时改动还没跑过任何 CI run（见下方待补的 run 号）。**不要拿第五节里上一批改动的 run 号当本修复的证据。**
- **真机未验证**：手机上的「图不刷新」只能在真机复现，本机没有 Android 运行环境。装机后要确认的是：选第二张照片后界面立刻换图；杀进程重开仍是第二张；调亮度/遮罩/模糊三个滑条立即可见效果。
