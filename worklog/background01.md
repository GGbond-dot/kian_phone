# Background 01 — 2026-05-04

> 给下一个对话用的上下文。新会话开始时把这份贴进去，让 Claude 快速接手。

---

## 项目速览

**KHUP**（Kian Health Use Phone） — 个人 Android app，目录 `/home/kian/kian_phone/`，包名 `com.kian.khup`（debug 构建是 `com.kian.khup.debug`）。

- **目标设备**：仅小米 14（Xiaomi 23127PN0CC，HyperOS）。不做 iOS、不做云、不做跨设备
- **核心功能**：聚合所有 App 通知（微信/QQ/抖音/钉钉/学习通/短信等）→ Room 入库 → 后续接 Gemma 4 端侧 LLM 分类 → 算法干预（抖音/小红书）
- **架构**：3 层事件溯源 — Collection（NLS / UsageStats / 外部 API） → Core（events 表 + AI + 规则） → Output（Compose UI / 提醒 / 干预）
- **权威架构文档**：`/home/kian/kian_phone/ARCHITECTURE.md` —— 改结构前必读对应章节
- **作者背景**：第一次写 Android，Kotlin / Compose / Hilt / Room 全是新的。沟通用中文，技术细节直说，**避免堆术语**

---

## 技术栈关键版本（`gradle/libs.versions.toml`）

- AGP 8.7.3 / Kotlin 2.1.0 / KSP 2.1.0-1.0.29
- Hilt 2.53 + hilt-work 1.2.0
- **Room 2.6.1** —— `fallbackToDestructiveMigration()` 必须**无参**调用，`dropAllTables` 参数是 2.7.0 才加的，写了会编译失败
- WorkManager 2.10.0
- Compose BOM 2024.12.01
- minSdk 33 / targetSdk 35

---

## 本次会话做了什么（Phase 1 的 WorkManager 部分）

### 改动文件

1. **`app/src/main/java/com/kian/khup/common/di/DatabaseModule.kt`**（修 bug）
   - 把 `.fallbackToDestructiveMigration(dropAllTables = true)` 改回 `.fallbackToDestructiveMigration()`
   - 原因：Room 2.6.1 没有 `dropAllTables` 参数

2. **`app/src/main/java/com/kian/khup/common/work/NlsRebindWorker.kt`**（新增）
   - `HiltWorker` + `CoroutineWorker`
   - 每 15 分钟（`PeriodicWorkRequest` 最小间隔）执行 component disable→enable toggle
   - 强制系统重新 bind `MessageListener`，绕过 MIUI 的"假死"NLS（架构文档 §5.1 服务保活）
   - `UNIQUE_NAME = "khup.nls_rebind"`

3. **`app/src/main/java/com/kian/khup/common/work/RetentionWorker.kt`**（新增）
   - 每 24 小时跑，调用 `EventRepository.pruneOlderThan()`
   - 删 7 天前的原始 events
   - `RAW_EVENT_RETENTION_DAYS = 7L`，`UNIQUE_NAME = "khup.retention"`
   - 用户决策：把架构文档原本的 30 天保留期改成 7 天（个人手机 App，老通知没回看价值）

4. **`app/src/main/java/com/kian/khup/common/work/WorkScheduler.kt`**（新增）
   - 单例 `object`，方法 `scheduleAll(context)`
   - 用 `enqueueUniquePeriodicWork(..., ExistingPeriodicWorkPolicy.KEEP, ...)` 注册两个任务
   - 用 `KEEP` 策略，避免每次 App 启动重置周期

5. **`app/src/main/java/com/kian/khup/KhupApplication.kt`**（改）
   - 加了 `onCreate()` 调用 `WorkScheduler.scheduleAll(this)`
   - 还加了一行 `Log.i("KHUP/App", "onCreate: scheduling periodic work")` 用于排错验证（可保留也可删）

### 没做但讨论过

- **没写 BootReceiver** — WorkManager 自带任务持久化，开机后系统会自动恢复（前提是 `RECEIVE_BOOT_COMPLETED` 已声明，已经有了）
- 数据保留策略最终定为：raw events 7 天，derived_results / app_sessions / action_logs 永久（与架构文档 §5.4 不同，以本文档为准）

---

## 测试结果（已通过）

| 测试 | 结果 |
|---|---|
| 多 App 接收（微信/QQ/短信/抖音/钉钉） | ✅ |
| 群消息去重（每条独立显示，无重复回调） | ✅ |
| 重启手机后 NLS 自动 rebind | ✅ |
| 锁屏 30 分钟存活 | ✅（一次过，但 MIUI 不可保证持续） |
| WorkManager 注册 | ✅（logcat 看到两个 unique work + Job ID 0/1） |
| Worker 首次执行 | ✅（`WM-Processor: processing` 日志） |

**已知风险**：MIUI 杀后台不可预测，息屏 30 分钟测一次过 ≠ 永远稳，靠 NlsRebindWorker 每 15 分钟兜底。

**用户已手动开**：通知使用权（含 MIUI 二次确认） + 自启动 + 应用智能省电「无限制」。这些操作要在 Settings tab 里固化成 checklist。

---

## Phase 1 进度

| 模块 | 状态 |
|---|---|
| NLS 监听 + 解析 + 去重 | ✅ |
| Room 数据库 | ✅ |
| Compose 4 tab UI（Dashboard/Messages/Analytics/Settings） | ✅ |
| WorkManager NLS 自检（rebind） | ✅ 本次完成 |
| WorkManager 数据清理 | ✅ 本次完成 |
| **UsageStats 接入** | ✅ Dashboard + Room 持久化 + Worker 同步 |
| **Settings 权限 checklist** | ✅ |

---

## 本轮继续完成：Settings checklist + UsageStats 接入

### 新增/改动文件

1. **`gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.jar`**（新增）
   - 修复命令行没有 Gradle Wrapper 的问题
   - 现在可以直接跑 `./gradlew clean assembleDebug`

2. **`README.md`**（改）
   - 新增「构建 / 重建」说明
   - Studio 找不到 `Rebuild Project` 时，用 `Build → Clean Project` + `Build → Make Project`

3. **`app/src/main/java/com/kian/khup/collection/notification/NotificationPermissions.kt`**（改）
   - 前三项权限跳转增加兜底到 App 详情页
   - 新增 MIUI 自启动入口：`AutoStartManagementActivity`
   - 新增 MIUI 省电策略入口：实测小米 14 / HyperOS 没有可用的单 App 省电策略 Activity，最终改为跳 KHUP 的 MIUI 应用详情页，让用户从「省电策略」手动选「无限制」
   - MIUI 自启动/省电策略无法可靠读取真实状态，增加本地确认状态：用户点进去返回 KHUP 后显示「✓ 已确认」

4. **`app/src/main/java/com/kian/khup/output/ui/settings/SettingsScreen.kt`**（改）
   - Settings 页改成 5 项 checklist：通知使用权、使用情况访问、悬浮窗、MIUI 自启动、MIUI 省电策略
   - 前三项显示真实授权状态，MIUI 两项第一次显示「需手动确认」，用户确认后显示「✓ 已确认」
   - 五项按钮统一为 `OutlinedButton` 风格，已授权/已确认时禁用

5. **`app/src/main/java/com/kian/khup/collection/usage/UsageStatsCollector.kt`**（新增）
   - 封装 `UsageStatsManager`
   - 查询今天 00:00 到现在的各 App 前台时长
   - 聚合 packageName，解析 app label，返回 Top N

6. **`app/src/main/java/com/kian/khup/core/data/db/AppSessionDao.kt`**（新增）
   - `app_sessions` 的 upsert 和 Top usage 查询
   - `packageName + startTime` 改成唯一索引，避免重复同步

7. **`app/src/main/java/com/kian/khup/core/data/repository/UsageStatsRepository.kt`**（新增）
   - 把 UsageStatsCollector 的结果写入 Room
   - Dashboard 读 Room 的今日 Top 5

8. **`app/src/main/java/com/kian/khup/common/work/UsageStatsSyncWorker.kt`**（新增）
   - 每小时同步 UsageStats 到 `app_sessions`
   - 没有 UsageStats 权限时直接跳过

9. **`WorkScheduler.kt` / `DatabaseModule.kt` / `AppDatabase.kt`**（改）
   - 注册 UsageStats worker
   - 提供 AppSessionDao
   - Room version 从 1 bump 到 2，并生成 `app/schemas/.../2.json`

10. **`DashboardViewModel.kt` / `DashboardScreen.kt`**（改）
   - Dashboard 顶部新增「今日用机 Top 5」
   - 未授权时提示去 Settings 开「使用情况访问」
   - 页面 resume 时刷新 UsageStats 并写入 Room

### 验证结果

```bash
./gradlew clean assembleDebug
./gradlew assembleDebug
```

均已通过。最后一次结果：`BUILD SUCCESSFUL in 3s`。

### 真机验证结果（小米 14 / HyperOS）

已连接设备：`23127PN0CC / houji`。

| 验证项 | 结果 |
|---|---|
| APK 安装并启动 | ✅ |
| 通知使用权 | ✅ `MessageListener` 在 `enabled_notification_listeners` 里 |
| 使用情况访问 | ✅ `GET_USAGE_STATS: allow` |
| 悬浮窗权限 | ✅ 用户已开启，Settings 显示已授权 |
| MIUI 自启动 | ✅ 用户已手动开启，本地显示已确认 |
| MIUI 省电策略 | ✅ 用户已设置「无限制」，本地显示已确认 |
| NLS 服务连接 | ✅ logcat: `KHUP/NLS: Listener connected` |
| UsageStats 同步 | ✅ logcat: `KHUP/UsageStats: synced 55 app usage rows` |
| WorkManager 注册 | ✅ JobScheduler 里有 `RetentionWorker` / `NlsRebindWorker` / `UsageStatsSyncWorker` |
| Dashboard 最近通知 | ✅ 用户确认有通知出现 |
| Dashboard 今日用机 Top 5 | ✅ 用户确认有数据出现 |

### 当前限制

- MIUI 自启动/省电策略状态无法可靠读取，需要用户手动确认

---

## 本轮继续完成：Analytics tab + 规则分类消息中心

### Analytics tab 已完成

新增/改动：

1. **`AppSessionDao.kt`**（改）
   - 新增今日总用机时长查询
   - 新增近 7 天每日用机汇总查询

2. **`UsageStatsRepository.kt`**（改）
   - 新增 `observeTodayTotal()`
   - 新增 `observeDailyTotals(days)`

3. **`AnalyticsViewModel.kt`**（新增）
   - 聚合权限状态、今日总时长、今日 Top App、近 7 天趋势
   - 页面 resume 时触发 `syncToday()`

4. **`AnalyticsScreen.kt`**（改）
   - 占位页换成真实页面
   - 顶部展示「今日总用机」
   - 展示「近 7 天趋势」普通 Compose 条形图
   - 展示「今日 App 排行」

验证：

| 验证项 | 结果 |
|---|---|
| `./gradlew assembleDebug` | ✅ |
| APK 安装并启动 | ✅ |
| Analytics 页面打开 | ✅ 用户确认 |
| 今日总用机 / App 排行显示 | ✅ 用户确认 |

### 规则分类 + Messages tab 已完成

目标：先不接 LLM，用规则分类把「事件 → 衍生分类 → 消息中心展示」链路跑通。

新增/改动：

1. **`app/src/main/java/com/kian/khup/core/classification/RuleNotificationClassifier.kt`**（新增）
   - 规则分类器 `rules-v1`
   - 分类：验证码 / 工作 / 社交 / 推广 / 算法推送 / 其他
   - 验证码：关键词「验证码」+ 4-8 位数字
   - 社交：微信 / QQ / Instagram 等包名
   - 算法推送：抖音 / 小红书 / 知乎 / 微博等包名或推荐类关键词
   - 推广：优惠、红包、折扣、直播、上新等关键词
   - 工作：会议、审批、任务、作业、课程、考试、签到等关键词
   - 生成 `DerivedResult`：classification / priority / summary / processedAt / modelVersion

2. **`DerivedResultDao.kt`**（新增）
   - `upsert()` / `upsertAll()`
   - `observeClassifiedEvents(classification, limit)`，联表读取 `events + derived_results`

3. **`EventDao.kt`**（改）
   - 新增 `getUnclassified(limit)`，找还没有 `derived_results` 的历史通知

4. **`EventRepository.kt`**（改）
   - `insert(event)` 成功后立即规则分类并写入 `derived_results`
   - `insertAll(events)` 同样补分类
   - 新增 `classifyUnprocessed(limit)`，给 Messages 页面补跑历史未分类通知

5. **`AppDatabase.kt` / `DatabaseModule.kt`**（改）
   - 暴露 `DerivedResultDao`

6. **`MessageRepository.kt`**（新增）
   - Messages ViewModel 读取分类后的通知列表

7. **`MessagesViewModel.kt`**（新增）
   - 分类筛选状态
   - 打开页面时补跑历史未分类通知
   - 默认选中「社交」
   - 点击消息时调用通知跳转逻辑

8. **`MessagesScreen.kt`**（改）
   - 占位页换成真实分类消息列表
   - 顶部分类 chip 顺序：社交、全部、验证码、工作、推广、算法推送、其他
   - 消息卡片显示标题、分类、摘要、优先级、包名、时间
   - 点击卡片尝试跳转

### 通知点击跳转 MVP 已完成

新增/改动：

1. **`NotificationSnapshot` / `NotificationParser.kt`**（改）
   - 从系统通知中取 `notification.contentIntent`
   - 跟随内存快照传到入库流程

2. **`NotificationLaunchRegistry.kt`**（新增）
   - 进程内缓存 `eventId -> PendingIntent`
   - 点击消息时优先 `PendingIntent.send()`，跳到原通知对应页面
   - 如果 `PendingIntent` 已失效或不存在，退化为打开对应 App

3. **`MessageListener.kt`**（改）
   - 新通知成功入库后，把 `eventId` 和 `contentIntent` 注册到 `NotificationLaunchRegistry`

限制：

- `PendingIntent` 不能可靠存进 Room 长期保存，所以历史消息通常只能打开对应 App。
- 刚收到、系统通知还有效的消息，最有机会跳到原 App 的具体页面（比如微信聊天页）；具体能跳多深取决于原 App 的通知实现。

验证：

| 验证项 | 结果 |
|---|---|
| `./gradlew assembleDebug` | ✅ |
| APK 安装并启动 | ✅ |
| Messages 分类展示 | ✅ 用户确认通过 |
| 分类合理性 | ✅ 用户确认「算合理」 |
| 社交默认/排最前 | ✅ 已实现 |
| 点击跳转 MVP | ✅ 已构建安装，待后续更多通知样本验证具体 App 行为 |

---

## 当前总进度（截至 2026-05-04 收工）

| 模块 | 状态 |
|---|---|
| NLS 通知监听 + 解析 + 双层去重 | ✅ 真机通过 |
| Room `events` 事件表 | ✅ |
| WorkManager：NLS rebind / 数据清理 / UsageStats 同步 | ✅ 真机通过 |
| Settings 权限 checklist | ✅ 真机通过 |
| UsageStats 接入 + `app_sessions` 持久化 | ✅ 真机通过 |
| Dashboard 最近通知 + 今日用机 Top 5 | ✅ 真机通过 |
| Analytics 今日总用机 / Top App / 近 7 天趋势 | ✅ 真机通过 |
| 规则分类 `rules-v1` | ✅ MVP 完成 |
| `derived_results` 分类结果写入 | ✅ MVP 完成 |
| Messages 分类列表 | ✅ 真机通过 |
| Messages 点击跳转原通知 / 原 App | ✅ MVP 完成，需继续积累不同 App 样本 |
| Dashboard 每日主线任务 checklist | ✅ MVP 完成 |
| MNN-LLM / Gemma 端侧模型 | ⏳ 未开始 |
| LLM JSON 输出解析 + 批量推理 WorkManager | ⏳ 未开始 |
| 规则引擎正式配置 / 规则 UI | ⏳ 未开始 |
| 算法干预（抖音 / 小红书提醒或阻断） | ⏳ 未开始 |

## 下一步建议

1. **先继续打磨 Messages 点击跳转**
   - 用微信、QQ、钉钉、短信、学习通分别发新通知测试
   - 记录哪些 App 能跳具体页面，哪些只能打开 App
   - 必要时在 Messages 卡片上区分「可跳原通知」/「只能打开 App」

2. **再做规则分类质量提升**
   - 根据真实通知样本调规则
   - 加更多包名映射和关键词
   - 允许手动改分类，为后续 LLM / 规则训练积累样本

3. **之后进入真正 Phase 2 AI**
   - MNN-LLM 集成
   - Gemma 模型加载
   - Prompt + JSON 输出解析
   - WorkManager 批量推理，把 `modelVersion` 从 `rules-v1` 逐步切到 Gemma 版本

---

## 临时追加：每日主线任务 checklist

背景：

- 用户复盘当天发现手机 App 开发属于“非主线任务”，容易挤占算法 / 比赛 / 项目深挖。
- 新需求不是做复杂日程表，而是把“每日必做清单 + 自由额度”的结构内置进 KHUP。
- 原则：不按小时排时间，只记录今天必须完成什么、完成多少。

实现位置：

- 放在 **Dashboard 首页顶部**
- 标题是「今日主线」
- 目的：每天打开 app 第一眼先看到主线任务，而不是先看通知或用机数据

新增/改动：

1. **`app/src/main/java/com/kian/khup/core/data/db/entities/DailyTask.kt`**（新增）
   - `daily_tasks` 表
   - 字段：`id` / `title` / `dayStartMs` / `isDone` / `createdAt` / `completedAt`
   - 每条任务绑定当天 00:00 的 `dayStartMs`
   - 这样每天自动是新的 checklist，不会把昨天的任务混进今天

2. **`app/src/main/java/com/kian/khup/core/data/db/DailyTaskDao.kt`**（新增）
   - `observeForDay(dayStartMs)`
   - `observeOverdueUnfinished(todayStartMs)`，显示昨天及更早没完成的任务
   - `insert(task)`
   - `setDone(id, isDone, completedAt)`
   - `delete(id)`

3. **`app/src/main/java/com/kian/khup/core/data/repository/DailyTaskRepository.kt`**（新增）
   - `observeTodayTasks()`
   - `observeOverdueUnfinishedTasks()`
   - `addTodayTask(title)`
   - `setDone(taskId, isDone)`
   - `delete(taskId)`
   - 内部计算当天 00:00

4. **`AppDatabase.kt` / `DatabaseModule.kt`**（改）
   - Room version 从 2 升到 3
   - 新增 `DailyTask::class`
   - 新增 `dailyTaskDao()`
   - 新增正式 `MIGRATION_2_3`
   - 迁移只创建 `daily_tasks` 表和索引，保留原有通知 / 分类 / UsageStats 数据
   - 注意：仍保留 `.fallbackToDestructiveMigration()` 作为开发期兜底，但 v2→v3 不会触发 destructive

5. **`DashboardViewModel.kt`**（改）
   - 新增 `todayTasks`
   - 新增 `overdueTasks`
   - 新增 `addTask(title)`
   - 新增 `setTaskDone(taskId, isDone)`
   - 新增 `deleteTask(taskId)`

6. **`DashboardScreen.kt`**（改）
   - Dashboard 顶部新增「今日主线」卡片
   - 显示完成计数 `done/total`
   - 空状态提示「写下今天必须完成的 3 件事。」
   - 支持新增任务
   - 支持勾选完成，完成后文字划线
   - 支持删除任务
   - 今日任务和过往未完成任务分开展示
   - 昨天及更早没完成的任务会显示在「过往未完成」，前缀显示日期，例如 `05-03 · 算法 1 题`

验证：

| 验证项 | 结果 |
|---|---|
| `./gradlew assembleDebug` | ✅ `BUILD SUCCESSFUL in 4s` |
| APK 安装 | ✅ `adb install -r ...` 成功 |
| 应用启动 | ✅ 最新 pid `22210` |
| 启动日志 | ✅ 未看到 Room migration / AndroidRuntime 崩溃 |

后续可选增强：

- 限制每日主线最多 3 条，强制“少而不漏”
- 增加“今日是否过关”状态
- 增加睡前 100 字反思输入
- 增加每周统计：固定项完成几天、是否沉迷新东西、下周最重要一件事

---

## 调试速查

```bash
# 卸载（强制下次全新安装，绕过 Studio 增量编译缓存）
adb uninstall com.kian.khup.debug

# 看 KHUP 相关日志
adb logcat -d | grep -iE "KHUP/|WM-" | head -80

# 看 NLS 是否被 bind
adb shell cmd notification allow_listener com.kian.khup.debug/com.kian.khup.collection.notification.MessageListener

# dump 系统所有通知的完整 extras（开发期金矿）
adb shell dumpsys notification --noredact

# 看 WorkManager 调度的 job
adb shell dumpsys jobscheduler | grep -B 1 -A 3 "com.kian.khup"
```

## 用户工作流的坑

- **Studio 没有显式 "Rebuild Project" 菜单时**：用「Build → Clean Project」+「Build → Make Project」组合，等价
- **改了 `KhupApplication` / Hilt 注解后增量编译可能不生效**：症状是新代码没进 APK。最稳办法是 `adb uninstall` 后再从 Studio Run
- 用户终端 prompt 是 `kian@kianSONG:~/kian_phone$`，工作目录已经在项目根

---

## 临时追加：Messages 跳转状态 + 手动分类修正记录

背景：

- 用户已测试 Messages 点击跳转，认为“还可以”，不需要继续专门打磨。
- 顺手保留了消息卡片上的跳转能力展示，后续观察效果即可。
- 用户表示分类需求很低、消息也不多，所以分类链路不要继续深挖统计。

新增/改动：

1. **`NotificationLaunchRegistry.kt`**（改）
   - 新增 `directLaunchEventIds: StateFlow<Set<String>>`
   - 注册到内存的 `PendingIntent` 会发布到 UI
   - 新增 `LaunchCapability`：`DirectNotification` / `App` / `None`
   - Messages 卡片可显示「可直达通知 / 打开 App / 无法打开」

2. **`MessagesViewModel.kt` / `MessagesScreen.kt`**（改）
   - `MessagesViewModel.messages` 从 `ClassifiedEvent` 包装成 `MessageUiItem`
   - 卡片底部显示跳转能力
   - 卡片右下角新增「改分类」菜单
   - 可手动改成：社交、验证码、工作、推广、算法推送、其他

3. **`ClassificationFeedback.kt`**（新增）
   - 新表 `classification_feedback`
   - 字段：`id` / `eventId` / `oldClassification` / `newClassification` / `createdAt`
   - 外键级联到 `events.eventId`
   - 目的：记录人工分类修正，后续可作为规则 / LLM 评估样本

4. **`ClassificationFeedbackDao.kt`**（新增）
   - `insert(feedback)`

5. **`DerivedResultDao.kt` / `MessageRepository.kt`**（改）
   - 新增 `getClassification(eventId)`
   - `updateClassification()` 改为事务：
     - 先插入 `classification_feedback`
     - 再更新 `derived_results.classification`
     - `modelVersion` 标记为 `manual-v1`

6. **`AppDatabase.kt` / `DatabaseModule.kt`**（改）
   - Room version 从 3 升到 4
   - 新增 `ClassificationFeedback::class`
   - 新增 `classificationFeedbackDao()`
   - 新增正式 `MIGRATION_3_4`
   - 生成 `app/schemas/com.kian.khup.core.data.db.AppDatabase/4.json`

验证：

| 验证项 | 结果 |
|---|---|
| `./gradlew assembleDebug` | ✅ `BUILD SUCCESSFUL in 4s` |
| APK 安装 | ✅ `adb install -r ...` 成功 |
| 应用启动 | ✅ pid `17791` |
| KHUP 进程日志 | ✅ 未看到 AndroidRuntime / Room / SQLite / migration 错误 |

注意：

- 设备日志里出现过微信自己的 SQLite 报错，不属于 KHUP；按 KHUP pid 过滤后无异常。
- `sqlite3` 在手机 `run-as` 环境里执行失败：`Permission denied`，所以没有直接 shell 查表；Room/KSP schema 校验和启动迁移日志均正常。

---

## 下一步建议（新对话从这里接）

不要继续深挖分类统计。用户明确说分类需求低，消息也不多。

下一步优先做 **算法 App 干预 MVP**，先不要急着接大模型：

1. **抖音 / 小红书阈值提醒**
   - 复用已有 UsageStats 今日使用时长
   - 例如抖音超过 30 分钟、小红书超过 20 分钟
   - 先做提醒 + 记录，不做强阻断

2. **新增 action log 链路**
   - 目前已有 `ActionLog` entity，但还没有 DAO / Repository / UI 使用
   - 可以先把“超过阈值触发提醒”写入 `action_logs`

3. **再考虑大模型**
   - 端侧 LLM 不建议现在只为消息分类接入，收益低、成本高
   - 更合理用途：每日复盘、主线偏离总结、沉迷原因总结、干预建议
   - 真要接，第一步应是 MNN-LLM 最小可运行验证：固定 prompt → logcat 输出

---

## 临时追加：算法 App 干预 MVP

背景：

- 用户不想继续深挖消息分类统计。
- 本轮先做抖音 / 小红书阈值提醒，不做强阻断，不接大模型。
- 阈值暂定：抖音 30 分钟，小红书 20 分钟。

新增/改动：

1. **`ActionLogDao.kt`**（新增）
   - `insert()`
   - `hasActionSince(ruleId, actionType, sinceMs)`
   - `observeRecent(limit)`

2. **`AppSessionDao.kt`**（改）
   - 新增 `getUsageForPackagesSince(packageNames, sinceMs)`
   - 给干预规则读取当天某些 package 的累计前台时长

3. **`InterventionRepository.kt`**（新增）
   - 规则：
     - `algorithm.douyin.daily_30m` → `com.ss.android.ugc.aweme` ≥ 30 分钟
     - `algorithm.xiaohongshu.daily_20m` → `com.xingin.xhs` ≥ 20 分钟
   - 超阈值后发提醒并写 `actions_log`
   - 同一规则每天只提醒一次
   - 用 `Mutex` 避免 Dashboard resume 和 Worker 同时评估造成重复记录

4. **`InterventionNotifier.kt`**（新增）
   - 建立本地通知 channel：`khup_intervention`
   - 发送“使用提醒”通知，点击回 KHUP 首页
   - 没有 `POST_NOTIFICATIONS` 权限时不发通知，但仍写 action log，payload 里记录 `notificationPosted=false`

5. **`InterventionCheckWorker.kt`**（新增）
   - 每 15 分钟跑一次
   - 先 `UsageStatsRepository.syncToday()`，再 `InterventionRepository.evaluateToday()`
   - 无 UsageStats 权限时跳过
   - `UNIQUE_NAME = "khup.intervention_check"`

6. **`WorkScheduler.kt`**（改）
   - 注册 `InterventionCheckWorker`

7. **`MainActivity.kt`**（改）
   - App 启动时申请 `POST_NOTIFICATIONS`
   - 这是 Android 13+ 自发通知必须的 runtime 权限

8. **`AppDatabase.kt` / `DatabaseModule.kt`**（改）
   - 暴露 `actionLogDao()`
   - 提供 `ActionLogDao`
   - 注意：Room version 未变化，因为 `actions_log` 表从 v1 schema 就已经存在，只是以前没 DAO

9. **`DashboardViewModel.kt` / `DashboardScreen.kt`**（改）
   - Dashboard 新增「算法 App 干预」卡片
   - 没有记录时显示当前规则说明
   - 有记录时展示最近 action log
   - Dashboard resume 时同步 UsageStats 后立即评估一次干预规则

验证：

| 验证项 | 结果 |
|---|---|
| `./gradlew assembleDebug` | ✅ `BUILD SUCCESSFUL in 4s` |
| APK 安装 | ✅ `adb install -r ...` 成功 |
| 应用启动 | ✅ |
| NLS 连接 | ✅ logcat: `KHUP/NLS: Listener connected` |
| Intervention Worker 执行 | ✅ logcat: `KHUP/Intervention: intervention check finished: triggered=1` |
| WorkManager 注册 | ✅ JobScheduler 里有新的 15 分钟 KHUP job |
| 启动异常 | ✅ 未看到 KHUP 的 AndroidRuntime / Room / Hilt 崩溃 |

注意：

- 本轮没有做强阻断，只做提醒和记录。
- 如果用户拒绝通知权限，系统通知不会发出，但 `actions_log` 仍会记录一次，payload 里 `notificationPosted=false`。
- 当前 package 只覆盖标准中国版抖音 / 小红书；后续如果装了极速版或国际版，需要补 packageName。

下一步可选：

- Settings checklist 增加“通知权限”状态。
- Dashboard 干预卡片解析 payload，显示“实际用了多久 / 是否成功发通知”。
- 加“我知道了 / 今天不再提醒 / 调高阈值”等用户响应，写回 `userResponse`。
- 后续再做悬浮窗提醒或 Accessibility 强干预。

### 追加：通知权限状态 + 可调阈值

用户追问：

- 在哪里查看通知权限有没有开启？
- 时间限制是否应该在软件内手动调？
- 当前干预措施是什么？

本轮补充：

1. **`NotificationPermissions.kt`**（改）
   - 新增 `hasPostNotificationsPermission()`
   - 新增 `openAppNotificationSettings()`
   - 区分：
     - “通知使用权”= KHUP 读取其他 App 通知
     - “发送通知权限”= KHUP 自己发提醒通知

2. **`InterventionSettingsRepository.kt`**（新增）
   - 用 SharedPreferences 保存阈值
   - 默认：抖音 30 分钟，小红书 20 分钟
   - 范围限制：1-240 分钟
   - 提供 `observeSettings()` 给 UI 实时刷新

3. **`SettingsViewModel.kt`**（新增）
   - 暴露 `interventionSettings`
   - 提供 `setDouyinLimit()` / `setXiaohongshuLimit()`

4. **`SettingsScreen.kt`**（改）
   - 权限设置顶部新增「发送通知权限」卡片
   - 页面底部新增「干预阈值」卡片
   - 抖音 / 小红书阈值支持用 +/- 以 5 分钟步进调节

5. **`InterventionRepository.kt`**（改）
   - 干预规则从设置仓库读取阈值，不再硬编码 30/20
   - ruleId 改为稳定值：
     - `algorithm.douyin.daily`
     - `algorithm.xiaohongshu.daily`

验证：

| 验证项 | 结果 |
|---|---|
| `./gradlew assembleDebug` | ✅ `BUILD SUCCESSFUL in 6s` |
| APK 安装 | ✅ `adb install -r ...` 成功 |
| 应用启动 | ✅ |
| NLS 连接 | ✅ logcat: `KHUP/NLS: Listener connected` |
| 启动异常 | ✅ 未看到 KHUP 的 AndroidRuntime / Room / Hilt 崩溃 |

### 追加：超阈值后打开算法 App 先输入目的

用户需求：

- 超过规定时间后，再点开抖音 / 小红书，不应该直接进入。
- 先弹出一个输入框，要求写“这次打开的目的是什么”。
- 输入后才能继续打开。

本轮实现的是悬浮窗 MVP，不用 Accessibility：

1. **`UsageStatsCollector.kt`**（改）
   - 新增 `getCurrentForegroundPackage()`
   - 通过 `UsageStatsManager.queryEvents()` 看最近前台 App

2. **`InterventionRepository.kt`**（改）
   - 新增 `isMonitoredPackage(packageName)`
   - 新增 `getExceededRuleForPackage(packageName)`
   - 新增 `recordPurposeGate(rule, purpose)`
   - 目的输入记录写入 `actions_log`
   - `actionType = "purpose_gate"`
   - `userResponse = "purpose_submitted"`

3. **`ForegroundAppMonitorService.kt`**（新增）
   - Hilt 注入 `UsageStatsCollector` / `UsageStatsRepository` / `InterventionRepository`
   - KHUP 打开后启动一个前台服务
   - 每 3 秒轮询当前前台 App
   - 只有当前台 App 是抖音 / 小红书时才同步 UsageStats，避免刷其他 App 时频繁写库
   - 如果当天累计时长超过阈值：
     - 弹 `TYPE_APPLICATION_OVERLAY` 全屏悬浮窗
     - 要求输入目的
     - 输入非空后才能点“写好了，继续”
     - 记录目的并移除悬浮窗
   - 同一个规则提交目的后有 15 分钟冷却，避免短时间内反复弹

4. **`AndroidManifest.xml`**（改）
   - 注册 `ForegroundAppMonitorService`
   - `foregroundServiceType="dataSync"`

5. **`MainActivity.kt`**（改）
   - App 启动时调用 `ForegroundAppMonitorService.start(this)`
   - 没放到 `Application`，避免系统后台拉起 KHUP 时触发前台服务启动限制

验证：

| 验证项 | 结果 |
|---|---|
| `./gradlew assembleDebug` | ✅ `BUILD SUCCESSFUL in 6s` |
| APK 安装 | ✅ `adb install -r ...` 成功 |
| 应用启动 | ✅ |
| NLS 连接 | ✅ logcat: `KHUP/NLS: Listener connected` |
| 前台监控服务 | ✅ `dumpsys activity services` 显示 `ForegroundAppMonitorService` 且 `isForeground=true` |
| 启动异常 | ✅ 未看到 KHUP 的 AndroidRuntime / Room / Hilt / ForegroundService 崩溃 |

测试方法：

1. 确认 Settings 里：
   - 使用情况访问：已授权
   - 悬浮窗权限：已授权
   - 发送通知权限：建议开启，但目的弹窗主要依赖悬浮窗权限
2. 把 Settings 里的抖音 / 小红书阈值调低，比如 1 分钟。
3. 打开对应 App 用超过阈值。
4. 回 KHUP 一次，确保前台监控服务启动。
5. 再打开抖音 / 小红书，预期出现全屏目的输入悬浮窗。

当前限制：

- 这是悬浮窗覆盖，不是真正系统级禁止启动 App。
- UsageStats 是轮询，不是实时回调，可能有 1-3 秒延迟。
- 如果 KHUP 被系统或用户强杀，监控失效。
- 冷却时间现在写死 15 分钟，后续可以做成设置项。

### 追加：目的输入弹窗配色 + 自动降媒体音量

用户反馈：

- 悬浮窗背景是深蓝色，但输入区域字体/背景搭配不好，打字看不清。
- 希望触发弹窗时顺便把声音调到最低，输入原因后再恢复。

本轮改动：

1. **`ForegroundAppMonitorService.kt`**（改）
   - 输入框改成白色圆角背景、深色文字、灰色 hint
   - 确认按钮改成蓝色圆角背景、白色文字
   - 按钮 disabled 时变成灰蓝色
   - 弹目的输入框前保存当前媒体音量到 `volumeBeforeGate`
   - 调用 `AudioManager.setStreamVolume(STREAM_MUSIC, minVolume, 0)` 把媒体音量降到最低
   - 用户输入原因并提交后调用 `restoreMediaVolume()` 恢复到弹窗前的媒体音量

2. **`AndroidManifest.xml`**（改）
   - 新增 `android.permission.MODIFY_AUDIO_SETTINGS`

验证：

| 验证项 | 结果 |
|---|---|
| `./gradlew assembleDebug` | ✅ `BUILD SUCCESSFUL in 4s` |
| APK 安装 | ✅ `adb install -r ...` 成功 |
| 应用启动 | ✅ |
| NLS 连接 | ✅ logcat: `KHUP/NLS: Listener connected` |
| 启动异常 | ✅ 未看到 KHUP 的 AndroidRuntime / Room / Hilt / ForegroundService 崩溃 |

本次对“目的输入干预”相关所有修改的完整汇总：

- **阈值提醒**：抖音 / 小红书超过设置阈值后，写入 `actions_log` 并可发系统通知。
- **可调阈值**：Settings 里可用 +/- 调整抖音、小红书每日阈值，默认 30 / 20 分钟。
- **权限状态**：Settings 里能看“发送通知权限”“通知使用权”“使用情况访问”“悬浮窗权限”等状态。
- **今日干预记录**：Dashboard 的“算法 App 干预”只显示今天的干预记录，第二天自动回到空状态；历史记录保留在库里。
- **前台监控服务**：KHUP 打开后启动 `ForegroundAppMonitorService`，每 3 秒查看当前前台 App。
- **目的输入悬浮窗**：超阈值后再打开抖音 / 小红书，会弹全屏悬浮窗要求输入目的，非空才能继续。
- **冷却机制**：提交目的后，同一规则 15 分钟内不重复弹。
- **目的记录**：提交目的会写入 `actions_log`，`actionType = "purpose_gate"`，`userResponse = "purpose_submitted"`。
- **弹窗视觉**：输入框改成浅色背景深色字，按钮状态颜色更清楚。
- **音量处理**：弹窗出现前把媒体音量降到最低，提交目的后恢复到弹窗前音量。

仍未做：

- 冷却时间还不能在 Settings 里配置。
- 目的输入记录还没有在 Dashboard 中解析展示“目的内容 / 当时已用多久”。
- 当前只覆盖中国版抖音 `com.ss.android.ugc.aweme` 和小红书 `com.xingin.xhs`。
- 仍是悬浮窗覆盖，不是 Accessibility 级别的强阻断。

### 追加：Dashboard 干预栏改为“今日记录”

用户确认：首页「算法 App 干预」栏第二天应该刷新，不应一直展示历史最近记录。

本轮改动：

1. **`ActionLogDao.kt`**（改）
   - 新增 `observeSince(sinceMs, limit)`

2. **`InterventionRepository.kt`**（改）
   - 新增 `observeTodayActions(limit)`
   - 使用今天 00:00 作为 `sinceMs`

3. **`DashboardViewModel.kt` / `DashboardScreen.kt`**（改）
   - 首页干预卡片从 `recentActions` 改为 `todayActions`
   - 第二天会自动显示空状态和规则说明
   - 历史 `actions_log` 仍保留，只是不在首页展示

验证：

| 验证项 | 结果 |
|---|---|
| `./gradlew assembleDebug` | ✅ `BUILD SUCCESSFUL in 11s` |
| APK 安装 | ✅ `adb install -r ...` 成功 |
| 应用启动 | ✅ |
| NLS 连接 | ✅ logcat: `KHUP/NLS: Listener connected` |
| 启动异常 | ✅ 未看到 KHUP 的 AndroidRuntime / Room / Hilt 崩溃 |
