# Background 04 — 2026-05-05(深夜)

> 给下一个对话用的上下文。新会话开始时把这份 + `background03.md` + `background02.md` + `background01.md` 一起贴进去。
> 这份覆盖 2026-05-05 深夜:模型 push 完成 + 真机自检触发 LiteRT-LM native crash,**没修完**,留给下一轮。

---

## 本轮起点

background03 收尾时:代码 + APK 都好了,只差 Gemma 4 模型文件。本轮用户已下载好模型 `~/khup-models/gemma-4-E4B-it.litertlm`(3.5G)。

---

## 已完成

### 1. 模型 push 到设备

```bash
adb shell mkdir -p /data/local/tmp/llm/
adb push ~/khup-models/gemma-4-E4B-it.litertlm /data/local/tmp/llm/
# 1 file pushed, 39.6 MB/s, 88s
adb shell ls -lh /data/local/tmp/llm/gemma-4-E4B-it.litertlm
# -rw-rw-rw- 1 shell shell 3.4G ✓
```

### 2. 装 APK 并启动

`adb install -r app/build/outputs/apk/debug/app-debug.apk` Success。
进程能起来(pid 25182 持续存活)。

---

## 关键发现:LiteRT-LM 在 Engine.initialize() 里 native abort 了 3 次

### 现象

用户切 AI tab 应该自动触发了 model status 检查 / engine 初始化(没按🧪自检按钮 —— 后面才告诉用户按钮是 AI tab 右上角的试管图标 Icons.Outlined.Science)。结果 KHUP 连续崩 3 次,系统 ActivityManager 报 `Killing pid:com.kian.khup.debug (adj 0): crash`。

### 崩溃栈(关键帧,从 `adb logcat -b crash`)

```
signal 6 (SIGABRT), code -1 (SI_QUEUE)
#00 abort+160                                    libc.so
#01-#08                                          liblitertlm_jni.so (各种内部偏移)
#09 Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine+1072
#16 com.google.ai.edge.litertlm.Engine.initialize
#21 com.kian.khup.core.ai.LiteRtLmLlmEngine.getOrCreateEngine
#31 com.kian.khup.core.ai.LiteRtLmLlmEngine$generateInternal$2.invokeSuspend
```

**确定是 LiteRT-LM 自己 abort,不是 OOM kill**(LMK 走 SIGKILL,这里是 SIGABRT)。

### 已收集的 native log(abort 之前)

`litert_lm_loader.cc` 把模型 8 个 section 全部解析成功:

| section | model_type | backend |
|---|---|---|
| 3 | tf_lite_per_layer_embedder | - |
| 4 | tf_lite_audio_encoder_hw | cpu |
| 5 | tf_lite_audio_adapter | cpu |
| 6 | tf_lite_end_of_audio | - |
| 7 | tf_lite_vision_encoder | - |
| 8 | tf_lite_vision_adapter | cpu |

**模型文件本身 OK,模型有 audio + vision 多模态 section。**

abort 之前 242ms 没打 FATAL 原因(LiteRT-LM 0.9.0-alpha01 的诊断挺差,abort 没 message)。

### 系统 OOM 的旁证(可能相关也可能不相关)

`15:52:08` lmkd 报 "critical pressure and device is low on memory",杀了一堆其他进程(qiyi/baidu/miui.tsmclient 等)。但 KHUP 自己是 SIGABRT 不是 SIGKILL,所以 **OOM 不是直接死因**,可能只是 engine 初始化时分配大块内存触发系统压力。

---

## 两个假设(下轮先验证)

### 假设 1:多模态 section 跟 EngineConfig 不匹配(优先)

模型有 audio + vision section,但 `LiteRtLmLlmEngine.kt` 当前传:

```kotlin
EngineConfig(modelPath = path, backend = Backend.CPU)
// visionBackend/audioBackend 没传,默认 null
```

可能 0.9.0-alpha01 在加载到 vision/audio section 时强制要求显式设置 backend,不设就 abort。

**修复尝试**:

```kotlin
EngineConfig(
    modelPath = path,
    backend = Backend.CPU,
    visionBackend = Backend.CPU,
    audioBackend = Backend.CPU,
)
```

或者反过来 —— 只用纯文本模型(去 HF 找 text-only 版本),但 litert-community 仓库里只有 `gemma-4-E4B-it.litertlm` 一个文件,没 text-only。

### 假设 2:`.litertlm` 格式版本跟 0.9.0-alpha01 SDK 不兼容

HF 上的模型可能是更新格式,SDK 还没跟上。这种没法本地修,只能等 SDK 更新或退方案 C(Gemma 3 4B + MediaPipe)。

---

## 下一轮要做(顺序)

### Step 1:试假设 1,改 LiteRtLmLlmEngine.kt

在 `getOrCreateEngine()` 构造 `EngineConfig` 那里加上 `visionBackend = Backend.CPU, audioBackend = Backend.CPU`。重编 APK,再装,再切 AI tab。

如果还是同样 SIGABRT 在 `nativeCreateEngine`,继续 Step 2。

### Step 2:抓更详细的 native log

LiteRT-LM 用 absl/glog,可能要设环境变量提高日志级别:

```bash
adb shell setprop log.tag.native VERBOSE
adb shell setprop log.tag.litertlm VERBOSE
# 或者 setprop GLOG_v 2
```

也可以试试在 Engine 创建前调 `LiteRtLm.setLogLevel(LogLevel.VERBOSE)` 之类(查 SDK 真实 API)。

### Step 3:如果都不行,退方案 C(Gemma 3 4B + MediaPipe)

background02 末尾 + background03 末尾都有详细回退路径。5 分钟切回。

---

## 调试一手数据(下轮可直接用)

### 当前设备状态

- 设备:`7c14a351`(小米 14 / HyperOS)
- KHUP 进程:pid 25182(还活着,因为 crash 后系统自动重启了 Activity)
- 模型:`/data/local/tmp/llm/gemma-4-E4B-it.litertlm` 已 push,3.4G
- 本地源文件:`/home/kian/khup-models/gemma-4-E4B-it.litertlm` 还在,3.5G(下轮如果不需要可以删省盘)

### 抓崩溃的命令

```bash
# 主 logcat 看 KHUP/AI 自己的 log
adb logcat -s KHUP/AI:V

# 看 native crash 栈(关键)
adb logcat -d -b crash | grep -E "DEBUG|tombstone" | grep -E "signal|Cmdline|Abort message|backtrace:|#00 |#01 " | tail -40

# 看 LiteRT-LM native log(在 abort 前)
adb logcat -d | grep -iE "litertlm|tflite|odml|absl|CHECK failed" | tail -60

# 看是不是 OOM kill
adb logcat -d | grep -iE "lowmemorykiller|killing.*khup" | tail -20
```

### 自检按钮位置(用户问过)

AI tab 顶部右上角,**试管图标**(`Icons.Outlined.Science`),contentDescription 是"运行自检"。三个图标从左到右:Refresh / Science / Delete。

---

## 进度表(截至 2026-05-05 深夜)

| 模块 | 状态 |
|---|---|
| 模型 push 到设备 | ✅ |
| APK 安装 | ✅ |
| Engine.initialize() 跑通 | ❌ native SIGABRT,未解决 |
| 真机自检通过 | ⏳ 卡在 init |
| 干预加强(#5) | ⏳ 推迟 |

---

## 注意事项给下一轮

1. **崩溃是真崩,不是用户误判** —— 用户那句"好像会自己闪退"是随口说的,但 logcat 确实有 3 次 SIGABRT。下轮不要怀疑这个事实。
2. **abort 没 message 是 LiteRT-LM 0.9.0-alpha01 的痛点**,先试改 EngineConfig,再调 log level。
3. **不要急着退方案 C**,先确认假设 1 是不是真因。如果改完还崩,再退也来得及。
4. **可能要加 `abiFilters arm64-v8a`**:`build.gradle.kts` 当前没限制 ABI,但小米 14 是纯 arm64-v8a,不影响 .so 加载,这条优先级低。
5. 用户的 `.venv/` 还在 git status 里(huggingface_hub 装的),不影响开发但可以加 `.gitignore`。
