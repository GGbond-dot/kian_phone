# Background 02 — 2026-05-05

> 给下一个对话用的上下文。新会话开始时把这份 + `background01.md` 一起贴进去。
> 这份只覆盖 2026-05-05 这一轮做的事,核心是修了 UsageStats 跨天 bug、挪了 AI 配置到 Settings、踩坑发现 MNN-LLM + Gemma 4 当前不可行,确定下一步走 LiteRT-LM。

---

## 本轮起点

用户对 `background01.md` 收尾时 codex 做的 AI 部分不满意,主要诉求:

1. **AI 页不能滑动** —— `AiChatScreen.kt` 顶层 Column 里塞了 ApiSettingsCard(4 个输入框)+ ModelStatusCard + LazyColumn(weight 1f),设置区直接挂外层、没有 verticalScroll,导致设置区占掉小半屏、聊天区被挤到几行
2. **干预措施单薄(#5)** —— 只覆盖抖音/小红书,只能"提醒+输入目的"
3. **想用阿里 MNN-LLM** —— 不想用 codex 之前用的 MediaPipe
4. **新发现的严重 bug** —— 用机时间过夜不刷新,5/4 显示 25h 超过 24h

用户本轮明确要做的(优先级排序):
1. 修 UsageStats bug
2. AI 配置挪 Settings
3. 接端侧 LLM(模型选 Gemma 4 4B,坚持用 Gemma 4)

干预加强(#5)推迟到后续。

---

## 已完成 1:UsageStats 跨天累计 bug 修复

### 根因诊断

两个 bug 叠加导致显示 25h+:

1. **`UsageStatsCollector.getTodayTopApps()`** 用 `queryUsageStats(INTERVAL_DAILY, startOfDay, now)`。这个 API 返回的是"与时间范围**有交集**的所有 bucket",每个 bucket 的 `totalTimeInForeground` 是 **bucket 全段累计,不被请求时间裁剪**。系统的 daily bucket 边界由 framework 决定,小米 14 上常常不在午夜对齐,导致第二天早上查 5/5 范围,系统把 5/4 跨天 bucket 的整天累计也加进来。

2. **`UsageStatsRepository.observeTodayTotal()`** 的 Flow 不会自动跨天。`startOfTodayMs()` 在订阅 Flow 那一瞬间求值,之后只要 KHUP 进程不重启,这条 Flow 永远用最初那个 sinceMs。用户 5/4 打开 KHUP 不杀进程,过夜后 Dashboard 仍在查"自 5/4 0:00 起的累计"。

### 改动文件

1. **`app/src/main/java/com/kian/khup/collection/usage/UsageStatsCollector.kt`**
   - `getTodayTopApps()` 弃用 `INTERVAL_DAILY`,改用 `queryEvents(startOfDay, now)`
   - 自己配对 `ACTIVITY_RESUMED` / `MOVE_TO_FOREGROUND` ↔ `ACTIVITY_PAUSED` / `MOVE_TO_BACKGROUND`
   - 跨午夜的会话起点 clamp 到 `startOfDay`
   - 仍在前台、未收到 PAUSED 的会话按 `now` 截断
   - 单 app foregroundMs 最终 clamp 到 `(now - startOfDay)` 作为防线

2. **`app/src/main/java/com/kian/khup/core/data/db/AppSessionDao.kt`**
   - 新增 `deleteSessionsExceedingDuration(maxDurationMs: Long): Int`

3. **`app/src/main/java/com/kian/khup/core/data/repository/UsageStatsRepository.kt`**
   - 新增私有 `todayStartFlow()`:`flow { while (true) { emit(startOfTodayMs()); delay(到下一个午夜) } }`
   - `observeTodayTotal()` / `observeTodayTopApps()` / `observeDailyTotals()` 全部改成 `todayStartFlow().flatMapLatest { ... }`,午夜自动切换 sinceMs
   - `syncToday()` 写入时 `durationMs.coerceAtMost(now - startOfDay)` 防止异常值入库
   - 新增 `cleanupAnomalousSessions()`:删 `durationMs > 25h` 的脏行(给 1h 缓冲)

4. **`app/src/main/java/com/kian/khup/KhupApplication.kt`**
   - `onCreate()` 起一个 SupervisorJob + Dispatchers.IO 的 appScope
   - 异步调用 `cleanupAnomalousSessions()`,日志记录删了几行

### 验证

- `./gradlew assembleDebug` —— `BUILD SUCCESSFUL in 15s`
- 编译警告:`MOVE_TO_FOREGROUND` / `MOVE_TO_BACKGROUND` deprecated,与 `ACTIVITY_RESUMED` / `ACTIVITY_PAUSED` 并列收事件,保留无问题
- 真机未测(用户没说要立即装,等所有改动一起验)

---

## 已完成 2:AI 页 API 配置挪到 Settings tab

### 思路

`AiChatScreen.kt` 之前顶层 Column 挂了 4 个东西:Header + ModelStatusCard + ApiSettingsCard(4 个输入框)+ LazyColumn(weight 1f)+ 输入栏。**没有 verticalScroll,LazyColumn 又因为 ApiSettingsCard 太高被挤扁**。

挪到 Settings 后:
- AI 页只剩 Header + Chat LazyColumn(weight 1f) + Input,聊天区独占大部分屏幕
- API 配置 + 模型路径状态作为一张卡放进 Settings 末尾(SettingsScreen 本来就是 verticalScroll Column)

### 改动文件

1. **`app/src/main/java/com/kian/khup/output/ui/settings/SettingsViewModel.kt`**
   - 注入 `AiSettingsRepository` + `LlmEngine`
   - 暴露 `aiSettings: StateFlow<AiSettings>`
   - 暴露 `aiModelState: StateFlow<LlmModelState>`(MutableStateFlow,调 `refreshAiModelState()` 刷新)
   - 新增 `setProviderMode` / `setApiBaseUrl` / `setApiKey` / `setApiModel` / `refreshAiModelState`

2. **`app/src/main/java/com/kian/khup/output/ui/settings/SettingsScreen.kt`**
   - 顶部加 import:Refresh icon、OutlinedTextField、TextButton、PasswordVisualTransformation、FontWeight、AiProviderMode、AiSettings、LlmModelState
   - 收集 `aiSettings` + `aiModelState`
   - 在 `InterventionSettingsCard` 之后追加 `AiChannelCard`
   - `AiChannelCard` 私有 composable:模型路径状态(✓/✗ + 候选路径列表)+ 三选一 Provider Mode 按钮 + Base URL/Model/API Key 输入 + 保存按钮
   - 私有 `ProviderModeButton` composable

3. **`app/src/main/java/com/kian/khup/output/ui/ai/AiChatScreen.kt`**
   - 删 `ApiSettingsCard` + `ModelStatusCard` 两个私有 composable
   - 删 `apiBaseUrlDraft` / `apiKeyDraft` / `apiModelDraft` 三个 state
   - 删 `import AiProviderMode` / `PasswordVisualTransformation` 等不再需要的 import
   - 顶部 Header 副标题改成"模型就绪 / 未找到模型(去 Settings 配置)"
   - EmptyChatCard 文案改成"API 配置和模型路径在 Settings 页里调整"

4. **`app/src/main/java/com/kian/khup/output/ui/ai/AiChatViewModel.kt`** —— **未改**
   - 几个 setter 方法(setProviderMode / setApiBaseUrl / setApiKey / setApiModel)目前没人调,但属于公开 API,不主动删
   - 后续真要清理可以删

### 验证

- `./gradlew assembleDebug` —— `BUILD SUCCESSFUL in 8s`
- 仅一个 deprecated 警告:`Icons.Outlined.Send` 建议换 `Icons.AutoMirrored.Outlined.Send`(RTL 镜像),原 codex 写法保留

---

## 已完成 3:模型常量从 Gemma 3 暂改成 Gemma 4 (但实际方案推翻)

`MediaPipeLlmEngine.kt` 的 `MODEL_FILE_NAME` 从 `khup_llm.task` 改成 `gemma-4-E4B-it.task`。

**这步实际上无效,因为下面的发现把整个 MediaPipe 路径推翻了。** 文件名等下一轮会再改一次。

---

## 关键发现:Gemma 4 + Android MediaPipe 路径堵死

### 原假设(已推翻)

- 假设 1:taobao-mnn 有 Gemma 4 MNN 转换。**错** —— 只到 Gemma 3 4B。自己用 `llmexport` 转 Gemma 4 现成失败案例在 alibaba/MNN issue #4177
- 假设 2:MediaPipe `tasks-genai` 0.10.27 + Gemma 4 `.task` 文件可以跑。**错** —— Gemma 4 在 Android MediaPipe 上没有可用 `.task` 文件

### 实测情况

`huggingface.co/litert-community/gemma-4-E4B-it-litert-lm` repo 内只有:

| 文件 | 大小 | 实际用途 |
|---|---|---|
| `gemma-4-E4B-it.litertlm` | 3.66 GB | LiteRT-LM 框架专用,不是 MediaPipe `tasks-genai` 格式 |
| `gemma-4-E4B-it-web.task` | 2.96 GB | Web/WebGPU,Android 跑不了 |

HF README 直接写明:"On supported Android devices, Gemma 4 is available through Android AI Core as Gemini Nano, **which is the recommended path for production applications**."

也就是 Google 自己没给 Android MediaPipe 出 Gemma 4 的 .task。`tasks-genai` 这条线对 Gemma 4 是死的。

### 三条真实路径

| 方案 | 说明 | 适用性 |
|---|---|---|
| **A. 切 LiteRT-LM SDK** | 替换 MediaPipe 依赖 → `com.google.ai.edge.litertlm:litertlm-android`,重写 Engine 为 `LiteRtLmLlmEngine`,模型用 `.litertlm` | **用户选定** |
| B. Android AI Core / Gemini Nano | 接 `AICore` API,小米 14 大概率不支持(主要 Pixel 8/9、三星 S24+) | 不行 |
| C. 退回 Gemma 3 4B | MediaPipe 不动,模型从 `litert-community/gemma-3-4b-it` 拿 `.task` | 备选,5 分钟 |

---

## 下一轮要做:方案 A — LiteRT-LM SDK

### 用户最终决策(2026-05-05 收尾)

- 模型坚持用 Gemma 4 E4B
- 走方案 A
- 用户**没有 HuggingFace 账号**,这是下一轮第一步要解决的阻塞
- 模型 push 到设备后,本地 Linux 残留要清理(用户 Linux 盘剩 76G,3.66GB 不算紧但能省就省)

### 改动清单(下一轮执行)

1. **`gradle/libs.versions.toml`**
   - 删 `mediapipeGenai = "0.10.27"`
   - 删 `mediapipe-tasks-genai`
   - 加 `litertlm-android = "<具体版本号>"`(去 mvn repo 查 `com.google.ai.edge.litertlm:litertlm-android` 当前最新 stable,不要用 `latest.release`)

2. **`app/build.gradle.kts`**
   - `implementation(libs.mediapipe.tasks.genai)` → `implementation(libs.litertlm.android)`

3. **`app/src/main/java/com/kian/khup/core/ai/MediaPipeLlmEngine.kt`** → 重命名为 `LiteRtLmLlmEngine.kt`
   - 实现还是 `LlmEngine` 接口
   - API 从 `LlmInference.createFromOptions(...).generateResponse(prompt)` 改成 `Engine(EngineConfig).initialize() + createConversation().sendMessage(prompt)`
   - 注意 close engine 释放资源(放 lifecycle 或单例销毁时)
   - `MODEL_FILE_NAME = "gemma-4-E4B-it.litertlm"`

4. **`app/src/main/java/com/kian/khup/common/di/AiModule.kt`**
   - `LlmEngine` 的 binding 从 `MediaPipeLlmEngine` 改到 `LiteRtLmLlmEngine`
   - `HybridLlmEngine` 不动(它是抽象之上的)

5. **模型文件流程**
   - 用户先注册 HuggingFace 账号
   - 接受 Gemma license 协议
   - `pip install huggingface_hub` 然后 `huggingface-cli login`
   - `huggingface-cli download litert-community/gemma-4-E4B-it-litert-lm gemma-4-E4B-it.litertlm --local-dir ~/khup-models`
   - `adb push ~/khup-models/gemma-4-E4B-it.litertlm /data/local/tmp/llm/gemma-4-E4B-it.litertlm`
   - 验证 push 成功后,删本地 `~/khup-models/`(以及 HF cache `~/.cache/huggingface/hub/models--litert-community--gemma-4-E4B-it-litert-lm/`)
   - **总共会下到本地两份**(huggingface-cli 默认会进 HF cache,然后再 symlink/copy 到 --local-dir)。两个都要清。或者用 `--local-dir-use-symlinks False` 后清 cache 即可

6. **真机验证**
   - `./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk`
   - 启动 KHUP → Settings → AI 通道 → 模型路径应显示 ✓
   - 进 AI tab → 点🧪 自检按钮 → logcat 看 `KHUP/AI: smoke test result: ...`
   - 第一次加载 Gemma 4 E4B 在 SD 8 Gen 3 上估计 10-30s,生成速度估计 5-15 tokens/s

### 兜底

如果方案 A 真踩到无法解决的坑(LiteRT-LM 版本兼容、so 库 ABI、模型加载崩溃),退方案 C(Gemma 3 4B + MediaPipe),5 分钟切回:`MODEL_FILE_NAME = "gemma-3-4b-it-int4.task"`,从 `litert-community/gemma-3-4b-it` 找 .task 文件,Engine 实现退回原 MediaPipe。

---

## 进度表(截至 2026-05-05 收尾)

| 模块 | 状态 |
|---|---|
| UsageStats 跨天 bug 修复 | ✅ 编译通过,真机未验 |
| AI 配置挪 Settings | ✅ 编译通过,真机未验 |
| Gemma 4 + LiteRT-LM 集成 | ⏳ 下轮做 |
| 干预加强(#5,Accessibility/包名扩展/冷却时间可配) | ⏳ 推迟 |
| 历史 5/4 脏数据清理 | ✅ KhupApplication.onCreate 一次性删 durationMs > 25h 行 |

## 注意事项给下一轮

1. **HuggingFace 账号是阻塞点**,先解决再开干代码
2. `gradle/libs.versions.toml` 当前还有 `mediapipeGenai = "0.10.27"`,改的时候记得**删干净**,别两份 SDK 共存
3. `MediaPipeLlmEngine.kt` 当前的 `MODEL_FILE_NAME = "gemma-4-E4B-it.task"` 是临时改的,下轮重写时直接覆盖,不用回滚
4. `AiChatViewModel.kt` 里的 setProviderMode/setApiBaseUrl 等方法目前是 dead code(UI 不再调),下轮可以顺手删
5. 真机这一轮没装,**所有改动累积未验证**,下轮第一件事应该先 install 现有 APK 看 UsageStats bug 是否真的修好(打开 Dashboard,看今日总用机不再超 24h),再开始 LiteRT-LM 改造
6. 用户 Linux 盘:`/dev/nvme0n1p6 156G 72G used 76G avail`,下载 3.66G 不紧,但 HF cache 不清就会留两份,记得 `rm -rf ~/.cache/huggingface/hub/models--litert-community--*` 清掉

---

## 调试速查(继续沿用 background01.md 的)

```bash
# 看本轮新加的 cleanup 日志
adb logcat -d | grep -E "KHUP/App.*cleanup"

# 看 UsageStats 修复后的同步日志
adb logcat -d | grep "KHUP/UsageStats"

# 安装最新构建
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk

# adb push 模型示例(下轮用)
adb push ~/khup-models/gemma-4-E4B-it.litertlm /data/local/tmp/llm/
adb shell ls -lh /data/local/tmp/llm/
```
