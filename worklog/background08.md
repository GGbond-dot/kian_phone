# Background 08 — 2026-05-06

> 给下一个对话用的上下文。新会话开始时把 `background01..08.md` 一起贴进去。
> 这份是**设计文档**，本轮没有改业务代码。覆盖：每日复盘深化、异常值检测、信息茧房干预、多会话 AI 聊天。

---

## 本轮讨论的方向

用户希望 KHUP 后续不只是统计“用了多久”，而是能逐渐做到：

1. 理解自己每天被哪些信息牵引。
2. 在每日总结里给出更深层的分析。
3. 发现异常值，比如某类内容 / 某个 App / 某个时段突然失控。
4. 在合适的时候把用户拉出信息茧房。
5. AI 聊天页不只是一个单会话聊天框，而是能新开聊天、查看过往聊天窗口。

这个方向可行，而且应该作为 KHUP 的后续核心能力之一。

---

## 一、每日复盘要进化成“注意力复盘”

### 当前每日复盘设计的不足

`background06.md` 里每日复盘主要输入：

- 今日总用机时长 + Top 5 apps
- daily_tasks 完成情况
- action_logs 干预记录
- 今日通知数 + 高频包名
- hourly_summary 拼接

输出是三段：

- 亮点
- 警示
- 明日建议

这个是可用 MVP，但深度不够。它更像“日报”，不是“注意力教练”。

### 新目标

每日复盘应该回答这些更深的问题：

- 今天你的注意力主要被什么拿走？
- 哪些信息源反复把你拉回去？
- 哪些通知 / 内容主题明显造成了打断？
- 哪些 App 使用是主动选择，哪些像是被算法牵引？
- 有没有异常值：比你过去 7 天明显更高、出现时间更危险、内容主题更集中？
- 明天最应该防的不是哪个 App，而是哪类诱因？

---

## 二、能力分层：先异常值，再诱因，再信息茧房

### Level 1：异常值检测，不需要 LLM

先做统计层异常，最稳。

数据来源：

- `app_sessions`
- `events`
- `derived_results`
- `hourly_summary`
- `action_logs`
- `daily_tasks`

可检测：

- 某 App 今日屏幕前台时长 > 过去 7 天均值 + 2 倍标准差
- 某 App 今日打开/活跃次数明显增多
- 某时间段，比如 22:30 后，高刺激 App 使用超过阈值
- `algorithmic` / `promotion` 通知数量暴增
- 某联系人 / 群 / App 在 1 小时内高频打断
- 睡前高刺激使用：抖音 / 小红书 / B 站 / 游戏 / 微博等在晚间连续使用

建议新表：

```text
attention_anomaly
- id
- dayStartMs
- type
- sourcePackage
- sourceLabel
- windowStartMs
- windowEndMs
- score
- baselineValue
- currentValue
- explanation
- createdAt
```

示例：

```text
22:10-23:00 抖音使用 42 分钟，高于过去 7 天同时间段均值 2.8 倍。
```

### Level 2：诱因标签，需要本地 LLM

对通知标题、摘要、小时摘要做轻量标签。

不是直接“理解你刷了什么”，而是先理解“什么信息把你拉回去”。

候选标签：

- `social_pressure`：关系压力 / 必须回人
- `task_obligation`：任务 / 作业 / 审批 / 截止时间
- `reward_loop`：奖励 / 抽卡 / 红包 / 签到 / 连续奖励
- `novelty`：猎奇 / 新鲜事 / 热榜
- `conflict`：争议 / 对立 / 情绪化新闻
- `consumption`：消费 / 种草 / 优惠
- `status_anxiety`：成绩、排名、钱、成长焦虑
- `entertainment_pull`：短视频 / 游戏 / 直播 / 娱乐内容

建议新表：

```text
attention_trigger
- id
- eventId nullable
- windowStartMs nullable
- sourcePackage
- triggerType
- confidence
- evidence
- createdAt
- modelVersion
```

本地 E2B 可以先跑短 JSON 输出：

```json
{"trigger_type":"reward_loop","confidence":0.78,"evidence":"签到、奖励、限时"}
```

### Level 3：信息茧房干预，需要更谨慎

目标不是“禁止用户刷”，而是在发现单一主题 / 单一情绪 / 单一立场反复出现时，给一个轻量反向视角。

可做的干预：

- 视角补全：你今天看到很多同类观点，要不要看 2 个反向问题？
- 冷却提醒：这个主题今天已经出现 18 次，后续 2 小时同类通知静默。
- 事实核查：对情绪化标题给出“先确认这 3 件事实”。
- 兴趣迁移：把低质量内容转成高质量内容，比如篮球八卦 → 战术分析。
- 自我提问：你现在是想解决问题，还是在继续找刺激？

注意边界：

- 不要假装知道用户屏幕上刷过的全部内容。
- 先基于通知标题、摘要、App 使用、小时摘要推断。
- 如果后续接 Accessibility / 屏幕 OCR，需要明确用户授权和隐私边界。

---

## 三、是否需要装 skill / 额外能力

短期不需要装 Codex skill。原因：

- 当前这些能力是 KHUP App 内的业务功能，不是开发环境技能。
- 已有本地 LLM + API 路由 + Room + WorkManager，基础设施够用。

但需要在 App 内新增“分析技能模块”，可以理解为 KHUP 自己的 skills：

```text
core/attention/
- AttentionAnomalyDetector
- AttentionTriggerClassifier
- AttentionReviewBuilder
- InformationCocoonInterventionPlanner
```

后续如果要做“事实核查 / 反向视角 / 外部内容推荐”，才需要考虑：

- API 搜索能力
- curated sources
- RSS / 可信信息源列表
- 或者单独的检索模块

第一阶段不建议联网检索，先做本机数据分析。

---

## 四、每日复盘新 Prompt 结构

每日复盘应从“总结今天”升级为“解释今天”。

建议输入：

```text
你是 KHUP 注意力复盘助手。

今天统计：
- 屏幕点亮总时长：12小时18分
- Top Apps：浏览器 3小时35分，抖音 2小时29分，微信 1小时25分...
- 今日任务：...
- 干预记录：...

通知和打断：
- 总通知数：...
- 分类分布：社交 40，推广 12，算法推送 9...
- 高频来源：微信、钉钉、学习通...

小时摘要：
- 16:00-17:00：...
- 17:00-18:00：...

异常值：
- 22:10-23:00 抖音使用显著高于过去 7 天
- 推广通知比平时多 2.1 倍

诱因标签：
- reward_loop：...
- social_pressure：...
- novelty：...
```

建议输出：

```text
1. 今天注意力被什么拿走
2. 主要诱因
3. 异常值
4. 信息茧房风险
5. 明天一个具体防线
```

输出长度：200-350 字。不要泛泛鼓励，要指出具体时间段、App、诱因。

示例：

```text
今天真正消耗你的不是单个 App，而是下午到晚上的连续打断：微信社交消息把你拉回手机，随后抖音和浏览器承接了注意力。16:00-17:00 的通知密度较高，但重要性不高，属于低价值打断。

异常值是晚间短视频使用：抖音在睡前阶段明显偏高，这类内容更像奖励循环，不是明确任务。今天的信息输入也偏娱乐和即时反馈，缺少学习/长期目标相关内容。

明天建议只设一条防线：22:00 后抖音和浏览器连续使用超过 15 分钟时，KHUP 直接提醒你写下“我现在要查什么”，没有目的就退出。
```

---

## 五、多会话 AI 聊天设计

### 当前状态

当前 AI 聊天是单会话持久化。

现有表：

```kotlin
chat_message(
    id,
    role,
    text,
    providerTier,
    timestamp
)
```

现有逻辑：

- `AiChatViewModel.init` 加载最近 100 条消息
- `send()` 写 user / assistant 两条
- `clear()` 删除整张 `chat_message`
- 没有 `conversationId`
- 没有会话列表
- 没有新建聊天
- 不能查看历史聊天窗口

所以用户看到的确实不是成熟聊天框。

### 目标

AI tab 应该支持：

- 新建聊天
- 查看历史聊天列表
- 切换历史聊天
- 当前会话标题自动生成
- 删除单个会话
- 清空当前会话，而不是清空所有会话

### 数据库设计

新增表：

```text
chat_session
- id INTEGER PRIMARY KEY AUTOINCREMENT
- title TEXT NOT NULL
- createdAt INTEGER NOT NULL
- updatedAt INTEGER NOT NULL
- lastMessagePreview TEXT
```

修改 `chat_message`：

```text
新增 sessionId INTEGER NOT NULL DEFAULT 1
索引 sessionId, timestamp
外键 sessionId -> chat_session(id) ON DELETE CASCADE
```

迁移策略：

- Room v5 → v6
- 创建 `chat_session`
- 插入一条默认历史会话，标题 `旧对话`
- 给现有 `chat_message` 增加 `sessionId = 1`
- 后续所有消息按 session 写入

### UI 设计

AI tab 顶部：

- 左侧：当前会话标题
- 右侧：
  - 新建聊天 icon
  - 历史列表 icon
  - 清空当前会话 icon

历史列表：

- 可以先做 `ModalBottomSheet`
- 每项显示：
  - title
  - lastMessagePreview
  - updatedAt
  - delete icon

标题生成：

- MVP：第一条用户消息前 18 个字作为标题
- 后续：让本地 LLM 生成 8 字以内标题

### ViewModel 变化

`AiChatViewModel` 需要维护：

```kotlin
currentSessionId: Long
sessions: List<ChatSession>
messages: List<ChatMessage>
```

核心方法：

```kotlin
newSession()
selectSession(sessionId)
deleteSession(sessionId)
clearCurrentSession()
renameSession(sessionId, title)
```

`buildPrompt()` 只取当前 session 的消息。

---

## 六、建议执行顺序

### 第一阶段：聊天框变成熟

优先做，因为这是用户明显感知到的基础体验问题。

1. Room v5 → v6：新增 `chat_session`，`chat_message` 加 `sessionId`
2. DAO / Repository 改成按 session 查询
3. AI tab 加新建聊天 + 历史列表
4. clear 改成清当前会话
5. 旧消息迁移到 `旧对话`

### 第二阶段：异常值检测

1. 新增 `AttentionAnomalyDetector`
2. 每天 / 每小时跑一次
3. Dashboard 或每日复盘里展示异常值
4. 不依赖 LLM，先用规则跑准

### 第三阶段：每日复盘深化

1. 每日复盘输入加入异常值、小时摘要、诱因标签
2. 输出改成“注意力复盘”
3. 周日加入一段“本周趋势”

### 第四阶段：诱因标签 + 信息茧房

1. 本地 LLM 给通知 / 小时摘要打 trigger 标签
2. 统计最近 3-7 天主题重复度
3. 给轻量反向视角和冷却建议

---

## 注意事项

1. 不要一开始接 Accessibility / OCR。先用通知、屏幕时长、App 前台、小时摘要做 60 分版本。
2. “拉出信息茧房”不能做成说教，应做成具体、短、可执行的反向问题。
3. 每日复盘不要堆太多术语，用户要能一眼看懂今天为什么失控。
4. 多会话聊天要先做，否则后续自然语言查询、每日复盘追问都会混在一个历史里。
5. API Heavy 可以负责深度复盘，本地 Light 可以负责小时摘要和短标签。

