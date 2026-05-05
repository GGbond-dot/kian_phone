# Background 05 — 2026-05-05(晚 / 接 background04)

> 给下一个对话用的上下文。新会话开始时把 `background01..05.md` 一起贴进去。
> 这份覆盖 2026-05-05 晚:**端侧 LLM 跑通了**(从 0.9.0 SIGABRT 一路修到 E2B 多轮稳定输出)。

---

## TL;DR

| 之前 | 现在 |
|---|---|
| SDK `0.9.0-alpha01` 不认 Gemma 4 → `nativeCreateEngine` SIGABRT | SDK `0.11.0` 已加 `Gemma4DataProcessor`,模型能 init |
| Gemma 4 E4B(3.65GB)PSS 飙到 ~8GB → MIUI 6GB 单 app 上限 → SIGKILL | Gemma 4 E2B(2.58GB,CPU mem ~1.7GB)稳过线 |
| 首次冷启动慢:XNNPack cache 写不下 `/data/local/tmp/`(Permission denied) | `cacheDir = context.cacheDir/litertlm`,允许持久化 cache |

**当前状态:多轮对话不崩,响应 5-7s/条,模型本地跑得动。**

---

## 关键修改

### 1. SDK 升级 `0.9.0-alpha01` → `0.11.0`

`gradle/libs.versions.toml`:
```toml
litertlm = "0.11.0"
```

**为什么 0.9 跑不了**:解 .so `strings` 看 `LlmModelType` 枚举,0.9 里只有 `Gemma3 / Gemma3N / FunctionGemma / TinyGemma / Qwen2.5 / Qwen3 / GenericModel`,**没有 Gemma4**;0.11 里有 `Gemma4DataProcessor` / `Gemma4 LlmModelType is required` / `litert.lm.proto.Gemma4`。这是 background04 的"假设 2"命中。

### 2. 0.11 API 改动适配(`LiteRtLmLlmEngine.kt`)

```kotlin
// Backend 从 enum 变 sealed class,需要 ()
backend = Backend.CPU()         // 0.9 是 Backend.CPU
visionBackend = Backend.CPU()
audioBackend = Backend.CPU()

// reply.contents 从 List<Content> 变成 Contents 包装类
reply.contents.contents.filterIsInstance<Content.Text>()...

// Conversation.sendMessage(String) 直接传字符串
conv.sendMessage(prompt)        // 不再需要 Message.of(prompt)
```

`EngineConfig` 现在签名:`(modelPath, backend, visionBackend, audioBackend, maxNumTokens, maxNumImages, cacheDir)`。

### 3. 模型从 E4B 换 E2B(决定性的一步)

`MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"` (2.58GB)
来源:`huggingface.co/litert-community/gemma-4-E2B-it-litert-lm`

**为什么必须换** — 看实测 PSS:
```
E4B: pss used: 8503809kb / threshold: 6291456kb → SIGKILL
```
MIUI/HyperOS 的 `StabilityLocalService` hardcode 了第三方前台 app **6GB PSS 阈值**,跟设备总内存(16G)无关、**跟"无限制后台"开关也无关**(那个只影响冻结)。E4B 静态权重 + audio/vision adapter + XNNPack delegate 加起来必过线。

E2B 实测 multi-turn 不崩。HF benchmark(S26 Ultra,小米 14 八折):
| | E4B | **E2B** |
|---|---|---|
| 文件 | 3.65GB | 2.58GB |
| CPU 内存 | 3283MB | 1733MB |
| Prefill | 195 tok/s | **557 tok/s** |
| Decode | 17.7 tok/s | **46.9 tok/s** |
| TTFT | 5.3s | **1.8s** |

### 4. `maxNumTokens = 2048`

E4B 时试图压 KV cache,实测**只省 ~250MB**(8503 → 8245),无法救 E4B。但保留给 E2B,长对话场景也防意外。

### 5. `cacheDir` 指到可写目录

```kotlin
val cacheDir = File(context.cacheDir, "litertlm").apply { mkdirs() }
EngineConfig(..., cacheDir = cacheDir.absolutePath)
```

之前模型在 `/data/local/tmp/llm/`,app 没写权限,XNNPack 每次冷启动重编 delegate(首启慢的主因)。改完**第二次冷启动应明显加速**(下轮验证)。

### 6. 残留(可清理但无害)

```kotlin
runCatching { Engine.Companion.setNativeMinLogSeverity(LogSeverity.VERBOSE) }
```
0.11 上仍未让 native log 变 verbose(abort 前没新行),可删。

---

## 设备状态

- 设备:`7c14a351`(小米 14 / HyperOS,SD8Gen3,16G RAM)
- `/data/local/tmp/llm/`:
  - `gemma-4-E2B-it.litertlm` 2.4G ✓ 当前在用
  - `gemma-4-E4B-it.litertlm` 3.4G(可删省 3.4G:`adb shell rm /data/local/tmp/llm/gemma-4-E4B-it.litertlm`)
- 本地 `~/khup-models/`:E2B + E4B 都在
- App `cacheDir` 下会有 `litertlm/` 子目录(第一次跑后产生 XNNPack cache)

---

## 真机已验证

聊了 4-5 轮,response time 5-7s/条:
```
chat started → chat result: 我是一个本地 AI 助手...(完整 markdown 输出)
chat started → chat result: 当然可以。请告诉我你希望我叫什么名字。
chat started → chat result: 好的,从现在开始,你可以叫我小kian。
chat started → chat result: 我目前无法直接查看您的个人信息记录...
```
没有 SIGABRT,没有 PSS kill,UI 状态正常切换。

---

## 下一轮要做(优先级排序)

### 1. 验证 XNNPack cache 是否生效(5min)

冷启动两次,对比时间:
- 第一次冷启动(cache 还没建):预期跟之前差不多
- 第二次冷启动(从最近任务划掉再开):**预期 TTFT 显著下降**

如果第二次没快,说明 0.11 SDK 对 cacheDir 的实现没用上,要去看源码或换思路(预加载 / WorkManager 在 app 启动就 init engine)。

### 2. 体感优化:engine 预加载

当前 engine 是 lazy init,用户切到 AI tab 输第一句才开始加载 → 看着像"卡住"。可以:
- App 进前台时就 trigger `KhupApplication` / AiViewModel 初始化 engine(别人切 tab 时已经热好)
- 或加 loading 状态:模型加载中显示 "模型加载中..." 而不是空白/卡住

### 3. 清掉无效代码

`setNativeMinLogSeverity(LogSeverity.VERBOSE)` 这行 0.11 上不生效,删掉。

### 4. 真正的业务接入:通知摘要 prompt

E2B 跑通了,接下来才是 KHUP 的本职工作。`UsageStatsCollector` / 通知聚合数据 → prompt → 摘要。Prompt 模板要定:输入格式、输出长度限制、隐私脱敏(模型纯本地,但 prompt 里可能含敏感字段)。

### 5. HybridLlmEngine / API 回退路径未测

`ApiLlmEngine.kt + HybridLlmEngine.kt + AiSettingsRepository.kt + AiModule.kt` 都已经写了但没真测过。设置里能切吗?`KEY_PROVIDER` 切到 API 时网络调用走通了吗?下轮验证。

### 6. (低优)`abiFilters arm64-v8a`

`build.gradle.kts` 没限制 ABI,APK 里其实只装了 arm64 的 .so(litertlm AAR 自带 arm64 + x86_64,小米 14 只用 arm64)。加上 abiFilter 可以让 APK 更瘦,优先级低。

### 7. (低优)`.venv/` 加 .gitignore

git status 里一直显示,无害但碍眼。

---

## 注意事项

1. **MIUI 6GB PSS 阈值是硬性的**,不是给"无限制"开关就能放开。任何超过这个的本地模型在小米机上都会被杀。下轮如果想试 E4B / 更大模型,先确认权重 + 运行时内存 < 5GB。

2. **0.11 native log severity 调用看着没生效** —— `Engine.setNativeMinLogSeverity(VERBOSE)` 在 0.9 和 0.11 都没让 abort 之前多打日志。如果以后又遇到 native crash 又看不到原因,先去 .so 里 `strings | grep CHECK\|FATAL` 暴力匹配,比调 SDK API 快。

3. **E2B 模型质量比 E4B 弱一档**(参数少一半多)。复杂多步推理 / 长上下文表现会差。摘要、问答这种短输入短输出场景没问题。如果发现质量不够,要么:
   - 等 Google 出 E3B 中间档 / 量化更优的 E4B
   - 或用 hybrid:本地 E2B 跑日常,API 跑复杂任务

4. **第一次启动慢是 expected 的**,模型加载本来就要 5-10s。验证 #1 和 #2 之后再判断要不要做更多优化。
