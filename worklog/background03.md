# Background 03 — 2026-05-05(下半场)

> 给下一个对话用的上下文。新会话开始时把这份 + `background02.md` + `background01.md` 一起贴进去。
> 这份覆盖 2026-05-05 下半场:验证了上轮 UsageStats / Settings 改动,然后做了 LiteRT-LM 集成的代码 + 工具链改造。
> **当前状态:代码 + APK 都准备好了,只差 Gemma 4 模型文件 push 到设备。**

---

## 本轮起点

用户已经验证了 background02 的两个待验改动:
- ✅ UsageStats 跨天 bug 修好(今日总用机不再超 24h)
- ✅ AI 配置卡片在 Settings 页正常显示

且用户已注册 HuggingFace 账号(下载 Gemma 4 的阻塞解除)。

本轮主线:**方案 A — 把 MediaPipe 替换成 LiteRT-LM SDK,代码侧全部就绪**。

---

## 已完成 1:依赖切换 + 工具链整体升级

### 起因

- 计划只换 `mediapipe-tasks-genai` → `litertlm-android`,结果 LiteRT-LM 0.9.0-alpha01 这个 aar 是用 Kotlin 2.2 编译的,带 metadata 版本 2.2.0
- Hilt 2.53 内嵌的 kotlinx-metadata-jvm 解析器只到 2.1.0,首次编译炸:`Provided Metadata instance has version 2.2.0, while maximum supported version is 2.1.0`
- 升 Hilt → 触发 KSP 兼容问题 → 升 KSP → 触发 Kotlin 版本绑定 → 升 Kotlin → 触发 Room XProcessing 兼容 → 升 Room

最终一连串升级(全部已编译 + 安装通过):

### `gradle/libs.versions.toml` 改动

```toml
kotlin   = "2.1.0"        →  "2.2.20"
ksp      = "2.1.0-1.0.29" →  "2.2.20-2.0.4"
hilt     = "2.53"         →  "2.57.2"
room     = "2.6.1"        →  "2.7.2"
mediapipeGenai = "0.10.27" 这一行整个删掉
litertlm = "0.9.0-alpha01" 新增

# [libraries] 段
mediapipe-tasks-genai 整行删掉
litertlm-android = { module = "com.google.ai.edge.litertlm:litertlm-android", version.ref = "litertlm" } 新增
```

### 版本组合的踩坑记录(给下一轮避坑)

| 组合 | 结果 |
|---|---|
| Hilt 2.59.x | ❌ 强制要求 AGP 9.0+,我们 AGP 8.7.3 不能用 |
| Kotlin 2.2.0 + KSP 2.2.0-2.0.2 + Hilt 2.57.2/2.58 + Room 2.6.1 | ❌ KSP 报 `unexpected jvm signature V`,Room 2.6.1 的 XProcessing 不认 KSP 2.x 输出 |
| Kotlin 2.2.20 + KSP 2.2.20-2.0.4 + Hilt 2.57.2 + Room 2.7.2 | ✅ 当前可用组合 |

**Hilt 2.57 的关键变化**:把 kotlinx-metadata-jvm 从 shade 改成普通依赖(unshade),所以才能跟上 Kotlin 2.2 的 metadata 格式。Hilt 2.56 及更早即使升 KSP 也会卡 metadata 版本。

### `app/build.gradle.kts` 改动

```kotlin
// 端侧 LLM:LiteRT-LM 跑 Gemma 4 E4B .litertlm 模型。
implementation(libs.litertlm.android)
// 老的 implementation(libs.mediapipe.tasks.genai) 已删
```

### Room 2.6.1 → 2.7.2 注意

`fallbackToDestructiveMigration()` 仍然无参调用,2.7.0 加的 `dropAllTables` 参数我们没用,所以 `DatabaseModule.kt` 不需要改。

---

## 已完成 2:`MediaPipeLlmEngine.kt` → `LiteRtLmLlmEngine.kt`

### 摸 API 的方式

LiteRT-LM 文档跟 0.9.0-alpha01 实际 API 对不上(文档说 `Backend.CPU()`,实际是 enum;文档示例 `sendMessage("string")`,实际签名是 `sendMessage(Message): Message`)。

最快办法:`./gradlew assembleDebug` 第一次编译失败后,aar 已落到 gradle cache:

```bash
find ~/.gradle/caches -name "litertlm-android*0.9.0-alpha01*api.jar"
# 然后 jar xf + javap -public 看真实签名
```

### 实际 API(0.9.0-alpha01)

```kotlin
import com.google.ai.edge.litertlm.Backend          // enum: CPU / GPU / NPU
import com.google.ai.edge.litertlm.Engine           // implements AutoCloseable
import com.google.ai.edge.litertlm.EngineConfig     // ctor(modelPath, backend, visionBackend?, audioBackend?, maxNumTokens?, cacheDir?)
import com.google.ai.edge.litertlm.Conversation     // implements AutoCloseable
import com.google.ai.edge.litertlm.Message          // 用 Message.of("text") 工厂构造
import com.google.ai.edge.litertlm.Content          // sealed: Text / ImageBytes / ImageFile / AudioBytes / AudioFile

val engine = Engine(EngineConfig(modelPath = path, backend = Backend.CPU))
engine.initialize()  // 文档说 10s+,Gemma 4 E4B 实测预计 10-30s

engine.createConversation().use { conv ->
    val reply: Message = conv.sendMessage(Message.of(prompt))
    val text = reply.contents.filterIsInstance<Content.Text>()
        .joinToString("") { it.text }
}
```

注意:`Conversation.sendMessageAsync(...)` 返回 `Flow<Message>` 可以做流式输出,我们当前没用,后续要做"边生成边显示"再切。

### `LiteRtLmLlmEngine.kt` 当前实现要点

- `@Singleton @Inject`,`Engine` 单例缓存,只在 modelPath 变了才重建并 close 旧的
- 用 `Mutex` 串行化 generate,防止两次并发调用同时初始化引擎
- `MODEL_FILE_NAME = "gemma-4-E4B-it.litertlm"`
- 候选路径(按优先级):
  1. `context.filesDir/models/`
  2. `context.getExternalFilesDir("models")/`
  3. `/data/local/tmp/llm/`(下一步 adb push 走这条)
- `runSmokeTest()` / `generate(prompt)` 都返回 `Result<String>`,失败走 `onFailure` 写 logcat `KHUP/AI`

### 旧 `MediaPipeLlmEngine.kt` 已删除。

---

## 已完成 3:其他配套改动

1. **`AndroidManifest.xml`** —— `<application>` 内加:
   ```xml
   <uses-native-library android:name="libvndksupport.so" android:required="false"/>
   <uses-native-library android:name="libOpenCL.so" android:required="false"/>
   ```
   官方 doc 要求,GPU 后端可选依赖。CPU 后端没 GPU 也能跑。

2. **`HybridLlmEngine.kt`** —— `private val localEngine: MediaPipeLlmEngine` → `LiteRtLmLlmEngine`,其余逻辑不动。`AiModule.kt` 不需要改(它 binds 的是 `HybridLlmEngine` → `LlmEngine`)。

3. **`AiChatViewModel.kt`** —— 删 4 个 dead setter(`setProviderMode` / `setApiBaseUrl` / `setApiKey` / `setApiModel`)以及 `import AiProviderMode`。这些 background02 #4 提示的待清理项,UI 已经迁到 `SettingsViewModel`。

---

## 已完成 4:验证(编译 + 安装)

```bash
./gradlew assembleDebug   # BUILD SUCCESSFUL in 46s
adb install -r app/build/outputs/apk/debug/app-debug.apk   # Success
```

**没装模型,所以 AI tab 自检按钮一定会失败**(报"没找到模型文件…")。这是预期。

警告(可忽略):
- `kotlin.SynchronizedLazyImpl` 那种内部警告,Kotlin 2.2 的标准噪音
- `KT-73255`:Kotlin 2.2 对 `@Inject` annotation target 有迁移建议,不影响运行
- `MOVE_TO_FOREGROUND` deprecated 老警告,保留

---

## 进度表(截至 2026-05-05 收尾)

| 模块 | 状态 |
|---|---|
| LiteRT-LM SDK 接入 | ✅ 代码 + APK 都好,只差模型 |
| Kotlin/KSP/Hilt/Room 工具链升级 | ✅ |
| Gemma 4 E4B 模型 push 到设备 | ⏳ 用户的 HF 账号下载,下一步 |
| 真机自检(AI tab → 🧪) | ⏳ push 完才能验 |
| 干预加强(#5) | ⏳ 推迟 |

---

## 下一轮要做(顺序很重要)

### Step 1:用户在本地下载 Gemma 4 模型

前置:HF 账号已注册,**且需要先在 huggingface.co/litert-community/gemma-4-E4B-it-litert-lm 页面点同意 Gemma license**(否则 download 会 401)。

```bash
pip install huggingface_hub
huggingface-cli login   # 粘 HF access token

mkdir -p ~/khup-models
huggingface-cli download litert-community/gemma-4-E4B-it-litert-lm \
  gemma-4-E4B-it.litertlm \
  --local-dir ~/khup-models \
  --local-dir-use-symlinks False
# 文件 ~3.66 GB
```

### Step 2:push 到设备 + 清本地

```bash
adb push ~/khup-models/gemma-4-E4B-it.litertlm /data/local/tmp/llm/
adb shell ls -lh /data/local/tmp/llm/gemma-4-E4B-it.litertlm   # 确认 ~3.66G

# 清本地省盘(用户 Linux 盘 76G 剩余)
rm -rf ~/khup-models
rm -rf ~/.cache/huggingface/hub/models--litert-community--gemma-4-E4B-it-litert-lm
```

### Step 3:真机自检

1. 启动 KHUP
2. 进 Settings → 滑到底「AI 通道」卡片 → 模型路径应显示 ✓(文件存在)
3. 切回 AI tab → 点 🧪 自检按钮
4. `adb logcat -s KHUP/AI:V` 看实际:
   - 第一次会有 `loading LiteRT-LM engine from ...` 然后等 10–30s
   - 成功:`smoke test result: 本地模型已经可以运行`(或类似)
   - 失败:看 stacktrace,常见可能:
     - `UnsatisfiedLinkError` —— LiteRT-LM 的 .so 在小米 14 ABI(arm64-v8a)上加载失败,可能要在 build.gradle 加 abiFilters
     - `Out of memory` —— Gemma 4 E4B 在 12GB RAM 上理论够,但 KHUP 进程被系统 OOM kill 时 logcat 会有 lowmemorykiller
     - `IllegalArgumentException: invalid model file` —— 文件 push 损坏,重新 push

### Step 4:跑通后的小优化(优先级低)

- `LiteRtLmLlmEngine` 当前是阻塞式 `sendMessage`,UI 显示"生成中..." 但等的久,可以改 `sendMessageAsync(): Flow<Message>` 流式输出
- 模型路径目前是写死优先级,Settings 里可以加个"重新扫描模型"按钮(已有 `refreshAiModelState` 后端)
- GPU 后端:`Backend.GPU` 也是 enum,试试小米 14 Adreno 750 能不能加速,但要先确认 OpenCL 装载

### 兜底(如果 LiteRT-LM 真踩到无法解决的坑)

退方案 C:Gemma 3 4B + MediaPipe。本轮已经把 MediaPipe 依赖删干净了,要回退就反向改 `libs.versions.toml` 加回 `mediapipeGenai = "0.10.27"`,Engine 文件按 background02 末尾的 MediaPipe 样板重写,模型从 `litert-community/gemma-3-4b-it` 找 `.task` 文件。整个工具链升级(Kotlin 2.2 / Hilt 2.57 / Room 2.7)可以保留,不会冲突。

---

## 注意事项给下一轮

1. **HF download 大概率会报 license 拒绝** —— 必须先在 HF 网页上点同意,光登录不够
2. **首次 `Engine.initialize()` 要 10–30 秒**,UI 上当前的 spinner 看起来像卡死,正常。logcat 看 `loading LiteRT-LM engine from` 那行是不是出现了
3. **小米 14 是 SD 8 Gen 3 + 12GB RAM**,Gemma 4 E4B(~3.66GB)在内存里完全装得下,理论 5-15 tokens/s
4. KSP 警告 `No dependencies reported for generated source ... willprevent incremental compilation` 是 Hilt 2.57 已知 bug,不影响构建,Google issue tracker 有
5. `AiChatViewModel` 已经清理 dead code(完成 background02 第 4 项),不用再处理
6. Room 升到 2.7.2 后 schema 行为没变,现有 migrations(MIGRATION_2_3 / MIGRATION_3_4)不动

---

## 调试速查(继续沿用前两份)

```bash
# AI 模块 logcat
adb logcat -s KHUP/AI:V

# 看模型文件是否在设备上
adb shell ls -lh /data/local/tmp/llm/

# 完整 KHUP 日志
adb logcat -d | grep -iE "KHUP/|KhupApplication" | tail -100

# 装最新 APK
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```
