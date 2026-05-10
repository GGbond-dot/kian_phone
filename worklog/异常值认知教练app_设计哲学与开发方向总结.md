# 异常值认知教练 App：设计哲学与开发方向总结

## 0. 文档用途

这份文档用于承接当前关于手机 App 项目的产品哲学、代码审计结果和后续开发方向讨论。后续可以把这份文档发给 CLI、Codex、Copilot 或其他代码 Agent，让它继续根据现有项目代码进行设计修正、代码实现和路线规划。

项目当前不是一个普通的防沉迷 App，也不是一个普通的用机统计 App，而是一个基于塔勒布“黑天鹅 / 反脆弱 / 可选性”思想的“异常值认知教练”。它的目标是帮助用户识别自己被算法和习惯推回的“回归值生活”，然后通过低成本、正期望、可拒绝的异常值行动，恢复用户对信息、时间和行为路径的选择权。

---

# 1. 产品思想来源：塔勒布哲学如何转译成 App

## 1.1 黑天鹅：世界无法被准确预测

塔勒布所谓“黑天鹅”，指的是那些事前难以预测、事后影响巨大、发生后又容易被人们编故事解释成“早有征兆”的事件。

对这个 App 来说，“黑天鹅”带来的产品启发不是让用户更努力预测未来，而是：

> 不要把人生设计成完全依赖预测正确的结构。

普通推荐系统的逻辑是：

> 你过去喜欢什么，我继续给你什么。

这个 App 的逻辑应该是：

> 我知道你过去喜欢什么，但我更关心你是否正在被过去的偏好困住。

因此，产品不应该只服务用户已经知道的需求，而应该帮助用户暴露在高质量、可承受、有潜在收益的意外中。

## 1.2 反脆弱：不是抗住波动，而是利用波动

塔勒布区分了三种系统：

| 类型 | 面对冲击时 | 例子 |
|---|---|---|
| 脆弱 | 受损 | 玻璃杯、单一收入来源、单一路径人生 |
| 强韧 | 扛住、不变 | 石头、稳定现金流 |
| 反脆弱 | 因冲击变强 | 肌肉、免疫系统、健康的创业生态 |

产品上的转译是：

> 用户不应该追求完全没有波动的稳定生活，而应该拥有稳定基本盘，同时主动接触小剂量、有收益可能的异常值。

所以这个 App 不是让用户变得更“规整”、更“自律”、更“可控”，而是让用户变得：

- 对非线性机会更开放；
- 对默认信息流更不顺从；
- 对习惯路径更敏感；
- 对信息茧房更警觉；
- 对自己的时间和行为有更多选择权。

## 1.3 杠铃策略：保护基本盘 + 小比例高上行探索

杠铃策略的产品化表达是：

> 一边保护用户的精神基本盘，减少低质量信息和无意识刷屏；另一边制造小比例、高质量、正期望的异常值暴露。

在 App 中可以对应成两端：

### 保守端：保护用户不被低质量信息吞没

包括：

- 识别高重复内容；
- 识别低营养信息；
- 识别情绪性刷屏；
- 识别深夜算法使用；
- 识别过度依赖某类 App；
- 识别注意力耗竭和快速切屏。

### 激进端：制造低成本异常值

包括：

- 一条反常识内容；
- 一篇反向但高质量的材料；
- 一个小的行为偏离；
- 一次主动观察；
- 一条不同路线；
- 一次不同类型的社交、身体、空间、创作行动。

关键原则：

> 异常值不是随机作死，而是低成本、正期望、可拒绝的偏离。

## 1.4 可选性：产品不是替用户决定，而是增加选择

这个 App 不应该变成另一个控制用户的算法系统。

它不应该说：

> 你必须这样做。

它应该说：

> 我看到你正在重复这个路径，这里有一个低成本异常值，你可以接受、拒绝、延后或换一个。

因此，产品必须保留用户的：

- 查看权；
- 删除权；
- 拒绝权；
- 纠正权；
- 关闭权；
- 反馈权。

---

# 2. 项目的核心命题

## 2.1 核心问题：信息社会导致“精神肥胖”

项目的起点是一个判断：

> 信息社会并不让人缺少信息，反而让人摄入过量高刺激、低营养、低认知增量的信息，造成“精神肥胖”。

身体肥胖来自高热量、低营养的食物；精神肥胖来自高刺激、低增量的信息。

| 身体层面 | 信息层面 |
|---|---|
| 高糖高脂食物 | 短平快爽感内容 |
| 吃得多但营养差 | 看得多但认知增量低 |
| 身体代谢变差 | 注意力代谢变差 |
| 越吃越想吃 | 越刷越想刷 |
| 运动能力下降 | 深度思考能力下降 |

普通防沉迷工具的逻辑是：

> 你刷太多了，停下来。

这个 App 的逻辑是：

> 你刷的内容正在高度收敛，你的信息结构变胖了，需要引入异质、高营养、能产生认知摩擦的信息。

所以它不是简单减少信息摄入，而是改善信息代谢。

## 2.2 核心敌人：被动回归

这个 App 不是反对算法，也不是反对 AI。相反，它是“让魔法打败魔法”：用 AI 识别算法和习惯如何塑造用户，再用 AI 帮用户跳出被动回归。

它真正反对的是：

> 用户在不知不觉中，被平台推荐、社交压力、习惯路径和即时反馈塑造成一个越来越可预测的人。

它反对的不是“看短视频”，而是：

> 用户以为自己在自由选择，其实只是在重复被算法验证过的偏好。

它反对的不是“每天开完会回家”，而是：

> 用户的生活路径完全失去探索性，所有行动都变成最低阻力路径。

一句高度概括的产品精神是：

> 别让算法和惯性，把你训练成一个容易被预测的人。

---

# 3. 两类异常值：信息异常值与行为异常值

## 3.1 信息异常值

信息异常值指的是用户默认信息流之外的高价值信号。

它不是简单的“冷门内容”，也不是随便推相反观点，而是：

> 对用户当前信息结构有纠偏作用、能增加认知摩擦、能打开新问题或新路径的信息。

例子：

- 用户持续刷娱乐八卦，推荐一篇关于算法如何塑造情绪偏好的文章；
- 用户持续看同一立场观点，推荐一篇高质量反方材料；
- 用户大量接触焦虑内容，推荐一个更结构化、更低情绪污染的解释；
- 用户短视频内容高度集中在财富焦虑和颜值比较，提醒其信息营养失衡。

信息异常值的目标不是让用户看更多，而是让用户看得不那么被动。

## 3.2 行为异常值

行为异常值指的是对用户默认行为路径的低成本偏离。

它不是让用户随机做奇怪的事，也不是破坏正常秩序，而是识别那些“未经选择的固定行为”。

例如：

- 开完会 → 疲劳 → 直接回宿舍 → 刷手机；
- 上完课 → 食堂 → 宿舍 → 短视频；
- 睡前 → 空白感 → 打开抖音；
- 遇到任务压力 → 逃避 → 切到社交软件；
- 周末 → 躺着 → 焦虑 → 继续刷屏。

行为异常值的建议可以是：

- 不要立刻回宿舍，先走 10 分钟；
- 睡前不要马上打开短视频，先写一句今天最强烈的念头；
- 开完会后去一个平时不会去的空间停留 15 分钟；
- 焦虑时不要切 App，先用 3 分钟写下自己在逃避什么。

重点：

> 第一版行为异常值不需要自动获取位置，不需要做复杂路线识别。用户可以主动输入当前行为处境，AI 再分析回归路径并提出异常值建议。

---

# 4. 用户主动输入行为处境：行为异常值 MVP 的关键修正

之前讨论中曾默认行为异常值需要位置、路线、地理场景识别。但后续修正为：

> 第一版不需要自动获取位置。用户可以定期输入自己的当前行为总结，AI 根据这段行为摘要识别回归值，并提出行为异常值。

例如用户输入：

> 我刚刚开完组会，现在有点累，按照平时习惯我应该会直接回宿舍，然后刷会儿手机。

AI 输出：

> 检测到一个回归值行为：会议结束 → 疲劳 → 回宿舍 → 刷手机。这个路径的问题不是“回宿舍”本身，而是你可能又会进入低摩擦恢复模式。今日行为异常值：不要立刻回宿舍。先去操场走 12 分钟，期间不听歌，不刷手机，只观察今天脑子里最强烈的一个念头。回来后再决定要不要休息。

这个方向的优点：

1. 不需要位置权限；
2. 用户主动参与，隐私压力更小；
3. 产品更像认知教练，而不是监控器；
4. 更容易做 MVP；
5. 更符合“用户邀请 AI 观察自己的回归值”的理念。

因此，行为数据模型应支持：

```text
BehaviorEvent(sourceType = USER_REPORT)
```

而不是第一版就追求自动定位或路线识别。

---

# 5. 抖音 / 短视频信息异常值：悬浮窗 + 截图 OCR 的轻量方案

关于抖音内容分析，后续修正为：并不需要接入抖音官方数据，也不需要完整理解平台内部内容结构。

可行的第一版思路是：

```text
悬浮窗按钮 / 观察模式
        ↓
用户授权后定期采样屏幕
        ↓
本地 OCR 提取文字
        ↓
识别标题、字幕、评论、话题词、账号名
        ↓
生成 InformationEvent(sourceType = SHORT_VIDEO_OCR)
        ↓
分析主题重复度、情绪类型、信息营养度
        ↓
生成信息异常值建议
```

需要注意：

- 悬浮窗只是交互入口；
- 真正截图需要屏幕采集授权、无障碍能力或其他系统能力；
- 第一版可以先用 mock OCR 文本代替真实截图；
- 尽量本地 OCR；
- 默认不保存原截图，只保存提取文本、摘要、主题标签和内容 hash；
- 用户必须能随时停止观察、删除本轮记录。

这个方案比“深度接入抖音数据”现实，也比只看 App 使用时长更接近信息异常值分析。

---

# 6. 当前代码审计结论摘要

根据前几轮代码审计，当前项目已经有相当完整的基础骨架。

## 6.1 技术栈

- Android 原生 Kotlin / Java 17；
- Jetpack Compose；
- Navigation Compose；
- Room 数据库；
- WorkManager 后台调度；
- NotificationListenerService 通知采集；
- UsageStats 用机统计；
- 本地 LiteRT-LM；
- OpenAI-compatible API 通道；
- PromptRedactor 脱敏层；
- KhupPromptPolicy 世界观与导师风格 prompt。

## 6.2 UI 结构

底部 5 个 Tab：

- 首页；
- 消息；
- 用机；
- AI；
- 设置。

首页已经聚合：

- 异常；
- 诱因；
- 小时摘要；
- 干预；
- 任务；
- 复盘。

用机页已经展示：

- 今日总用机；
- 近 7 天；
- Top Apps。

设置页已有：

- 权限引导；
- AI 通道配置。

消息页已有：

- 分类浏览；
- 用户改分类；
- 写 feedback；
- 更新 derived_results。

## 6.3 采集与数据哲学

当前项目已经体现事件溯源思想：

- Event 作为不可变事实；
- eventId hash 幂等写入；
- DerivedResult 作为衍生投影；
- 衍生结果可以删除后重算；
- 采集层极简，不阻塞系统回调。

这非常适合后续扩展为“异常值认知教练”，因为它天然支持：

```text
事实事件 → 规则/AI 投影 → 用户反馈 → 重算/修正
```

## 6.4 已有能力

项目目前已经具备：

1. 通知采集；
2. 通知分类；
3. 通知小时摘要；
4. 用机统计；
5. AppSession 聚合；
6. 注意力异常检测；
7. 诱因标签；
8. 内容主题打标；
9. 信息茧房分析；
10. 每日复盘；
11. 干预提醒；
12. Overlay 目的闸门；
13. AI 聊天；
14. 本地/云端模型切换；
15. Prompt policy。

## 6.5 主要风险

当前审计指出的主要风险：

1. AI 输出全文写入 Log，有隐私泄露风险；
2. Room destructive migration 可能导致升级时清库；
3. 用户数据导出、删除、保留策略不足；
4. PromptRedactor 主要覆盖 API 通道，脱敏边界有限；
5. actions_log、chat、app_sessions 等长期数据保留策略不统一；
6. 用户反馈目前主要覆盖消息分类纠错，没有形成建议反馈闭环。

---

# 7. 当前项目最关键的断点

当前项目已经能“观察用户”，但还缺少“改变用户”的闭环。

它现在大概已经有：

```text
采集数据 → 统计 → 摘要 → 每日复盘
```

但还没有完整形成：

```text
识别回归值模式 → 生成异常值建议 → 用户反馈 → 下次建议变化
```

最核心的问题是：

> “异常值”还不是一个可持久化、可反馈、可学习的产品对象，而更多只是 DailyReview 文本中的建议。

因此，下一步不是继续堆更多分析文案，而是把以下三个概念实体化：

```text
RegressionPattern
AnomalySuggestion
UserFeedback
```

它们分别对应：

| 实体 | 产品含义 | 代码意义 |
|---|---|---|
| RegressionPattern | 用户正在被什么惯性困住 | 把分析器里的临时判断变成可追踪模式 |
| AnomalySuggestion | 今天建议用户怎么产生正期望偏离 | 把复盘文本变成可操作任务 |
| UserFeedback | 用户是否接受这个异常值 | 让系统能学习用户偏好 |

可以这样理解：

> Event 是事实，RegressionPattern 是诊断，AnomalySuggestion 是处方，UserFeedback 是疗效反馈。

---

# 8. 建议新增 / 改造的数据模型

## 8.1 InformationEvent

InformationEvent 表示一条信息摄入事件，例如：

- 通知；
- 消息；
- 网页标题；
- 短视频 OCR 文本；
- 用户主动输入的内容；
- AI 总结出的内容片段。

当前项目已有 Event + DerivedResult，可以承载最小版 InformationEvent，但需要扩展 sourceType。

建议字段：

```text
id
eventId
timestamp
sourceType
sourceId
appPackage
rawText
extractedText
contentHash
language
privacyLevel
confidence
createdAt
```

sourceType 可包括：

```text
NOTIFICATION
SCREEN_OCR
SHORT_VIDEO_OCR
USER_INPUT
WEB_TITLE
MANUAL_NOTE
```

## 8.2 BehaviorEvent

BehaviorEvent 表示一条行为事件。

第一版不需要位置，也不需要路线。重点支持用户主动输入：

```text
sourceType = USER_REPORT
```

建议字段：

```text
id
timestamp
sourceType
rawText
contextType
mood
energyLevel
expectedDefaultAction
detectedPatternId
createdAt
```

sourceType 可包括：

```text
USER_REPORT
APP_USAGE
TASK_CONTEXT
CALENDAR_CONTEXT
MANUAL_CHECKIN
```

contextType 可包括：

```text
AFTER_MEETING
AFTER_CLASS
BEFORE_SLEEP
TIRED_IDLE
AVOIDING_TASK
COMMUTING
SOCIAL_ANXIETY
UNKNOWN
```

## 8.3 RegressionPattern

RegressionPattern 表示用户的回归值模式。

建议字段：

```text
id
type
title
description
severity
confidence
firstSeenAt
lastSeenAt
frequency
status
evidenceSummary
createdAt
updatedAt
```

类型可以包括：

```text
LATE_ALGORITHM_USAGE
APP_USAGE_SPIKE
RAPID_APP_SWITCHING
NOTIFICATION_BURST
CONTENT_THEME_CONCENTRATION
LOW_NUTRITION_CONTENT
USER_REPORTED_DEFAULT_PATH
REPEATED_RECOVERY_PATTERN
```

状态可以包括：

```text
ACTIVE
COOLED_DOWN
DISPUTED
ARCHIVED
```

## 8.4 PatternEvidence

PatternEvidence 表示 AI/规则为什么判断某个回归值模式存在。

建议字段：

```text
id
patternId
sourceType
sourceId
eventTime
evidenceText
weight
createdAt
```

例如：

```text
sourceType = APP_SESSION
evidenceText = 23:41 - 00:28 使用 Douyin 47 分钟
```

或：

```text
sourceType = USER_REPORT
evidenceText = 用户输入：我刚开完会，平时会直接回宿舍刷手机
```

## 8.5 AnomalySuggestion

AnomalySuggestion 表示异常值建议。

建议字段：

```text
id
patternId
suggestionDomain
title
actionText
whyText
costLevel
expectedUpside
status
scheduledAt
expiresAt
modelVersion
createdAt
updatedAt
```

suggestionDomain 可包括：

```text
INFORMATION
BEHAVIOR
SOCIAL
SPACE
BODY
CREATION
```

status 可包括：

```text
PENDING
ACCEPTED
POSTPONED
REJECTED
COMPLETED
EXPIRED
```

costLevel 可包括：

```text
LOW
MEDIUM
HIGH
```

MVP 阶段只允许生成 LOW，因为异常值建议必须低成本。

## 8.6 UserFeedback

UserFeedback 表示用户对系统判断、建议、AI 输出的反馈。

建议字段：

```text
id
targetType
targetId
feedbackType
rating
reason
createdAt
```

targetType 可包括：

```text
SUGGESTION
PATTERN
DAILY_REVIEW
CLASSIFICATION
AI_MESSAGE
```

feedbackType 可包括：

```text
ACCEPT
REJECT
POSTPONE
COMPLETE
USEFUL
NOT_USEFUL
INACCURATE
TOO_SHARP
TOO_SOFT
```

这个统一反馈层可以替代或兼容现有 ClassificationFeedback。

## 8.7 ScreenOcrSample

用于抖音观察模式或其他屏幕内容采样。

建议字段：

```text
id
sessionId
timestamp
appPackage
extractedText
contentHash
ocrConfidence
rawImageSaved
createdAt
```

默认 rawImageSaved = false，即不保存原截图。

## 8.8 ShortVideoSession

用于记录一轮短视频观察。

建议字段：

```text
id
appPackage
startAt
endAt
sampleCount
summaryJson
themeDistributionJson
emotionDistributionJson
nutritionScore
createdAt
```

---

# 9. 修正后的 MVP 路线

当前更合理的 MVP 不是自动位置路线识别，而是两条轻量闭环。

## 9.1 行为线：用户主动输入 → 行为异常值

数据流：

```text
UserCheckInScreen
    ↓
BehaviorEvent(sourceType = USER_REPORT)
    ↓
RegressionPatternGenerator
    ↓
RegressionPattern(type = USER_REPORTED_DEFAULT_PATH)
    ↓
AnomalySuggestionGenerator
    ↓
AnomalySuggestion(domain = BEHAVIOR)
    ↓
Dashboard 今日异常值行动卡片
    ↓
UserFeedback
    ↓
下一次建议权重调整
```

### 示例

用户输入：

> 我刚开完会，有点累，平时会直接回宿舍刷手机。

系统识别：

> 会议后疲劳 → 回宿舍 → 刷手机恢复情绪。

系统建议：

> 不要立刻回宿舍。先去楼下走 10 分钟，只观察你现在最想逃避的念头。

用户反馈：

- 接受；
- 换一个；
- 不适合我；
- 已完成。

## 9.2 信息线：屏幕 OCR → 信息异常值

数据流：

```text
FloatingWindow Observe Mode
    ↓
ScreenOcrSample / MockOcrInput
    ↓
InformationEvent(sourceType = SHORT_VIDEO_OCR)
    ↓
ContentThemeTag / MentalNutritionAnalyzer
    ↓
RegressionPattern(type = CONTENT_THEME_CONCENTRATION)
    ↓
AnomalySuggestion(domain = INFORMATION)
    ↓
UserFeedback
```

### 第一版实现建议

第一版不直接接真实截图，先做 mock OCR 文本输入：

1. 用户手动复制 / 输入一段刷到的视频文字；
2. 系统将其作为 SHORT_VIDEO_OCR 类型 InformationEvent；
3. 规则或 AI 分析主题、情绪、信息营养；
4. 生成信息异常值建议。

第二版再接：

- 悬浮窗观察模式；
- 屏幕采样授权；
- 本地 OCR；
- 定时采样；
- 本轮刷屏报告。

---

# 10. 异常值建议的产品规则

所有 AnomalySuggestion 必须满足以下约束。

## 10.1 低成本

异常值不能让用户付出过高代价。

允许：

- 多走 10 分钟；
- 看一篇反方文章；
- 写一句观察；
- 换一个低风险空间；
- 暂停刷屏 20 分钟。

不允许：

- 突然跨城旅行；
- 临时翘课；
- 大额消费；
- 高压力社交；
- 危险行为。

## 10.2 正期望

异常值必须有潜在收益，包括：

- 新信息；
- 新感受；
- 新关系；
- 新身体状态；
- 新空间体验；
- 新判断角度；
- 新行动可能。

## 10.3 可拒绝

用户必须能：

- 接受；
- 拒绝；
- 延后；
- 换一个；
- 标记不准确；
- 标记太尖锐；
- 标记有帮助。

## 10.4 有证据链

每条建议都应该能回答：

> 你为什么给我这个建议？

证据来自：

- 用户输入；
- AppSession；
- Notification Event；
- HourlySummary；
- ContentThemeTag；
- AttentionAnomaly；
- ScreenOcrSample。

## 10.5 锋利但不羞辱

文案可以直接，但不能羞辱。

允许：

> 你今天的信息摄入非常顺滑，几乎没有遇到任何反对你偏好的内容。算法没有挑战你，它只是在喂养你。

不允许：

> 你太废了，又刷了一天垃圾内容。

---

# 11. 首页核心卡片设计

首页最重要的不是展示很多数据，而是展示一个明确行动。

建议首页核心卡片：

```text
今日异常值行动

你刚刚描述的默认路径是：
会议结束 → 疲劳 → 回宿舍 → 刷手机。

这个路径的问题不是“回宿舍”，而是你可能又会进入低摩擦恢复模式。

今天的异常值：
不要立刻回宿舍。先去楼下走 10 分钟，回来后再决定是否休息。

成本：低
预期收益：打断会议后自动进入算法恢复的惯性。

[接受] [换一个] [不适合我] [已完成]
```

这张卡片是产品灵魂。

它把：

```text
回归值识别 → 异常值建议 → 用户反馈
```

串了起来。

---

# 12. Prompt 设计原则

## 12.1 行为异常值 Prompt

输入：

- 用户当前行为处境；
- 用户自述的默认行为；
- 最近反馈；
- 可用时间；
- 是否允许社交建议；
- 是否允许外出建议。

输出必须是结构化 JSON。

示例输出：

```json
{
  "patternType": "USER_REPORTED_DEFAULT_PATH",
  "patternTitle": "会议后低阻力恢复路径",
  "patternDescription": "用户开完会后感到疲劳，默认会直接回宿舍并刷手机。",
  "suggestionDomain": "BEHAVIOR",
  "title": "先不要回到默认恢复路径",
  "actionText": "不要立刻回宿舍。先在楼下走 10 分钟，期间不刷手机，只观察你现在最想逃避的念头。",
  "whyText": "你现在不是单纯想休息，而是可能在寻找一个低摩擦的情绪缓冲。这个小偏离能让你重新获得选择权。",
  "costLevel": "LOW",
  "expectedUpside": "打断会议后自动刷手机的惯性，恢复对疲劳状态的主动处理。",
  "tone": "sharp_but_not_shaming"
}
```

约束：

- 只生成低成本建议；
- 不羞辱用户；
- 不制造危险行为；
- 不要求高成本社交；
- 不为了异常而异常；
- 建议必须具体可执行；
- 必须说明为什么这是正期望异常值。

## 12.2 信息异常值 Prompt

输入：

- 本轮 OCR 文本；
- 内容主题分布；
- 情绪类型分布；
- 重复主题；
- 用户近期信息偏好；
- 用户反馈历史。

输出：

- 信息茧房判断；
- 精神营养判断；
- 一个信息异常值建议；
- 可选的反向材料推荐。

示例：

```json
{
  "patternType": "CONTENT_THEME_CONCENTRATION",
  "patternTitle": "短视频内容高度集中在情绪争议",
  "diagnosis": "本轮刷屏内容主要集中在亲密关系争议、财富焦虑和颜值比较，属于高刺激、低行动增量的信息摄入。",
  "suggestionDomain": "INFORMATION",
  "title": "切出情绪回路",
  "actionText": "停止继续刷相似视频。看一篇关于推荐算法如何强化情绪偏好的短文，或者写下你刚才连续刷下去的原因。",
  "whyText": "你当前的信息流在强化情绪反应，而不是提供新的行动选项。",
  "costLevel": "LOW",
  "expectedUpside": "降低算法情绪牵引，恢复对信息摄入的主动权。"
}
```

---

# 13. 后续开发阶段建议

## Phase 0：安全底盘修复

目标：先解决隐私、数据丢失、用户控制权。

重点：

1. 去掉 LLM 输出全文 Log；
2. 修复 destructive migration；
3. 设置页加入数据删除入口；
4. 明确 app_sessions、chat、actions_log、daily_review 的保留策略；
5. 明确哪些数据会出端；
6. API prompt 之前必须脱敏和最小化。

验收标准：

- Logcat 不出现 LLM 输出全文；
- 数据库升级不清库；
- 用户能清空数据；
- 设置页能看到基本隐私说明。

## Phase 1：最小异常值闭环 MVP

目标：跑通：

```text
采集 / 输入 → 回归值模式 → 异常值建议 → 用户反馈 → 下次建议变化
```

优先实现：

1. BehaviorEvent(sourceType = USER_REPORT)；
2. InformationEvent(sourceType = SHORT_VIDEO_OCR / MOCK_OCR)；
3. RegressionPattern；
4. PatternEvidence；
5. AnomalySuggestion；
6. UserFeedback；
7. 首页“今日一个异常值行动”卡片；
8. 行为输入页面；
9. Mock OCR 输入页面；
10. 简单反馈影响建议权重。

验收标准：

- 用户输入行为处境后能生成行为异常值；
- 用户输入 / mock OCR 一段短视频文本后能生成信息异常值；
- 建议能接受、拒绝、延后、完成；
- 反馈能影响下一次建议类型；
- 建议有 pattern 和 evidence。

## Phase 2：产品哲学增强

目标：从分析工具升级为真正的异常值认知教练。

重点：

1. 信息茧房指数从固定阈值升级到个人基线；
2. 精神营养比；
3. 行为偏离指数；
4. 可选性指数；
5. 反向材料推荐；
6. 锋利但不羞辱的文案系统；
7. 7/30 天趋势。

## Phase 3：体验与长期记忆

目标：提升长期陪伴、可解释性、用户信任。

重点：

1. 证据链详情页；
2. 用户纠正 AI 判断；
3. 自定义监控 App / 主题 / 敏感词 / 勿扰时段；
4. 复盘历史趋势；
5. 长期记忆管理；
6. 导出、备份、恢复；
7. 本地优先隐私边界。

---

# 14. 给 CLI / Codex 的下一步提示词

下面这段可以直接发给 CLI 或 Codex，让它基于当前项目继续做只读设计或开始小步实现。

```text
你现在继续审计并推进当前 Android Kotlin 项目。项目目标是一个“异常值认知教练”App，不是普通防沉迷工具。

最新产品方向已经修正：

1. 行为异常值第一版不做自动定位/路线采集。
   用户主动输入当前行为处境，例如“我刚开完会，平时会直接回宿舍刷手机”。
   系统把这条输入作为 BehaviorEvent(sourceType=USER_REPORT) 落库。
   AI 或规则识别默认回归路径，生成低成本、正期望、可拒绝的 AnomalySuggestion。
   用户可以接受、换一个、不适合我、已完成，并写入 UserFeedback。

2. 信息异常值第一版不接入抖音官方数据。
   目标是悬浮窗观察模式 + 屏幕采样 + 本地 OCR。
   但第一版可以先用 mock OCR 文本输入代替真实截图。
   OCR 文本作为 InformationEvent(sourceType=SHORT_VIDEO_OCR 或 MOCK_OCR) 落库。
   系统分析主题重复度、情绪类型、信息营养度，并生成信息异常值建议。

请先不要大规模重构，不要接真实位置，不要接真实截图。

请完成以下任务：

一、检查当前代码是否已有或可复用：
- 用户主动输入行为处境的入口；
- BehaviorEvent 或类似表；
- AnomalySuggestion 独立表；
- UserFeedback 通用反馈表；
- 悬浮窗服务是否可复用为“观察模式入口”；
- OCR 接口或屏幕采样接口；
- InformationEvent 是否能承载 SCREEN_OCR / SHORT_VIDEO_OCR 来源。

二、请提出最小实现方案：
新增或改造：
- BehaviorEvent；
- InformationEvent.sourceType 扩展；
- ScreenOcrSample；
- ShortVideoSession；
- RegressionPattern；
- PatternEvidence；
- AnomalySuggestion；
- UserFeedback。

三、请给出新的最小数据流：

行为线：
UserCheckInScreen
→ BehaviorEvent(sourceType=USER_REPORT)
→ RegressionPattern(type=USER_REPORTED_DEFAULT_PATH)
→ AnomalySuggestion(domain=BEHAVIOR)
→ UserFeedback
→ 下一次建议权重调整

信息线：
MockOcrInput / FloatingWindow Observe Mode
→ ScreenOcrSample
→ InformationEvent(sourceType=SHORT_VIDEO_OCR)
→ ContentThemeTag / MentalNutrition
→ RegressionPattern(type=CONTENT_THEME_CONCENTRATION)
→ AnomalySuggestion(domain=INFORMATION)
→ UserFeedback

四、请给出小步实现路线：
Step 1：先做用户手动输入行为处境，不接位置。
Step 2：先用 mock OCR 文本模拟抖音观察，不接真实截图。
Step 3：跑通 AnomalySuggestion + UserFeedback。
Step 4：让反馈影响下一次建议。
Step 5：再考虑真实屏幕采集和本地 OCR。
Step 6：最后考虑悬浮窗定时采样。

五、输出要求：
- 需要新增/修改的文件；
- 需要新增/修改的数据表；
- 需要新增/修改的 Repository / DAO / Service；
- 需要新增/修改的页面；
- Prompt 结构；
- 风险点；
- MVP 验收标准。

限制：
- 不要大规模重构；
- 不要直接接位置；
- 不要直接接真实截图；
- 不要让 LLM 输出全文写入 Log；
- 新增表要考虑 Room migration；
- 所有异常值建议必须低成本、正期望、可拒绝、有证据链。
```

---

# 15. 一句话总结

当前项目已经有“信息/行为观察”和“LLM 复盘”的骨架，但还没有真正形成“异常值认知教练”的闭环。下一步最关键的不是继续堆报告，而是把 `RegressionPattern → AnomalySuggestion → UserFeedback` 做成独立、可持久化、可反馈、可学习的产品链路。行为异常值先从用户主动输入做起，信息异常值先从 mock OCR / 屏幕文字采样做起，等闭环跑通后再接真实截图、悬浮窗采样、长期记忆和更强的个性化推荐。

