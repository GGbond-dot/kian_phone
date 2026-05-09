# Background 07 — 2026-05-06

> 给下一个对话用的上下文。新会话开始时把 `background01..07.md` 一起贴进去。
> 这份覆盖 2026-05-06 后续收尾：P4 每小时通知摘要真机跑通、Dashboard 摘要卡补齐、UsageStats 总时长口径修正为屏幕点亮时长、API 调用超时拉到 30s。

---

## 本轮起点

用户给出的进度：

```text
5 tasks (4 done, 1 in progress, 0 open)
✔ P1: Retention 7→3 days
✔ P2: Rules v2 + finance classification
✔ P3: Persist AI chat history
◼ P4: Hourly notification summary
✔ DB: Migrate Room v4→v5
```

本轮先阅读了 `worklog/background01..06.md`，确认第一波里只剩 P4 收尾。代码里 P4 主体已经存在：

- `HourlySummaryWorker`
- `HourlySummaryDao`
- `HourlySummary`
- `HourlySummaryNotifier`
- `WorkScheduler` 每小时注册
- `DashboardViewModel.latestHourlySummary`

但 Dashboard UI 没有 collect / 展示 `latestHourlySummary`，所以摘要生成后首页看不到。

---

## 已完成 1：P4 每小时通知摘要收尾

### 改动文件

`app/src/main/java/com/kian/khup/output/ui/dashboard/DashboardScreen.kt`

新增首页顶部卡片：

- collect `viewModel.latestHourlySummary`
- `DashboardContent` 接收 `latestHourlySummary`
- 新增 `HourlySummaryCard`
- 展示：
  - 标题：`最近 1 小时`
  - 摘要文本
  - 重要程度：普通 / 一般 / 重要 / 紧急
  - 通知数 + 时间窗，例如 `26 条通知 · 16:00-17:00`

### 真机验证

设备：`7c14a351`，包名：`com.kian.khup.debug`。

执行：

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.kian.khup.debug -c android.intent.category.LAUNCHER 1
```

WorkManager 中确认 `HourlySummaryWorker` 已注册，Job trace tag 为 `HourlySummaryWorker`。

强制触发后日志：

```text
KHUP/AI : tier=Light → local
KHUP/AI : loading LiteRT-LM engine from /data/local/tmp/llm/gemma-4-E2B-it.litertlm
KHUP/AI : chat result: {"summary":"主要由用户“刘家欢”的频繁消息和一些应用（如微信、抖音）的通知构成打扰，其中包含一些互动和提醒性质的内容。","importance":1,...}
KHUP/HourlySummary: summary stored: window=1778054400000 importance=1 events=26
WM-WorkerWrapper: Worker result SUCCESS ... HourlySummaryWorker
```

UI tree 确认首页显示：

```text
最近 1 小时
一般
主要由用户“刘家欢”的频繁消息和一些应用（如微信、抖音）的通知构成打扰，其中包含一些互动和提醒性质的内容。
26 条通知 · 16:00-17:00
```

P4 当前状态：代码、调度、生成、入库、首页展示均已真机通过。重要提醒白名单逻辑已在代码里，但本轮没有真实学习通 / 钉钉高重要通知样本去验证推送弹出。

---

## 已完成 2：UsageStats “25h 用机” bug 修正

### 用户指出的问题

用户发现 2026-05-06 今日总用机显示 `25h`，这是物理上不可能的。

本轮真机 UI tree 也看到了：

```text
今日总用机 25小时24分
05/06 25h
```

### 根因

之前修复只解决了部分跨天问题，但总时长口径仍有两个问题：

1. `UsageStatsRepository.observeTodayTotal()` 从 `app_sessions` 里把各 App `durationMs` 直接求和。
2. Android / MIUI 的 UsageEvents 会出现重叠前台区间、系统桌面残留、未配对 foreground/background 等情况。

结果就是：单个 App 被 clamp 到“今天已过去时间”以内，但多个 App 一起求和仍然可以超过 24h。

更进一步，用户指出真正应该关注的是**屏幕使用时长**，不是后台挂着多久。这个判断是对的。

### 最终口径

现在三块数据的口径统一为：

| UI | 现在口径 |
|---|---|
| 今日总用机 | 今日 `SCREEN_INTERACTIVE → SCREEN_NON_INTERACTIVE` 的屏幕点亮总时长 |
| 近 7 天趋势 | 每天屏幕点亮总时长 |
| 今日 App 排行 | 只在屏幕点亮期间，把时间记给当前前台 App |

也就是说：

- 熄屏后不计入总用机
- 后台常驻不计入 App 排行
- 后台播放 / 通知服务 / 系统残留前台事件不会继续给 App 排行累计
- App 排行不是“后台运行时长”，而是“屏幕点亮期间前台占用时长”

### 改动文件

1. `app/src/main/java/com/kian/khup/collection/usage/UsageStatsCollector.kt`
   - 新增 `getScreenInteractiveMs(startMs, endMs)`
   - 使用 `UsageEvents.Event.SCREEN_INTERACTIVE` / `SCREEN_NON_INTERACTIVE` 计算屏幕点亮区间
   - `getTodayTopApps()` 改为只在屏幕点亮期间统计当前前台 App
   - 保留 `getTotalForegroundMs()`，但当前总用机不再用它

2. `app/src/main/java/com/kian/khup/core/data/repository/UsageStatsRepository.kt`
   - `observeTodayTotal()` 改用 `usageStatsCollector.getScreenInteractiveMs(todayStart, now)`
   - `observeDailyTotals()` 改用屏幕点亮时长，而不是 `app_sessions` 求和
   - 每分钟刷新一次，让当天数值持续更新
   - `cleanupAnomalousSessions()` 清理阈值从 25h 改为 24h

3. `app/src/main/java/com/kian/khup/output/ui/analytics/AnalyticsScreen.kt`
   - 文案从 `来自系统使用情况访问记录` 改成 `来自系统屏幕点亮记录`

### 真机验证结果

编译：

```bash
./gradlew assembleDebug
```

结果：`BUILD SUCCESSFUL`。

安装并打开用机页后：

```text
今日总用机：12小时18分
05/06 趋势：12h
Top App:
浏览器 3小时35分
抖音 2小时29分
微信 1小时25分
KHUP 44分钟
观赛宝 38分钟
...
```

之前不合理的：

```text
今日总用机 25h
单 App 观赛宝 14h
```

已消失。

注意：这个口径会比旧的“App 前台事件求和”小，且更接近小米 / 澎湃系统“屏幕使用时间”的直觉。

---

## 已完成 3：API 模型调用超时拉长到 30s

### 改动文件

`app/src/main/java/com/kian/khup/core/ai/ApiLlmEngine.kt`

Ktor `HttpClient(OkHttp)` 加 `HttpTimeout`：

```kotlin
install(HttpTimeout) {
    requestTimeoutMillis = 30_000L
    connectTimeoutMillis = 30_000L
    socketTimeoutMillis = 30_000L
}
```

目的：API / 中转站 / GPT-5.4 响应慢时，不要过早失败。

本轮只验证编译通过，未专门发一次 API 请求测 30s 行为。

---

## 当前改动文件

```text
M app/src/main/java/com/kian/khup/collection/usage/UsageStatsCollector.kt
M app/src/main/java/com/kian/khup/core/ai/ApiLlmEngine.kt
M app/src/main/java/com/kian/khup/core/data/repository/UsageStatsRepository.kt
M app/src/main/java/com/kian/khup/output/ui/analytics/AnalyticsScreen.kt
M app/src/main/java/com/kian/khup/output/ui/dashboard/DashboardScreen.kt
```

新增本文档：

```text
worklog/background07.md
```

---

## 验证记录

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.kian.khup.debug -c android.intent.category.LAUNCHER 1
adb shell uiautomator dump /sdcard/window.xml
adb shell cat /sdcard/window.xml
adb logcat -d -b crash
```

结果：

- 编译通过
- APK 安装成功
- P4 Worker 成功生成摘要并入库
- Dashboard 摘要卡显示正常
- 用机页总时长 / 7 天趋势 / App 排行改为屏幕点亮口径
- crash log 为空

---

## 后续注意事项

1. App 排行现在依赖 UsageEvents 里的当前前台 App 推断。正常情况下可用，但 MIUI 如果漏事件，单个 App 排行仍可能有小误差。
2. “今日总用机”现在不是 `app_sessions` 求和，而是直接查系统 UsageEvents 的屏幕事件，因此不受历史 `app_sessions` 脏数据影响。
3. `app_sessions` 仍用于 App 排行持久化 / 干预规则评估。因为 `syncToday()` 现在写入的是屏幕点亮前台 App 口径，后续干预阈值也会更接近用户真实看到 App 的时间。
4. `MOVE_TO_FOREGROUND` / `MOVE_TO_BACKGROUND` 仍有 deprecated 编译警告，但为了兼容老事件类型保留，当前不影响构建。
5. API 超时已设为 30s，但如果以后做每日复盘等长任务，可能还需要按任务 tier 设置不同超时。

