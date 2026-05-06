# Background 06 — 2026-05-06

> 给下一个对话用的上下文。新会话开始时把 `background01..06.md` 一起贴进去。
> 这份是**纯设计文档**,本对话框没改代码。下一轮对话框按这份执行。
> 覆盖 2026-05-06:API 中转站接通 + 隐私脱敏 + Hybrid 路由档位化 + A 类业务功能方案敲定。

---

## 本轮已动代码(给下一轮起点)

1. **UsageStats 跨夜 bug 真修好** — `UsageStatsRepository.todayStartFlow()` 从 "delay 到午夜" 改成 1 分钟轮询 + distinctUntilChanged 语义。Doze 唤醒后最多 60s 跨天。bg02 那次只修了 Flow 逻辑,但 `delay()` 走 uptimeMillis 在深睡时停摆,所以 5/4 那个 bug 实际还在。
2. **API 中转站接通** — base URL `https://api.kipfel.top/v1`,model 用 `gpt-5.4`,Provider Mode 设 `ApiOnly`。已在设备上验证自检通过。
3. **AI tab 改名** — 标题 `本地 AI` → `kian-ai-chat`,placeholder / 系统 prompt 同步改。副标题按 ProviderMode 分支显示状态(API 已配 / 本地就绪 / 兜底等)。
4. **TaskTier 路由 + 脱敏** — `LlmEngine.generate(prompt, tier)`,`Light=本地强制 / Heavy=API 强制 / Auto=按 ProviderMode`。`PromptRedactor` 在 `ApiLlmEngine` 入口跑,覆盖手机号 / 邮箱 / 身份证 / 验证码上下文 / IPv4。logcat 里看 `redaction_delta=N`。**聊天界面默认 Auto,行为不变。**

---

## 本轮拍板的设计(下一轮按这个做)

### 0. 消息保留期 7 → 3 天

- 改 `RetentionWorker.RAW_EVENT_RETENTION_DAYS = 3L`
- `derived_results / app_sessions / action_logs / daily_tasks / classification_feedback` 仍永久
- 一行改动

### 1. 通知摘要(每小时,本地 Light)

**触发**:WorkManager periodic 1 小时,Doze 时系统会推迟到下次活跃。

**窗口**:过去 1 小时 events。

**Prompt**:
```
你是 KHUP 通知摘要器。过去 1 小时收到这些通知:
[11:05 微信] 张三:xxx
[11:12 钉钉] 周会延期到明天 ...
要求:用 1-2 句中文概括这小时谁找你、有什么重要事项、是不是被打扰多。
不要列举每条,不要"好的"开头。
```

**输入裁剪**:Gemma 4 E2B 上下文 2048 token,1 小时通知超量时截最近 N 条 + prompt 末尾追加"还有 X 条未列出"。

**存储**:新表 `hourly_summary`
- `id` PK / `windowStartMs` / `windowEndMs`(unique 联合 windowStartMs)/ `summary` / `eventCount` / `topPackages`(JSON 字符串) / `importance`(0-3)/ `createdAt` / `modelVersion`

**Dashboard**:顶部新增"最近 1 小时"卡片(摘要 + 通知数 + 时间窗 + 重要程度 chip)。

**重要提醒推送**:
- LLM 输出 JSON `{"summary": "...", "importance": 0-3, "trigger_packages": [...]}`
- **只有当 `importance >= 2` 且 `trigger_packages` 命中以下白名单**才发本地通知:
  - 学习通 `com.chaoxing.mobile`
  - 钉钉 `com.alibaba.android.rimet`
- 其它 app 即使 importance 高也只静默生成摘要,不打扰
- 复用现有 `khup_intervention` 通知 channel 或新建 `khup_summary`(建议新建,语义不同)

**边界**:
- 0 通知 → 不生成、不入库
- LLM JSON 解析失败 → 退化为纯文本,importance 默认 0
- Worker 跟主进程同进程,`LiteRtLmLlmEngine` Singleton 复用引擎;Worker 长时间没跑被回收时仍冷启 5-10s,可接受

### 2. 每日复盘(每天 22:00,API/Heavy)

**触发**:WorkManager periodic + `setRequiresCharging(false)` + 计算到下一个 22:00 的初始延迟。Dashboard 卡片同时提供"立即生成"按钮(可重跑覆盖)。**时间固定 22:00,不做 Settings 配置项**。

**输入**(纯统计数据,无具体通知内容、无好友名,API 隐私风险低):
- 今日总用机时长 + Top 5 apps
- 今日 daily_tasks(完成 / 未完成,带 title)
- 今日 actions_log(阈值提醒次数 + purpose_gate 提交的目的文本)
- 今日通知数 + 高频包名
- (可选)Top apps 的 hourly_summary 拼接

**输出**:三段中文,150-250 字
- 亮点(完成了什么、克制得好的地方)
- 警示(被吃掉的时间、沉迷迹象)
- 明日建议(1-2 条具体可执行)

**存储**:新表 `daily_review`
- `id` PK / `dayStartMs`(unique) / `summary` / `highlights`(JSON,三段拆开)/ `createdAt` / `modelVersion`

**Dashboard**:主线任务卡片下方加"今日复盘"卡片;22:00 前显示空状态 + "立即生成"按钮;22:00 后显示内容 + "重新生成"按钮。

### 3. 沉迷原因 / 干预建议(API/Heavy)

**MVP 不单独做。** 合并进每日复盘 — 周日(`dayOfWeek == SUNDAY`)的复盘 prompt 自动带"本周趋势"段:输入额外塞过去 7 天的 `daily_review.summary` + 7 天用机趋势。

输出在原"明日建议"前再加一段"本周回顾"。

后续如果用户想要单独的"周报"页面再说。

### 4. 自然语言查询(本地/Light,聊天框内)

**路线 C — 关键词命中 + LLM 包装**:

1. AI tab `AiChatViewModel.send()` 入口先跑一遍意图识别
2. 命中预设模板 → 应用层跑 SQL → 把数字交给本地 LLM 包装成自然语言 → 回写聊天
3. 不命中 → 走原 LLM(按 ProviderMode 分流)

**预设查询模板**(MVP 5-7 个):
- `<app名> 今天多久` / `今天 <app名> 用了多久`
- `今天用机多久` / `今天总共玩了多久`
- `这周用机多少` / `本周用机`
- `今天通知多少` / `今天收到多少通知`
- `<app名> 这周多久`

意图匹配:简单中文关键词 + 包名映射表(抖音 / 小红书 / 微信 / 钉钉 / B 站 / 学习通)。命中率不高没关系,fallback 走 LLM。

**优先级**:第三波,等 1/2/5 跑稳。

### 5. LLM 分类(替换 rules-v1)— 两步走

**痛点**:微信里支付记录、公众号推送被一锅端到"社交"。

**Step 1 — 规则增强(立即做)**:

`RuleNotificationClassifier.kt` 改:

- **新增分类**:`finance`(中文显示"金融通知")—— 银行 / 支付 / 信用卡到账
- 微信(`com.tencent.mm`)特殊处理,**先看 title/text 再决定**:
  - title 含 "微信支付" / text 含 "转账/收款/红包/到账/已退款" → `finance`
  - title 是公众号格式(常见模式:`<公众号名>` 单行 title + 短摘要 text;或 title 含 "订阅号消息" / "订阅号")→ `promotion`
  - 都不是 → `social`
- QQ(`com.tencent.mobileqq`)同理:
  - "QQ钱包" / "转账" / "红包" → `finance`
  - "QQ订阅" / "公众号" → `promotion`
  - 否则 → `social`
- 银行 / 支付宝包名整体进 `finance` 白名单:
  - `com.eg.android.AlipayGphone`(支付宝)
  - `com.unionpay`(云闪付)
  - 工行 / 招行 / 建行等(列一个常见银行包名表)
- Messages tab 顶部 chip 加 `finance`,顺序建议:社交 / 全部 / 验证码 / 金融通知 / 工作 / 推广 / 算法推送 / 其他

**推广类不拆**(订阅号 vs 营销),先看 Step 1 跑下来效果再说。

**modelVersion 升到 `rules-v2`**。

**Step 2 — LLM 分类(等通知摘要稳了再做)**:

- 复用功能 1 的本地引擎基础设施
- 新建 Worker `LlmClassifyWorker`,每 30 分钟扫一批未走 LLM 分类的事件
- 输入:title + text + packageName,输出 JSON `{"classification": "social"}`
- 写回 `derived_results`,modelVersion = `gemma-4-e2b-cls-v1`
- 高置信规则(验证码、金融关键词)仍走规则,**LLM 只覆盖模糊场景**(`social` / `promotion` / `algorithmic` 三选)
- 失败回退保留 rules-v2 结果

### 6. AI 聊天历史持久化(本轮临时加的)

**问题**:进程被杀 / 重启后,AI tab 对话历史丢失。

**方案**:单会话持久化(MVP),后续可扩多会话。

**新表 `chat_message`**:
- `id` PK auto / `role`(user/assistant)/ `text` / `providerTier`(实际走的是 local/api,排查用)/ `timestamp`

**改动**:
- `AiChatViewModel.init` 启动时从 DB 加载全部消息(或最近 100 条防爆)
- `send()` 成功后把 user / assistant 两条都写 DB
- `clear()` 同时 `DELETE FROM chat_message`
- `buildPrompt()` 上下文长度按 tier 分:
  - 实际走本地 → `MAX_CONTEXT_MESSAGES = 8`(同现状,Gemma 2048 token 顶不住更多)
  - 实际走 API → `MAX_CONTEXT_MESSAGES_API = 20`(GPT-5 上下文够大,可以更长记忆)
- "实际走哪条"在 `HybridLlmEngine` 决定后回传给 ViewModel 难度高,简化:**直接看 ProviderMode**(LocalOnly / LocalFirst → 8;ApiOnly → 20)

后续可扩:
- 多会话(类 ChatGPT 侧栏)
- 单条消息删除 / 重新生成
- 系统 prompt 用户可改

---

## 优先级 / 执行顺序(下一轮主线)

### 第一波(下一个会话核心)

1. 消息保留 7 → 3 天 ⏱ 1 分钟
2. 规则增强 + 金融通知分类(功能 5 Step 1) ⏱ 30 分钟,立刻解决微信痛点
3. AI 聊天历史持久化(功能 6) ⏱ 1 小时
4. 通知摘要每小时 + 学习通/钉钉重要提醒(功能 1) ⏱ 2-3 小时,**大头**

### 第二波

5. 每日复盘 22:00(功能 2)
6. LLM 分类替换(功能 5 Step 2)

### 第三波

7. 自然语言查询(功能 4)
8. 周报合并(功能 3,塞进周日复盘)

### 仍未做(bg05 遗留)

- HybridLlmEngine 真机回归(虽然今天 ApiOnly 单链路验过,但 LocalFirst 路径未测)
- Engine 预加载(app 启动就 init)
- 流式输出(`sendMessageAsync` Flow)
- XNNPack cache 验证(冷启动两次对比 TTFT)
- 删 `setNativeMinLogSeverity` 死代码
- `abiFilters arm64-v8a` 瘦 APK
- `.venv/` 进 `.gitignore`

---

## 数据库迁移规划

下一波要新增 3 张表,Room version 4 → ?:

- `hourly_summary`(功能 1)
- `daily_review`(功能 2)
- `chat_message`(功能 6)

建议**一次升到 v5**,在同一个 `MIGRATION_4_5` 里 CREATE 三张表。比分三次 minor migration 干净。注意 `derived_results.classification` 列要新增 `finance` 这个值,但因为是 String 不是 enum,**不需要 schema 迁移**,改 `RuleNotificationClassifier` 就行。

---

## 注意事项给下一轮

1. **本轮代码已装到设备**(API 通道 + 脱敏 + tier 路由),下一轮直接接着改,不要回滚。
2. **kipfel.top API 中转站、模型名 gpt-5.4 已存进设备 SharedPreferences**,不会丢。
3. **学习通 / 钉钉包名先列出确认**:
   - 学习通:`com.chaoxing.mobile`
   - 钉钉:`com.alibaba.android.rimet`
   - 真机收一条这两个的通知,从 logcat 抓 `KHUP/NLS` 里的 packageName 确认
4. **金融通知分类的银行包名表**要现查现填,直接在用户设备上 `pm list packages | grep -i bank` 之类,加进白名单
5. **22:00 复盘 Worker** 要用 `setInitialDelay` 算到下一个 22:00,而不是 `PeriodicWork` 直接 24 小时(对齐挂表时间用 OneTime + reschedule 模式更稳)
6. **聊天历史的 ProviderMode 推断 tier** 是简化,如果以后接入业务的 Light/Heavy 显式 tier,要重新设计:可能要在 ViewModel 里持有"上次实际通道"状态而不是 ProviderMode
7. **新建通知 channel `khup_summary`** 跟 `khup_intervention` 分开,用户可以单独关掉摘要的提醒
8. **每小时 Worker 频率**:WorkManager periodic 最小 15 分钟。1 小时设 `PeriodicWorkRequest(1, HOURS)` 即可

---

## 调试速查

```bash
# 本轮新接口的 logcat 过滤
adb logcat -s KHUP/AI:V

# 看脱敏触发情况
adb logcat -s KHUP/AI:V | grep redaction_delta

# 装最新 APK
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk

# 查包名(下一轮做学习通/钉钉重要提醒会用)
adb shell pm list packages | grep -iE "chaoxing|rimet|alipay|bank"

# 摘要 Worker 触发(下一轮加完后用)
adb shell cmd jobscheduler run -f com.kian.khup.debug 0
```
