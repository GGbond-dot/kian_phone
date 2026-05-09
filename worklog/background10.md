# Background 10 — 2026-05-07

> 本轮补上每日复盘基础闭环，并按用户反馈把首页改成模块标签页。

---

## 本轮目标

用户反馈 22:00 没收到每日总结。排查结果：

- `daily_review` 表和 DAO 已存在。
- 但没有 `DailyReviewWorker`。
- `WorkScheduler.scheduleAll()` 没有注册 22:00 每日复盘任务。

因此补齐：

- 每日复盘生成与入库
- 每日复盘通知
- 22:00 调度
- Dashboard 今日复盘卡片 + 手动重新生成
- 首页模块标签化，并去掉跟“消息”页重复的通知模块

---

## 新增

```text
app/src/main/java/com/kian/khup/core/summary/DailyReviewGenerator.kt
app/src/main/java/com/kian/khup/core/summary/DailyReviewNotifier.kt
app/src/main/java/com/kian/khup/common/work/DailyReviewWorker.kt
worklog/background10.md
```

### DailyReviewGenerator

输入：

- 今日总用机时长
- 今日 Top 5 App
- 今日主线任务完成情况
- 今日干预记录
- 今日通知数量 + 高频来源
- 今日小时摘要

输出：

- 写入 `daily_review`
- `summary` 存展示文本
- `highlights` 存 JSON：亮点 / 警示 / 明日建议
- `modelVersion = daily-review-v1`

LLM 走 `TaskTier.Heavy`。如果 API/LLM 失败，会退化成规则版复盘并照常入库，避免 22:00 完全没有结果。

### DailyReviewNotifier

新增通知渠道：

```text
khup_daily_review / 每日复盘
```

生成成功后通知：

```text
今日复盘已生成
```

### DailyReviewWorker

`UNIQUE_NAME = khup.daily_review`

执行：

1. `DailyReviewGenerator.generateToday()`
2. 成功后通知
3. 成功后调用 `WorkScheduler.scheduleNextDailyReview()` 安排下一天

---

## 调度

`WorkScheduler.scheduleAll()` 现在会额外调用：

```kotlin
scheduleDailyReview(context, ExistingWorkPolicy.KEEP)
```

使用 OneTimeWork，而不是 PeriodicWork：

- 计算到下一个 22:00 的 initial delay。
- Worker 成功后用 `ExistingWorkPolicy.REPLACE` 安排下一次。

这样比 24h periodic 更贴近固定 22:00。

真机 WorkManager DB 已验证：

```text
workname: khup.daily_review
state: ENQUEUED
initial_delay: 77311175 ms
```

当时手机时间约 00:32，延迟约 21.5 小时，对齐当天 22:00。

---

## DAO 改动

新增只读查询，供每日复盘生成使用：

```text
AppSessionDao.loadTopUsageSince(...)
DailyTaskDao.loadForDay(...)
ActionLogDao.loadSince(...)
HourlySummaryDao.loadInWindow(...)
DailyReviewDao.findForDay(...)
```

没有变更 Room schema，数据库版本不变。

---

## Dashboard 改动

`DashboardViewModel`：

- 移除首页最近通知订阅。
- 新增 `todayReview: StateFlow<DailyReview?>`。
- 新增 `dailyReviewUiState`。
- 新增 `generateDailyReview()`，用于 Dashboard 手动生成 / 重新生成。

`DashboardScreen`：

- 首页从单个纵向长列表改成顶部 `TabRow`：

```text
概览 / 主线 / 复盘 / 用机
```

- 概览：最近 1 小时 + 算法 App 干预
- 主线：今日主线任务
- 复盘：今日复盘 + 重新生成按钮
- 用机：今日用机 Top 5

按用户反馈移除首页“通知”模块，因为它和底部“消息”页重复。

---

## 验证

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.kian.khup.debug/com.kian.khup.MainActivity
adb logcat -d -b crash
```

结果：

- `assembleDebug` 成功。
- APK 安装成功。
- crash buffer 无内容。
- 首页 UI dump 确认出现顶部标签：概览 / 主线 / 复盘 / 用机。
- UI dump 确认不再出现“通知 / 最近通知”。
- 真机 `daily_review` 表已有今日复盘记录：

```text
dayStartMs=1778083200000
modelVersion=daily-review-v1
```

---

## 注意事项

1. 每日复盘现在是基础版。异常值检测还没接进 prompt，后续第二阶段做完 `attention_anomaly` 后应并入复盘输入。
2. Dashboard 顶部标签目前是本地 `remember` 状态，不跨进程持久化。
3. 手动生成会覆盖当天 `daily_review`，符合之前“重新生成可覆盖”的设计。
4. `@ApplicationContext` 仍有 Kotlin 未来注解目标 warning，不影响编译。

---

## 下一步

继续按 bg08/bg09：

1. 异常值检测：新增 `attention_anomaly` 表和规则检测器。
2. 把异常值并入 Dashboard 概览或复盘输入。
3. 深化每日复盘 prompt：解释今天为什么失控，而不是只总结今天发生了什么。

---

## 2026-05-07 睡前讨论补充

用户准备明天继续做。今晚确认：每日复盘和 AI 对话后续还可以继续加“背景/上下文”来优化，但这件事可以放到后面。当前更适合先补基础能力。

已梳理仍未做好 / 可继续做的功能：

1. **异常值检测**（建议明天优先）
   - App 今日屏前时长明显高于 7 天均值。
   - 22:30 后继续使用高刺激 App。
   - 某类通知或某个来源 1 小时内暴增。
   - 单一来源高频打断。
   - 睡前连续使用短视频 / 社交。
   - 结果进入 Dashboard 概览和每日复盘输入。

2. **每日复盘深化**
   - 当前是基础总结。
   - 后续要解释“为什么偏离主线”“哪个时段最容易失控”“主要诱因是什么”“明天只守哪一条防线”。
   - 等异常值、诱因标签等基础数据更完整后再深化 prompt。

3. **诱因标签**
   - 给通知 / 用机 / 干预记录打标签，例如 algorithmic、social、promotion、task、study/work、emotion_escape。
   - 让复盘和 AI 对话能分析“被什么牵着走”，不只是统计用时。

4. **信息茧房 / 内容偏食干预**
   - 检测连续几天高比例算法内容或同类内容。
   - 给出打开前写目的、限制短视频窗口、替代动作等干预。

5. **AI 对话的数据查询能力**
   - 多会话聊天已经完成，但 AI 还不能稳定查询本地数据。
   - 后续支持问：“今天刷抖音多久？”“这周哪天最失控？”“今天有哪些异常？”“帮我制定明天计划。”

6. **调度可靠性增强**
   - WorkManager 已安排 22:00 复盘，但 Android 省电策略下可能不精确。
   - 后续可在 App 内显示下一次复盘时间、调度状态、失败提示；必要时评估 AlarmManager。

7. **首页概览增强**
   - 现在概览只有最近 1 小时和干预。
   - 后续可变成：今日主线完成度、今日总用机、当前最大异常、复盘状态、一个最重要建议。

明天建议顺序：

1. 先做异常值检测基础版。
2. 再把异常值展示到首页概览。
3. 再把异常值输入每日复盘。

---

## 2026-05-07 继续：异常值检测基础闭环

本轮完成：

1. 新增 `attention_anomaly` 表、DAO、Room v7 schema 和 `MIGRATION_6_7`。
2. 新增 `AttentionAnomalyDetector`，当前规则：
   - App 今日用时明显高于过去 7 天活跃日均。
   - 22:30 后继续使用抖音 / 小红书超过 5 分钟。
   - 某一小时通知总量暴增。
   - 某一小时单一来源高频打断。
3. `UsageStatsCollector` 新增任意时间窗口 Top App 统计，供睡前高刺激 App 检测使用。
4. Dashboard 概览新增“今日异常”卡片，展示异常数量、标题、严重度和细节。
5. `DailyReviewGenerator` 生成前刷新异常检测，并把异常列表写入 prompt；规则 fallback 也会引用主要异常。`modelVersion` 更新为 `daily-review-v2`。
6. 顺手修复真机启动时的前台服务类型问题：
   - `ForegroundAppMonitorService` 从 `dataSync` 改为 `specialUse`，避免 Android 新版本 dataSync 前台服务限时导致崩溃。
   - `startForeground` 失败时只停止服务，不拖垮 App 主进程。

验证：

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c -b crash
adb shell am start -n com.kian.khup.debug/com.kian.khup.MainActivity
adb logcat -d -b crash
adb shell uiautomator dump /sdcard/khup_dump.xml
```

结果：

- `assembleDebug` 成功。
- APK 安装成功。
- 清空 crash buffer 后启动 App，无新 crash。
- UI dump 确认首页概览出现“今日异常”。
- 真机当前识别出 2 项通知异常：
  - `通知在 00:00 集中出现`，55 条。
  - `微信 高频打断`，00:00 这一小时 32 条。

注意：

1. App 用时对比依赖 `app_sessions` 里过去几天的日聚合数据；新装或历史数据不足时不会强行报“高于均值”。
2. 22:30 后规则只有在 22:30 之后刷新 / 生成复盘时才会出现；22:00 定时复盘天然还看不到 22:30 之后的数据。
3. 通知异常现在只做总量和来源频次，尚未按“社交 / 推广 / 算法 / 任务”等诱因分类。

下一步建议：

1. 加诱因标签：把通知、用机、干预记录打成 `algorithmic/social/promotion/task/study/emotion_escape` 等。
2. 将异常卡片从“列出异常”升级为“今日最大风险 + 一个行动建议”。
3. 让 AI 对话能查询 `attention_anomaly`，支持“今天有哪些异常？”。
