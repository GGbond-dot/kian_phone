# 消息处理 & 数字健康 App 架构文档

> 一个 Android 端的消息聚合 + 使用时长管理工具，端侧跑 Gemma 4 做语义理解，目标是把"被动接收 + 算法推送"重新拿回主动权。

---

## 1. 项目目标

### 核心能力
- 聚合所有 App 的消息（微信、QQ、抖音、小红书、钉钉、学习通、短信、邮件、各类 App 推送）
- 端侧 AI 自动分类、摘要、打标签、判断优先级
- 统计每个 App 的使用时长和打开次数
- 根据用户设定的策略，提醒/拦截过度使用，特别是算法推送类（抖音、小红书）
- 钉钉、学习通的待办、作业、签到聚合到统一视图

### 非目标（明确不做）
- 不做云端同步，所有数据本地处理
- 不做跨设备
- 不做 iOS（系统限制太多，做不出对等功能）

### 目标设备
- 小米 14（骁龙 8 Gen 3，16GB RAM，512GB），HyperOS / MIUI 14+
- 最低支持：Android 13（API 33）。低于这个版本 NLS 行为差异较大，不值得兼容。

---

## 2. 技术栈

| 层 | 选型 | 理由 |
|---|---|---|
| 语言 | Kotlin | Android 原生标配 |
| UI | Jetpack Compose | 声明式，开发快 |
| 异步 | Coroutines + Flow | 与 Room、Compose 天然集成 |
| 数据库 | Room | 类型安全的 SQLite 封装 |
| DI | Hilt | 减少手动布线 |
| 设置存储 | DataStore (Preferences) | 替代 SharedPreferences |
| 后台调度 | WorkManager | 定时批量任务（AI 推理批次、日报生成） |
| 端侧 LLM | MNN-LLM 优先，llama.cpp 备选 | MNN 对国产芯片优化好、Java/Kotlin 接口完整 |
| 模型 | Gemma 4 E4B Q4_K_M | 4-bit 量化约 5-6GB，16GB RAM 余量充足 |
| 网络 | Ktor Client | 钉钉 / 超星 API 调用 |
| 序列化 | kotlinx.serialization | 配合 Ktor、规则引擎 JSON |

---

## 3. 三层架构

```
┌─────────────────────────────────────────────────────────┐
│  1. 采集层 Collection                                    │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐    │
│  │ 通知监听     │ │ 使用统计     │ │ 外部 API     │    │
│  │ NLS          │ │ UsageStats   │ │ DingTalk/CX  │    │
│  └──────────────┘ └──────────────┘ └──────────────┘    │
└─────────────────────────────────────────────────────────┘
                          ↓ 事件流
┌─────────────────────────────────────────────────────────┐
│  2. 核心层 Storage & Processing                         │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐    │
│  │ Room DB      │ │ Gemma 4 引擎 │ │ 规则引擎     │    │
│  │ 事件 + 衍生  │ │ 分类/摘要    │ │ 策略 + 触发  │    │
│  └──────────────┘ └──────────────┘ └──────────────┘    │
└─────────────────────────────────────────────────────────┘
                          ↓ Action
┌─────────────────────────────────────────────────────────┐
│  3. 输出层 UI & Intervention                            │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐    │
│  │ Dashboard    │ │ 提醒系统     │ │ 拦截系统     │    │
│  │ Compose UI   │ │ 通知 + 日报  │ │ 悬浮窗+冷却  │    │
│  └──────────────┘ └──────────────┘ └──────────────┘    │
└─────────────────────────────────────────────────────────┘
```

### 设计原则
- **采集层是唯一与系统/外部交互的边界**，所有数据进入应用都在这里完成，统一打成 `Event` 写入数据库。
- **核心层不直接被 UI 调用**，UI 只通过 Repository 读数据库的 Flow。
- **三个出口（Dashboard / 提醒 / 拦截）互相不依赖**，都只订阅核心层的事件流和 Action 流。

---

## 4. 数据流约定（Event Sourcing）

整个系统采用**事件溯源 + 单向数据流**：

```
采集层 ──写入──> events 表（不可变）
                    │
                    ├──读──> AI 引擎 ──写入──> derived_results 表
                    │
                    └──读──> 规则引擎 ──产生──> Action ──> 输出层
```

### 关键约定

1. **事件不可变**：`events` 表只 INSERT，从不 UPDATE。任何"更新"都是新事件。
2. **幂等性**：每个事件有唯一 `event_id`（基于内容 hash），重复写入自动去重。
3. **可重放**：可以删掉 `derived_results` 重新跑 AI 全量推理，用来测试新模型或新 prompt，不用真的等手机收一周通知。
4. **衍生结果与原事件强关联**：`derived_results.event_id` 是外键，事件被清理时衍生结果级联删除。

### 事件类型枚举

```kotlin
enum class EventType {
    NOTIFICATION_POSTED,      // 通知到达
    NOTIFICATION_REMOVED,     // 通知被划掉/撤回
    APP_FOREGROUND_START,     // 进入前台
    APP_FOREGROUND_END,       // 离开前台
    APP_OPEN_COUNT,           // 打开次数（聚合）
    DINGTALK_TODO,            // 钉钉待办
    DINGTALK_MEETING,         // 钉钉会议
    CHAOXING_HOMEWORK,        // 超星作业
    CHAOXING_SIGN_IN,         // 超星签到
    CHAOXING_EXAM,            // 超星考试
}
```

---

## 5. 各模块实现要点

### 5.1 通知监听（NotificationListenerService）

#### 服务声明

```xml
<service
    android:name=".collection.MessageListener"
    android:label="消息处理"
    android:exported="true"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService"/>
    </intent-filter>
</service>
```

注意 `exported="true"` + `BIND_NOTIFICATION_LISTENER_SERVICE` 权限是给系统 bind 用，不是开放给其他 App。**别放单独进程**，更容易被 MIUI 杀。

#### 权限引导

NLS 不能 `requestPermissions()`，必须跳系统设置：

```kotlin
fun isNotificationListenerEnabled(context: Context): Boolean {
    val cn = ComponentName(context, MessageListener::class.java)
    val flat = Settings.Secure.getString(
        context.contentResolver, "enabled_notification_listeners"
    )
    return flat?.split(":")?.any { ComponentName.unflattenFromString(it) == cn } == true
}

fun openSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}
```

**MIUI 坑**：开关打开后还有二次确认（"允许读取通知"），引导页必须放截图说明。

#### 提取通知字段

```kotlin
val extras = sbn.notification.extras
val title    = extras.getString(Notification.EXTRA_TITLE)
val text     = extras.getCharSequence(Notification.EXTRA_TEXT)
val subText  = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
val bigText  = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
val pkg      = sbn.packageName
val postTime = sbn.postTime
val channelId = sbn.notification.channelId  // 钉钉用这个区分 IM/工作通知
val category = sbn.notification.category     // 抖音用这个区分 msg/social/recommendation
val key      = sbn.key
```

**MessagingStyle**（微信、QQ、Telegram 必用）：

```kotlin
val messages = NotificationCompat.MessagingStyle
    .extractMessagingStyleFromNotification(sbn.notification)
    ?.messages  // 每条有 sender, text, timestamp
```

**InboxStyle**（聚合通知）：

```kotlin
val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
```

#### 双层去重

同一条逻辑消息可能触发多次回调（更新通知、进度条、媒体控制）。

**第 1 层（内存 / 实时）**：

```kotlin
data class NotificationSnapshot(
    val key: String,
    val pkg: String,
    val title: String?,
    val text: String?,
    val postTime: Long,
    val contentHash: Int  // hash(title + text + bigText + 所有 messages)
)

private val cache = ConcurrentHashMap<String, NotificationSnapshot>()

override fun onNotificationPosted(sbn: StatusBarNotification, rm: RankingMap) {
    val snap = sbn.toSnapshot() ?: return
    val prev = cache[snap.key]
    if (prev?.contentHash == snap.contentHash) return  // 内容没变，丢
    cache[snap.key] = snap
    channel.trySend(snap)
}
```

**第 2 层（DB / 持久）**：写库前用 `(packageName, title, text, postTime/1000)` 算 hash 作为 `event_id`，PK 冲突自动 skip。

#### 严禁阻塞回调

`onNotificationPosted` 跑在 Binder 线程，系统有 ANR 监控。回调里**只做提取和入队**，AI 推理、写盘全走异步：

```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val channel = Channel<NotificationSnapshot>(capacity = Channel.BUFFERED)

override fun onListenerConnected() {
    scope.launch {
        for (snap in channel) {
            repository.insertEvent(snap.toEvent())
        }
    }
}
```

AI 推理由 WorkManager 定时批量触发，不在 Listener 里直接调。

#### 服务保活

MIUI/HyperOS 会激进 kill NLS。三步走：

1. **引导用户加白名单**：设置 → 应用管理 → 你的 App → 省电策略 → 无限制；自启动管理打开。这步**必须**有手把手引导页，不然用户绝对找不到。
2. **不要保活套路**：1px Activity、双进程互拉、无声音频——MIUI 都识别并拉黑。
3. **被杀后周期性自检 + 重连**：

```kotlin
fun rebindListener(context: Context) {
    val cn = ComponentName(context, MessageListener::class.java)
    val pm = context.packageManager
    pm.setComponentEnabledSetting(cn, COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP)
    pm.setComponentEnabledSetting(cn, COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP)
}
```

切换 component enabled 状态强制系统重新评估并重新 bind，这是绕过"假死"的标准操作。WorkManager 每 15 分钟跑一次自检即可。

#### App 特殊处理

- **抖音 / 小红书**：默认不入库营销推送，只统计推送频率。`category == "msg"` 或 `category == "social"` 的才入库（评论、私信、@）。
- **钉钉**：通知文本经常是 `[3 条] 张三：…`，原始消息走 MessagingStyle。`channelId` 区分 IM 通知 / 工作通知。
- **学习通**：通知格式相对规整，正则解析截止时间。但学习通通知有时延迟 5 分钟才到，重要事项（签到、作业截止）必须配合网页端轮询双保险。

#### 调试

```bash
# 允许/禁止 listener
adb shell cmd notification allow_listener com.your.app/.collection.MessageListener
adb shell cmd notification disallow_listener com.your.app/.collection.MessageListener

# dump 所有通知的完整 extras（开发期金矿）
adb shell dumpsys notification --noredact
```

---

### 5.2 使用统计（UsageStatsManager）

#### 权限

`PACKAGE_USAGE_STATS` 是特殊权限，要跳设置：

```kotlin
fun openUsageAccessSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
}

fun hasUsageAccess(context: Context): Boolean {
    val ops = context.getSystemService(AppOpsManager::class.java)
    val mode = ops.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(), context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}
```

#### 数据采集

两种 API：

- `queryUsageStats(interval, start, end)` — 聚合数据，适合日报。
- `queryEvents(start, end)` — 原始事件流（MOVE_TO_FOREGROUND / MOVE_TO_BACKGROUND），适合精确计算前台时长。

**推荐方案**：WorkManager 每 5 分钟跑一次，用 `queryEvents` 取增量事件，转成 `APP_FOREGROUND_START` / `APP_FOREGROUND_END` 事件入库。这样事件颗粒度足够细，可以做"今天打开抖音几次"、"每次平均停留多久"这种分析。

注意 `queryEvents` 返回的事件可能因为系统休眠而缺失配对（有 START 没有 END），处理时按"下一个 START 之前的最后一个事件"补 END。

---

### 5.3 外部 API（钉钉 / 超星）

#### 钉钉

如果只是个人用，能拿到的数据有限。`钉钉开放平台`需要企业管理员授权才能拿组织数据。建议：

- **MVP 阶段不做 API 接入**，完全靠通知层 + AI 摘要够用。
- **后续可选**：实现 OAuth 个人扫码登录，拿自己的待办列表 / 日程。

#### 超星学习通

无官方开放 API。三种思路按推荐度：

1. **网页端模拟登录**（推荐）：`i.mooc.chaoxing.com` / `mooc1-1.chaoxing.com`，OAuth 后用 Ktor 拉课程列表、作业、签到信息。GitHub 上搜 `chaoxing` 有几个非官方 SDK 可参考。
2. **通知截取**（已在 5.1 覆盖）：作为补充。
3. **AccessibilityService 读屏**（不推荐主用）：对 App 改版极度敏感。

---

### 5.4 数据库（Room）

核心表（细节字段 Claude Code 可以根据需要扩展）：

```kotlin
@Entity(tableName = "events")
data class Event(
    @PrimaryKey val eventId: String,        // hash(pkg + title + text + postTime/1000)
    val type: EventType,
    val packageName: String,
    val timestamp: Long,
    val title: String?,
    val text: String?,
    val subText: String?,
    val bigText: String?,
    val channelId: String?,
    val category: String?,
    val rawJson: String?                    // 兜底：原始 extras 序列化
)

@Entity(
    tableName = "derived_results",
    foreignKeys = [ForeignKey(
        entity = Event::class,
        parentColumns = ["eventId"],
        childColumns = ["eventId"],
        onDelete = CASCADE
    )]
)
data class DerivedResult(
    @PrimaryKey val eventId: String,
    val classification: String,             // 验证码 / 工作 / 社交 / 推广 / 其他
    val priority: Int,                      // 0-3
    val summary: String?,                   // AI 一句话摘要
    val entities: String?,                  // JSON: ["张三", "明天 14:00"]
    val processedAt: Long,
    val modelVersion: String                // 方便 reprocess
)

@Entity(tableName = "app_sessions")
data class AppSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startTime: Long,
    val endTime: Long?,                     // 可空，未结束时为 null
    val durationMs: Long?
)

@Entity(tableName = "rule_state")
data class RuleState(
    @PrimaryKey val key: String,            // e.g. "douyin_today_usage_ms"
    val valueJson: String,
    val updatedAt: Long
)

@Entity(tableName = "actions_log")
data class ActionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: String,
    val actionType: String,                 // remind / block / cooldown
    val payload: String,
    val triggeredAt: Long,
    val userResponse: String?               // dismissed / acknowledged / overridden
)
```

#### 索引

```kotlin
@Entity(
    tableName = "events",
    indices = [
        Index("packageName", "timestamp"),
        Index("type", "timestamp"),
        Index("timestamp")  // 时间范围查询
    ]
)
```

#### 数据保留策略

事件表会快速膨胀。建议：

- 原始 `events`：保留 30 天，超过的归档到压缩文件（按月）。
- `derived_results`：保留 90 天（AI 已经压缩成短摘要，体积小）。
- `app_sessions`：保留 1 年（用于年度报告）。
- `actions_log`：永久保留（数据量小，且对调试规则很有用）。

WorkManager 每天跑一次清理任务。

---

### 5.5 AI 引擎（Gemma 4 端侧）

#### 部署

**首选：MNN-LLM**

```gradle
implementation 'com.alibaba.mnn:mnn-llm:x.y.z'
```

模型文件放 `/data/data/<pkg>/files/models/gemma-4-e4b-q4.mnn`，首次启动从 assets 解压或在线下载（300MB-2GB，看量化等级）。

**备选：llama.cpp**

自己交叉编译 NDK 版本，或用社区已编译好的 `llama-android` 库。灵活度更高（可控 KV cache、量化策略），但封装代码量大。

#### Prompt 设计

让 Gemma 输出 **JSON**，方便下游解析。Gemma 4 原生支持 system role 和 function calling，prompt 例：

```kotlin
val systemPrompt = """
你是手机消息分类助手。对每条通知，输出 JSON：
{
  "classification": "验证码|工作|社交|推广|算法推送|其他",
  "priority": 0-3,
  "summary": "不超过 20 字的摘要",
  "entities": ["关键人名/地点/时间"],
  "actionable": true|false
}
不要输出任何其他内容。
""".trimIndent()

val userPrompt = """
App: $appName
标题: $title
正文: $text
""".trimIndent()
```

#### 推理调度

**混合策略**（非常重要，决定了体验和耗电）：

1. **规则秒过**（不进 LLM）：
   - 验证码：正则 `\d{4,8}` + 关键词"验证码"
   - 群消息（MessagingStyle 多 sender）：直接归类"社交"
   - 已知营销渠道（按 `channelId`）：归类"推广"
   
2. **批量 LLM**：剩余的、模糊的进队列，WorkManager 每 5 分钟跑一次批处理。
   - 一次 batch 一般 10-30 条，复用 KV cache，端侧大概 10-30 秒搞定。
   - 用户当下能看到的就是几分钟前的摘要，可接受。

3. **即时 LLM**（可选，仅高优先级 App）：
   - 用户标记的"重要联系人"或"工作 App"消息可立即跑，约 1-2 秒延迟。
   - 默认关闭，省电。

#### 模型生命周期

- App 启动时不要立即加载模型，按需懒加载。
- 加载后保持在内存（约 5-6GB），首次推理冷启动约 3-5 秒。
- 用户切到后台超过 10 分钟自动卸载，下次需要时重载。
- 提供"省电模式"开关：完全禁用 LLM，只用规则。

---

### 5.6 规则引擎

#### 设计思路

纯函数式：`(currentState, newEvent) -> List<Action>`

规则用 JSON 存储和热更新（v1 用这个，v2 再考虑 DSL）：

```json
{
  "id": "douyin_daily_limit",
  "name": "抖音每日限时",
  "trigger": {
    "type": "app_foreground",
    "package": "com.ss.android.ugc.aweme"
  },
  "condition": {
    "type": "and",
    "conditions": [
      { "type": "today_usage_exceeds", "package": "com.ss.android.ugc.aweme", "minutes": 30 }
    ]
  },
  "actions": [
    { "type": "show_overlay", "message": "今天抖音已用 {{usage}} 分钟，确定继续？", "delay_seconds": 5 },
    { "type": "log" }
  ]
}
```

#### 核心数据结构

```kotlin
sealed class Trigger {
    data class AppForeground(val pkg: String) : Trigger()
    data class NotificationReceived(val pkg: String?, val classification: String?) : Trigger()
    data class TimeOfDay(val hour: Int, val minute: Int) : Trigger()
}

sealed class Condition {
    data class TodayUsageExceeds(val pkg: String, val minutes: Int) : Condition()
    data class TimeWindow(val start: LocalTime, val end: LocalTime) : Condition()
    data class And(val conditions: List<Condition>) : Condition()
    data class Or(val conditions: List<Condition>) : Condition()
}

sealed class Action {
    data class ShowOverlay(val message: String, val delaySeconds: Int) : Action()
    data class BlockApp(val pkg: String, val cooldownMinutes: Int) : Action()
    data class SendNotification(val title: String, val body: String) : Action()
    object Log : Action()
}
```

#### 规则评估时机

由核心层统一调度：

- 新事件入库后，触发匹配 `NotificationReceived` 类型的规则
- App 切到前台事件，触发匹配 `AppForeground` 类型的规则
- WorkManager 每分钟跑一次，触发匹配 `TimeOfDay` 类型的规则

---

### 5.7 Dashboard（UI）

Compose + Repository 模式，所有数据来自 Room 的 Flow。

页面规划：

- **首页**：今日消息时间线（按优先级）+ 各 App 使用时长条形图
- **消息中心**：按分类 tab（验证码/工作/社交/推广/其他），可搜索
- **使用分析**：日/周/月维度，App 排行榜，趋势图
- **任务**：钉钉待办 + 学习通作业聚合视图
- **设置**：规则管理、模型设置、保活引导、数据导出

---

### 5.8 提醒系统

- **本地通知**：用 NotificationManager 发，记得自己的通知不要被自己的 NLS 再次捕获（在 Listener 里 filter `sbn.packageName == myPkg`）。
- **每日日报**：WorkManager 每天 22:00 触发，跑一次 LLM 总结今天的关键消息和使用情况，发一条富通知。
- **高优消息即时推送**：被规则标记为高优的，立刻发系统通知（用自定义 channel，IMPORTANCE_HIGH）。

---

### 5.9 拦截系统

#### 软干预（默认）
- 检测到打开抖音 → 发本地通知"今天已用 X 分钟"

#### 中干预
- 用 `AccessibilityService` 检测前台 App，超阈值时启动一个全屏 Activity（透明背景 + 居中卡片），强制等待 N 秒。
- 卡片提供"再用 5 分钟"和"关掉算了"两个按钮。

#### 硬干预（可选开启）
- 超过日预算后，再次打开直接拉起拦截 Activity，不允许进入。
- 提供"应急解锁"机制：连续点 5 次解锁按钮才放行（增加摩擦力，防止反射性绕过）。

#### AccessibilityService 注意事项
- 同样需要跳设置手动开（`Settings.ACTION_ACCESSIBILITY_SETTINGS`）。
- 服务被 kill 比 NLS 还频繁，必须有自检 + 重连机制。
- 用 `AccessibilityServiceInfo.eventTypes = TYPE_WINDOW_STATE_CHANGED` 减少不必要回调，省电。
- **不要尝试解析 view 树做内容过滤**（比如识别抖音"推荐"页 vs "关注"页）。抖音版本一更新就废，维护成本极高。仅用包名级别的拦截。

#### 悬浮窗权限
`SYSTEM_ALERT_WINDOW`，跳设置 `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`。MIUI 默认禁用，引导页要专门提一下。

---

## 6. 关键决策（请确认后再开工）

### a. 推理时机
- [x] **混合策略**（推荐）：规则秒过 + LLM 批量（5 分钟一次）+ 高优 App 即时

### b. 干预强度（默认值）
- [ ] 软：仅提醒
- [x] **中：提醒 + 5 秒强制等待**（默认）
- [ ] 硬：超预算锁住

用户可在设置里调档。

### c. 规则存储
- [x] **JSON 配置**（v1）
- [ ] DSL（v2 视情况）

---

## 7. MVP 阶段划分

### Phase 1（2-3 周）：能跑起来
- [ ] NotificationListenerService 完整实现（双层去重、保活引导）
- [ ] Room 数据库 + Repository
- [ ] 基础 UI：消息时间线 + 设置页
- [ ] 规则引擎骨架（先支持 1-2 种规则类型）
- [ ] **不接 LLM**，仅用规则做分类（验证码、群消息、推广）

### Phase 2（2-3 周）：AI 上线
- [ ] MNN-LLM 集成 + Gemma 4 E4B 加载
- [ ] Prompt 调试 + JSON 输出解析
- [ ] WorkManager 批量推理
- [ ] 消息中心按分类展示

### Phase 3（2 周）：使用统计
- [ ] UsageStatsManager 集成
- [ ] App 使用分析页
- [ ] 抖音 / 小红书时长统计
- [ ] 软干预提醒

### Phase 4（2-3 周）：干预系统
- [ ] AccessibilityService 接入
- [ ] 悬浮窗 / 拦截 Activity
- [ ] 规则配置 UI
- [ ] 每日日报

### Phase 5（按需）：第三方集成
- [ ] 学习通网页端登录 + 数据拉取
- [ ] 钉钉个人 OAuth（如果觉得有必要）

---

## 8. 项目结构建议

```
app/
├── src/main/java/com/yourname/messagehub/
│   ├── collection/                  # 采集层
│   │   ├── notification/
│   │   │   ├── MessageListener.kt
│   │   │   ├── NotificationParser.kt
│   │   │   └── DedupeCache.kt
│   │   ├── usage/
│   │   │   └── UsageStatsCollector.kt
│   │   └── external/
│   │       ├── ChaoxingClient.kt
│   │       └── DingtalkClient.kt
│   ├── core/                        # 核心层
│   │   ├── data/
│   │   │   ├── db/
│   │   │   │   ├── AppDatabase.kt
│   │   │   │   ├── EventDao.kt
│   │   │   │   └── entities/
│   │   │   └── repository/
│   │   ├── ai/
│   │   │   ├── GemmaEngine.kt
│   │   │   ├── PromptBuilder.kt
│   │   │   └── BatchProcessor.kt
│   │   └── rules/
│   │       ├── RuleEngine.kt
│   │       ├── RuleLoader.kt
│   │       └── model/
│   ├── output/                      # 输出层
│   │   ├── ui/
│   │   │   ├── dashboard/
│   │   │   ├── messages/
│   │   │   ├── analytics/
│   │   │   └── settings/
│   │   ├── reminder/
│   │   │   └── ReminderManager.kt
│   │   └── intervention/
│   │       ├── BlockingService.kt
│   │       └── OverlayManager.kt
│   ├── common/                      # 跨层共享
│   │   ├── di/                      # Hilt modules
│   │   └── util/
│   └── MainActivity.kt
└── src/main/res/raw/
    └── default_rules.json           # 预置规则
```

---

## 9. 给 Claude Code 的工作建议

1. **先按 Phase 1 顺序开发**，每个模块跑通再下一个。
2. **NotificationListener 这块特别注意**：MIUI/HyperOS 行为和 Pixel/AOSP 差异很大，开发期就在小米 14 真机上调，模拟器没意义。
3. **MNN-LLM 集成会有平台适配问题**，第一次集成预留 1-2 天踩坑。
4. **数据库 schema 可能会改几次**，开发期用 `fallbackToDestructiveMigration()`，正式发布前补正常 migration。
5. **小米保活**这块要做完整的引导页（截图 + 文字步骤），不能只放个跳转按钮。
6. 如果跑 LLM 时 OOM，先尝试更激进的量化（Q3 甚至 Q2_K），再考虑换 E2B。
