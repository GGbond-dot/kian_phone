# KHUP UI 重设计文档 v1.1

> 本文是 KHUP UI / 产品设计的**实施规范**。所有设计决策已由 Kian 锁定（§10）。
> 实施工具按 §11 的 Phase 顺序执行，每个 Phase 落地后由 Kian 验收，通过后再进下一个 Phase。
> 文档作者（Claude Opus）担任总工程师 / 设计负责人，**不直接写代码**，本文是唯一信息源。

---

## 0. 背景与约束

### 0.1 当前状态

KHUP 已有 4 个 Tab、9+ 张设置卡、3 个历史子 Tab、完整 AI 聊天链路。功能层面无重大缺失，问题集中在**视觉权重 / 信息层级 / 文案语气**三件事上。AI 响应慢的问题已由 Kian 通过换更高质量 API 解决，本文不再为"慢"做 UI 兜底。

### 0.2 用户使用习惯（来自 Kian 当面确认）

- **U1**. 首页主交互不是「接受/换/不适合」，是**「先和 AI 聊几句这条建议为什么给我」**，谈完再决定是否实施。
- **U2**. AI Tab 打开频率低，根因是响应慢 + prompt 不完善。慢已解决；prompt 后续优化。
- **U3**. 现阶段单人使用（Kian 自己），但视觉资产按"可推广"标准做。
- **U4**. **任何业务功能都不能删**。重组、折叠、降级、收纳 可以；删除需明示。

### 0.3 视觉方向

**B 日记式** —— 首页是 "AI 写给你的今日这一页"，保留三块内容（建议 / 检入 / 观察）但**重做信息层级和语气**，让一屏只有一个焦点。

### 0.4 实施工具必读

**文档读者**：本文的实施由其他 AI 工具（实施 Agent）按 §11 的 spec 顺序执行。Kian 担任产品决策与验收，本文档作者不写代码。每个 Phase 落地后实施 Agent 向 Kian 演示，Kian 通过后再进下一个 Phase。

**实施工具强制约束**：

1. **功能保留原则**：本文如出现"删除"二字，明确范围是**视觉层 UI 元素**（图标、按钮、容器、文案）；底层 `ViewModel` / `Repository` / `DAO` 的方法和字段**全部保留**。如某个 UI 元素的删除会导致某个 ViewModel 方法不再被调用，**该方法仍保留**，不做 `@Deprecated` 标记，不做"清理"，留着备用。
2. **Schema 变动白名单**：本次改造**仅一处** Room schema 变动 —— `DailyPlan` 表加 `sourceSuggestionId: Long?` 字段（§4.5 闭环数据流必需）。Room migration 需写规范的 `Migration(n, n+1)` 类，不允许 `fallbackToDestructiveMigration`。
3. **依赖白名单**：本次改造**不引入**任何新 Gradle 依赖。字体不引入（用系统字体）、动效库不引入（用 Compose 原生 `animate*AsState`）、图表库不引入（继续用现有 Canvas）。
4. **文案严格表**：UI 文案以 §8.1 术语表为准。表中"当前 UI 词 → 替换为"的映射，实施 Agent 必须**全局检索替换**，不允许遗漏。如发现 §8 未覆盖的术语，停下来反馈给 Kian，不要自作主张换词。
5. **提交粒度**：每个 spec（§11 的子项，如 P1.1 / P1.2 ...）一次 git commit。commit message 用本文档的 spec 编号开头，例如 `P2.3: 建议卡按钮重排 + acceptSuggestion 写入计划`。
6. **构建门禁**：每次 commit 前必须跑通 `./gradlew :app:compileDebugKotlin`（无 error，warning 允许）。涉及 Compose 预览的改动建议附 `@Preview` 截图给 Kian 看。
7. **现实校对**：如发现本文档与代码现实矛盾（字段不存在 / 方法签名变了 / Hilt binding 缺失），**停下来反馈给 Kian**，不要自作主张改文档或绕路实现。
8. **不做的事**：不重构 ViewModel 状态机、不抽新模块、不引入 BFF 思想、不写新的 Manager / Coordinator 类。本次改造是"换外壳"，不是"重构内核"。
9. **设计 token 写在哪**：颜色 / 字号 / 间距 / 圆角 的 token 全部进 `output/ui/theme/` 目录，**只改这一个目录的下沉资产**，不在业务页面里写 magic number。

---

## 1. 五条设计原则

| # | 原则 | 项目语境下的具体含义 |
|---|------|--------------------|
| 1 | 一屏一个焦点 | 每个页面必须有**且只有**一个视觉中心。其他元素降权重为它服务。 |
| 2 | 减重不减功能 | 9 张设置卡、5 个 AI 按钮、3 个历史子 Tab 都要保留，但用**分层 + 折叠**让首屏只暴露 30%。 |
| 3 | 数据要说人话 | UI 上不出现"异常值 / 回归值 / 接受率 / 模型路径"这类词。统计数字**翻译成自然语言句子**。 |
| 4 | 主动作清晰 | 任何卡上的多个按钮必须有明确主次：1 主按钮（Filled）+ N 次级（TextButton）。 |
| 5 | 节制使用动效 | 不用渐变、发光边、阴影堆叠。差异通过**字号 / 字重 / 留白 / 颜色饱和度**表达。 |

---

## 2. 视觉系统

### 2.1 颜色（D1 ✅ 已决策：用墨绿色板）

**决策**：关闭 `dynamicColor`（在 Settings 里**不**保留切换开关，简化用户认知），使用自定义墨绿色板。

**色板定义**（实施 Agent 直接写进 `output/ui/theme/Color.kt`）：

```
// Primary 墨绿系
val Primary       = Color(0xFF2F4F4F)   // 主品牌色
val PrimaryDim    = Color(0xFF2F4F4F).copy(alpha = 0.6f)  // 次级链接 / 弱化主色
val OnPrimary     = Color(0xFFFAFAF7)

// Accent 暖橙系（仅用于"今天值得做的一件事"主按钮）
val Accent        = Color(0xFFC56A1A)
val OnAccent      = Color(0xFFFFFFFF)

// Surface 暖白系
val Surface       = Color(0xFFFAFAF7)   // 主背景，略带暖意
val SurfaceDim    = Color(0xFFF2F2EE)   // L2 容器（折叠区、检入框）
val SurfaceBright = Color(0xFFFFFFFF)   // L3 焦点容器（建议卡）背景
val Outline       = Color(0xFFE2E2DC)   // 1dp 分隔线
val OutlineSoft   = Color(0xFFEDEDE7)   // 极弱分隔

// Text
val TextPrimary   = Color(0xFF1A1A1A)
val TextSecondary = Color(0xFF6A6A66)   // 带一点暖灰，不死灰
val TextTertiary  = Color(0xFF999996)   // Mini 统计、时间戳

// Semantic
val Success       = Color(0xFF4A6B3A)   // 已接受 / 已配置
val Warning       = Color(0xFFB5651D)   // 未授权 / 需手动确认（不用红色）
val Error         = Color(0xFF8B3A2E)   // 真错误才用
```

**Theme.kt 改动**：
```kotlin
@Composable
fun KhupTheme(
    darkTheme: Boolean = false,           // D2 ✅ 不做深色模式，永远 false
    dynamicColor: Boolean = false,        // D1 ✅ 关闭 dynamic
    content: @Composable () -> Unit,
) {
    val colorScheme = lightColorScheme(
        primary = Primary,
        onPrimary = OnPrimary,
        primaryContainer = PrimaryDim,
        secondary = Accent,
        onSecondary = OnAccent,
        surface = Surface,
        surfaceVariant = SurfaceDim,
        background = Surface,
        onSurface = TextPrimary,
        onSurfaceVariant = TextSecondary,
        outline = Outline,
        outlineVariant = OutlineSoft,
        error = Error,
    )
    MaterialTheme(
        colorScheme = colorScheme,
        typography = KhupTypography,
        content = content,
    )
}
```

**实施 Agent 注意**：`darkTheme` 参数保留以备将来扩展，但 KhupTheme 内永远走 lightColorScheme。不要删 `isSystemInDarkTheme()` import。

### 2.2 字体（D3 ✅ 已决策：不引入字体）

继续用 Compose / 系统默认字体（中文 Mi Sans，英文 Roboto）。但**自定义字号节奏**，不再用 `Typography()` 默认。

**实施 Agent 改 `output/ui/theme/Type.kt`**：

```kotlin
val KhupTypography = Typography(
    displayLarge   = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Light),
    displayMedium  = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Light),
    headlineLarge  = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal),
    headlineMedium = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleLarge     = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    titleMedium    = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    titleSmall     = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge      = TextStyle(fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal),
    // bodyLarge 用于「今日观察自然语言句」—— 核心阅读字号
    bodyMedium     = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodySmall      = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge     = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium    = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall     = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)
```

**使用约定**：
- `displayMedium` 仅用于首页"早上好"问候
- `bodyLarge` 仅用于首页"今日观察"自然语言句（核心阅读字号）
- `headlineMedium` 用于建议卡 actionText
- `titleLarge` 用于回顾页区块标题
- 其他场景就近选最合适的

### 2.3 间距与节奏

**当前问题**：所有 Card `padding(16.dp)`、所有 Column `spacedBy(12.dp)` → 节奏完全平均，没有"组"的概念。

**Token 定义**（新建 `output/ui/theme/Spacing.kt`）：

```kotlin
object Spacing {
    val xxs = 4.dp    // 组内（同一段落多行）
    val xs  = 8.dp    // 卡内（标题与内容）
    val sm  = 12.dp   // 紧凑列表行距
    val md  = 16.dp   // 段落间
    val lg  = 24.dp   // 卡片间 / 大块内容间
    val xl  = 40.dp   // 首页焦点区与下方次要区
    val screenPadding = 24.dp   // 横向边距（当前 16dp → 改 24dp）
}
```

**首屏边距**：横向 24dp（让阅读区窄一些，长时间阅读更舒服）

### 2.4 容器（最关键的视觉变化）

**当前问题**：所有内容都用 `Card` 包裹，elevation = 0 或 4 不统一 → "看起来都是卡片，但不知道谁重要"。

**三层容器系统**（新建 `output/ui/theme/Containers.kt`）：

```kotlin
// L1 不加容器
//   └─ 首页"今日观察"句、AI 消息正文、回顾页文字结论
//      Composable 直接用 Text，不要任何 Card / Surface 包裹

// L2 Surface 容器，仅背景色，无 elevation
@Composable
fun L2Surface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,  // SurfaceDim
    shape = RoundedCornerShape(16.dp),
    modifier = modifier,
) { Box(Modifier.padding(16.dp)) { content() } }

// L3 焦点容器，Surface + outline + 大圆角
@Composable
fun L3FocusCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = Surface(
    color = MaterialTheme.colorScheme.surface,         // SurfaceBright 在 colorScheme 里就是 surface
    shape = RoundedCornerShape(20.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = modifier,
) { Box(Modifier.padding(24.dp)) { content() } }
```

**关键约束**：
- **全局 elevation = 0**。当前 `AnomalySuggestionCard.kt:35` 的 `elevation = 4.dp` 删除，改用 L3FocusCard。
- **不允许混用** Material `Card` 和我们的 L2/L3。实施时把所有 `Card` 替换为 L2/L3 或无容器。

### 2.5 其他规范

- **圆角**：L2 用 16dp、L3 用 20dp、Button 用 12dp、OutlinedTextField 用 12dp
- **图标**：保持 `Icons.Outlined.*` 系列。当前混用 Outlined / Filled，统一为 Outlined。
- **触控目标**：最小 48dp（Android 标准）

---

## 3. 信息架构 / 底部 Tab（D4 ✅ 已决策：4 Tab → 3 Tab）

**改造前**：今日 / 历史 / AI / 设置

**改造后**：**今日 / 回顾 / 设置**

| Tab | 路由 | 包含 |
|-----|------|-----|
| 今日 | `today` | 当前首页内容 + 应用使用时间（折叠）+ 今日计划（闭环折叠条） |
| 回顾 | `review` | 当前历史 Tab 全部内容（模式 / 建议 / 趋势）+ 通知记录 |
| 设置 | `settings` | 当前设置内容，4 组重新分组（§7） |

### 3.1 AI 入口的 3 种进入方式

**AI 不做独立 Tab**，但**所有 AI 功能保留**。改为：

1. **首页建议卡的「和 AI 聊聊」主按钮** → 带当前建议上下文
2. **首页 + 回顾页右下角 FAB**（`Icons.Outlined.AutoAwesome`，56dp）→ 带今日观察上下文
3. **回顾页每条建议 / 模式 / 趋势条目的「问 AI」二级入口** → 带该条目上下文

进入后是**全屏对话**，所有原 AI 功能（多会话 / 历史 / 清空 / 模型刷新 / 自检）保留在右上角 ⋯ 溢出菜单。详见 §6。

### 3.2 导航代码改动点

实施 Agent 改 `MainScreen.kt`：

- 删除 `Tab.Ai`、`Tab.History` 两个枚举值
- 加 `Tab.Today` / `Tab.Review` / `Tab.Settings` 三个
- `ai` route 保留但**不在 BottomBar 显示**，仍可通过 `navController.navigate("ai")` 进入
- `navigateToAiWithBridge` 函数保留
- `notifications` / `daily_plan` / `app_usage` 路由保留（这些是二级页）

---

## 4. 首页 Today 重设计

### 4.1 总体结构

```
┌──────────────────────────────────────┐
│  ←  横向边距 Spacing.screenPadding   →│
│                                       │
│  早上好 / 下午好 / 晚上好             │ ← displayMedium 28sp Light
│  2026年5月12日 周二                    │ ← bodyMedium, TextSecondary
│                                       │
│  ━━━━━━━━━━━━━━━━━━━━━  Spacing.xl 40dp
│                                       │
│  〈今日观察 · 一段自然语言〉           │ ← bodyLarge 17sp
│  你今天看屏幕 2 小时 14 分。           │   L1 无容器
│  抖音占了 38 分钟。                    │
│                                       │
│  ━━━━━━━━━━━━━━━━━━━━━  Spacing.xl 40dp
│                                       │
│  ┌─────────────────────────────────┐  │
│  │  我想到一件可能对你有用的小事    │  │ ← L3FocusCard
│  │                                  │  │
│  │  22:30 把手机放客厅              │  │ ← headlineMedium 22sp
│  │                                  │  │
│  │  ━━━━━━━━━━━                    │  │ ← Outline 1dp
│  │                                  │  │
│  │  你已经连续 3 个晚上熬到         │  │ ← bodyMedium, TextSecondary
│  │  22:40 之后                      │  │
│  │                                  │  │
│  │  ┌─────────────────────────┐    │  │
│  │  │   和 AI 聊聊 →           │    │  │ ← Filled Button, Accent 色
│  │  └─────────────────────────┘    │  │
│  │      就这样做      换一条        │  │ ← TextButton, PrimaryDim
│  └─────────────────────────────────┘  │   长按建议卡 → 不适合
│                                       │
│  ━━━━━━━━━━━━━━━━━━━━━  Spacing.lg 24dp
│                                       │
│  今天 3 件事 · 完成 1 件     ＋  ⌄   │ ← L2 折叠条
│                                       │
│  ━━━━━━━━━━━━━━━━━━━━━  Spacing.lg 24dp
│                                       │
│  现在在做什么？                        │ ← L2 检入框（折叠态）
│  ┌─────────────────────────────────┐  │
│  │  ＋ 写下你现在在做什么……          │  │
│  └─────────────────────────────────┘  │
│                                       │
│  ━━━━━━━━━━━━━━━━━━━━━  Spacing.lg 24dp
│                                       │
│  今日数据 · 通知 47 条 · 屏幕 2h14m ⌄│ ← L1 折叠区
│                                       │
└──────────────────────────────────────┘
                                  [✨] ← 右下角 FAB
```

### 4.2 删除 TopAppBar

**当前**：`TopAppBar` 上挂着 "KHUP" 标题 + 3 个 IconButton（计划 / 应用使用 / 设置）

**改动**：
- **完全删除** `TopAppBar`（Scaffold 不再设置 topBar 参数）
- 标题 "KHUP" 删除
- 设置图标删除（底部 Tab 有入口）
- 应用使用时间图标删除 → 数据下沉到 §4.7 折叠数据区
- 今日计划图标删除 → 改为 §4.5 闭环折叠条

**腾出的空间**给"早上好 + 日期"做呼吸感（顶部 80dp 留白）。

### 4.3 今日观察自然语言句（D5 ✅ 已决策：后台 WorkManager 定时生成）

**当前**：`MiniObservationCard` 显示 3 行 KV（屏幕时间 / 异常次数 / 检入次数）+ 底部 2 个 TextButton。

**改动**：把 3 个数字拼成 1-2 句自然语言句，**无容器直接渲染**。

**数据流（D5 决策）**：
- 新建 `core/ai/TodayNarrationWorker.kt`（WorkManager，每 2 小时一次）
- 新建 Room 表 `today_narration`：`id, dayStartMs, narrationText, generatedAt, modelVersion`
  - **等等**：这是 schema 改动 → 算入白名单第二处。我把 §0.4 的约束 #2 更新为白名单两处。
- Worker 从 `appSessionDao` / `anomalyDao` / `eventDao` 取数据，调 `LlmEngine.generate(NarrationPrompt)`，写入 `today_narration`
- 首页 `TodayViewModel` 新增 `observeTodayNarration()` 直接读这个表
- 失败兜底：如果当天没有 `today_narration` 行，UI 显示当前 KV（保留 `formatDuration` 实现）

**Prompt 提示（给后续 AI engine 改造参考，不是本次实施）**：
```
你是 KHUP，正在为用户写"今天观察到的一段话"。
数据：
  - 今天屏幕时间 {N} 分钟
  - 抖音 {N} 分钟、小红书 {N} 分钟、微信 {N} 分钟
  - 触发了 {N} 次注意力异常
  - 用户写了 {N} 次检入
要求：
  - 1-2 句，不超过 60 个汉字
  - 不评价（不说"太多了"、"不健康"），只观察
  - 不夸张数字（"差不多 2 小时"优于"2 小时 14 分 37 秒"）
  - 第二人称"你"
  - 不出现 §8.1 术语表里禁用的词
```

### 4.4 今日建议卡（页面唯一 L3 焦点）

**当前问题**（来自审计）：
- 3 个按钮等宽，没有主次（U1：先聊 AI 才是主路径）
- 容器和其他卡视觉一致，没有焦点感
- "成本：低 · 收益：高" 暴露 prompt 字段

**改动**：

1. **容器**：唯一使用 `L3FocusCard`，让它是页面唯一焦点
2. **按钮重排**：
   ```
   主按钮（Filled, Accent 色, 全宽）：和 AI 聊聊 →
   次级链接（TextButton, PrimaryDim）：    就这样做      换一条
   ```
   - **"和 AI 聊聊"** → 新增 `TodayViewModel.discussSuggestion(id)` 方法：
     - 状态保持 `PENDING`（用户只是讨论，不是拒绝也不是接受）
     - 调 `aiContextBridge.setPending(buildAiContext(...), id)` （沿用 `confirmRejectAndChat` 已有的 context builder，但 reason 传 null）
     - 触发 `NavigationEvent.GoToAi`
   - **"就这样做"** → 触发 `acceptSuggestion(id)`，但 acceptSuggestion 内部**新增闭环写入逻辑**（详见 §4.5 D6）
   - **"换一条"** → 沿用现有 `postponeSuggestion(id)`，不变
   - **"不适合"按钮** → **从主按钮区移走**（视觉删除），改为**长按建议卡**手势触发：
     - `Modifier.combinedClickable(onLongClick = { onReject(suggestion.id) })`
     - 弹出当前 `RejectDialog` 不变（含"和 AI 聊聊"出口）
3. **删除元素**：
   - "成本：低 · 收益：高" 那一行整段删除（`PendingContent` 内的 `Spacer + Text("成本：...")`）
   - "今日异常值" 小标签（`labelSmall, color = primary`）删除
   - "为什么这条" 改为直接显示 whyText，不要前缀标签（§8.1 术语表）
4. **保留 5 个 SuggestionCardState 视觉适配**：

| State | 当前 | 改造后 |
|-------|------|--------|
| Loading | 空 Box | 保持空 |
| Empty | 显示 "今天还没有可说的回归值信号..." | 整张 L3 卡不渲染，下方计划折叠条上移补位 |
| Generating | shimmer "正在观察..." | 静态 "稍等，让我想想……" 无 shimmer |
| Pending | 当前样式 | 新样式（如上） |
| RecentAccepted | `alpha = 0.5f` 显示 | L2 容器（不是 L3）+ Success 色 ✓ 图标 + "已写入今日计划" 文字 |

**RecentAccepted 持续时间**：保留到当日 24:00（凌晨自动清，参考 `todayStartLocalMs`），不要立刻消失也不要永远占位。

### 4.5 闭环型今日计划（D6 ✅ 已决策：C 闭环型）

#### 4.5.1 设计目标

让"AI 建议接受"和"用户自定计划"形成一个数据闭环：

- 用户点建议卡的"就这样做" → 这条建议**自动写入**当天的 `DailyPlan`，建议自身状态变 `ACCEPTED`
- 用户也可以在折叠条展开后**手动添加**计划项（沿用当前 `AddPlanInline`）
- 计划项的 `isDone` 切换**不反向影响** AnomalySuggestion 状态（建议早已 ACCEPTED）

#### 4.5.2 Schema 变更（白名单）

**`DailyPlan` 表加字段**：
```kotlin
@Entity(tableName = "daily_plan", indices = [
    Index("dayStartMs"),
    Index("isDone"),
    Index("sourceSuggestionId"),  // 新增索引
])
data class DailyPlan(
    // ... 现有字段全部保留
    val sourceSuggestionId: Long? = null,  // 新增。null = 用户手动添加
)
```

**Room migration**：
```kotlin
val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE daily_plan ADD COLUMN sourceSuggestionId INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_plan_sourceSuggestionId ON daily_plan(sourceSuggestionId)")
    }
}
```
实施 Agent 自行确定 X / Y（看 `AppDatabase.kt` 当前 version 号）。

#### 4.5.3 数据流（实施 Agent 必读）

**用户点 "就这样做"**：
```
TodayScreen
  → TodayViewModel.acceptSuggestion(id)
      ├─ suggestionRepository.accept(id)
      │    └─ AnomalySuggestion.status: PENDING → ACCEPTED  (已有)
      │
      ├─ dailyPlanRepository.addFromSuggestion(suggestion)   ← 新增
      │    └─ INSERT INTO daily_plan(
      │           title = suggestion.actionText,
      │           note = suggestion.whyText,
      │           dayStartMs = todayStartLocalMs(),
      │           isDone = false,
      │           createdAt = now,
      │           sortOrder = (max sortOrder + 1) or 0,
      │           sourceSuggestionId = suggestion.id,
      │       )
      │
      └─ uiState.suggestionCardState = RecentAccepted(suggestion)
         (24h 后由 observeSuggestion 流自然变回 Empty)
```

**用户点折叠条**：
```
PlanFoldStripe.onExpandClick
  → expanded = true
  → 展开后显示当天所有 daily_plan 行（不分来源）
  → 每行用 PlanRow（沿用现有组件）
  → 末尾保留 AddPlanInline 入口
```

**用户在折叠条里 toggle 某行 done**：
```
PlanRow.onToggleDone
  → DailyPlanViewModel.toggleDone(planId)
  → 不触发建议状态变化（即使该 plan 来自某 suggestion）
```

#### 4.5.4 折叠条 UI

```kotlin
@Composable
fun PlanFoldStripe(
    todayPlans: List<DailyPlan>,
    onAddManual: () -> Unit,
    onExpandFull: () -> Unit,  // 导航到 DailyPlanScreen 二级页
) {
    val doneCount = todayPlans.count { it.isDone }
    val totalCount = todayPlans.size

    Row(
        Modifier.fillMaxWidth().clickable { onExpandFull() }
                .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when {
                totalCount == 0 -> "今天还没有计划"
                else -> "今天 ${totalCount} 件事 · 完成 ${doneCount} 件"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onAddManual) { Icon(Icons.Outlined.Add, "添加") }
        IconButton(onClick = onExpandFull) { Icon(Icons.Outlined.ExpandMore, "展开") }
    }
}
```

**折叠条永远显示**（即使 0 项）。空态文案"今天还没有计划"。

**DailyPlanScreen 二级页保留**，作为"展开看全部"的目标页。其内部 UI 不在本次改造范围内（如有时间可顺手按 §2 设计 token 微调，不强制）。

### 4.6 检入框（L2 默认折叠）

**当前**：`QuickCheckInCard` 是一张永远展开的 Card（OutlinedTextField + 计数 + 提交按钮）

**改动**：
- 默认折叠为一行 `+ 写下你现在在做什么……`
- 点击展开 OutlinedTextField + 提交按钮
- 提交后自动收起为折叠态
- 容器从 `Card` 改为 `L2Surface`
- 文案 "现在是什么处境？" → "现在在做什么？"（§8.1）
- 文案 "提交 →" → "发给 KHUP"

**ViewModel 不动**：`onCheckInTextChange` / `submitCheckIn` / `isSubmitting` 全部保留。

### 4.7 折叠数据区

放在首页最下方，默认折叠为一行：

```
今日数据 · 通知 47 条 · 屏幕 2h14m ⌄
```

点 `⌄` 展开后显示三个子入口（导航到现有二级页）：
- 应用使用时间 → `app_usage` 路由
- 通知历史 → `notifications` 路由
- 注意力异常详情 → `review` Tab 的"反复出现的模式"区块（§5）

实施 Agent **保留** `AppUsageScreen` / `NotificationsScreen` 二级页代码不变。

### 4.8 FAB

右下角 56dp 圆形 FAB，图标 `Icons.Outlined.AutoAwesome`，点击进入 AI 全屏对话。

**FAB 上下文**：进入 AI 时塞入"今日观察"作为系统消息预填（详见 §6.3）

---

## 5. 回顾 Tab 重设计（D8 ✅ 已决策：3 子 Tab → 单页时间轴）

### 5.1 总体结构

```
┌──────────────────────────────────────┐
│  回顾                                  │ ← Title L
│                                       │
│  最近  [7 天]  30 天  90 天           │ ← FilterChip
│                                       │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━
│                                       │
│  这 7 天的故事                         │ ← Title L
│  你的屏幕时间比上周多 1h12m，主要      │ ← bodyLarge, AI 生成
│  来自抖音；但你也写了 4 次检入、         │   L1 无容器
│  接受了 3 条建议。整体在变好。           │
│                                       │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━ Spacing.xl
│                                       │
│  趋势                                  │ ← titleLarge
│  〈每日屏幕时间折线图〉                 │
│  屏幕  2h14m ▲      接受了 3 / 共 6   │
│                                       │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━ Spacing.xl
│                                       │
│  反复出现的模式                        │ ← titleLarge
│  ⚠ 晚 22:00 后小红书使用增加     ?    │ ← L1 行式, 每行右边 ? 问 AI
│  ⚠ 工作日下午抖音频繁打开         ?    │
│                                       │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━ Spacing.xl
│                                       │
│  这 7 天给过的建议                      │ ← titleLarge
│  已接受 (3)                            │
│    · 22:30 把手机放客厅      5月10日   │
│    · 中午步行 15 分钟        5月09日   │
│    · ...                               │
│  换了一条 (1) ⌄                        │ ← 默认折叠
│  不适合 (2) ⌄                          │ ← 默认折叠
│                                       │
└──────────────────────────────────────┘
                                  [✨] ← FAB 同首页
```

### 5.2 关键改造

| 当前 | 改造后 |
|------|--------|
| `TabRow` 切换 3 子 Tab | **删除 TabRow**。改 LazyColumn，各区块用 `titleLarge` 标题分隔 |
| 顶部 `TopAppBar(title = "历史")` | 保留 TopAppBar 但标题改为 "回顾" |
| 趋势区 `FilterChip(7天/30天)` | 提到顶部，加一档 "90 天"，文案不变 |
| 趋势区"接受率 67%" 显眼 | 改为"接受了 3 / 共 6"，不显示百分比（§8.1） |
| 建议区按 4 状态分段全部展开 | "已接受"默认展开，"换了一条" "不适合" 默认折叠 |
| 模式区 evidence 用 `EvidenceListSheet` 弹窗 | 保留，沿用 |

### 5.3 "这 7 天的故事"（新增）

逻辑同 §4.3 今日观察，**只是周期更长**。

- 不新建 Worker，**按需生成**（用户切换周期时触发，结果缓存到 `today_narration` 表，用 `period` 字段区分）
- 或者**复用** `TodayNarrationWorker` 加一个 `weeklyNarration` task
- 实施 Agent 自行决策上述两种实现的选择，向 Kian 简单说明理由

**Empty / 失败兜底**：直接显示数据 KV（保留当前 SummaryRow 实现作为 fallback）

### 5.4 "问 AI" 行内入口

每个模式行 / 建议行的右侧加一个小 `Icons.Outlined.HelpOutline` 按钮：
```
⚠ 晚 22:00 后小红书使用增加    [?] ← 问 AI
```
点击：
- 调 `viewModel.discussFromReview(item)`（新增方法）
- 内部走 `AiContextBridge.setPending(...)` + 导航
- 上下文文本由本文 §9.2 表格定义

---

## 6. AI 全屏对话重设计（D9 ✅ 已决策：5 IconButton → 溢出菜单）

### 6.1 总体结构

```
┌──────────────────────────────────────┐
│  ← 返回    今天怎么样？          ⋯    │ ← TopAppBar
│                                       │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━
│                                       │
│  ┌─────────────────────────────┐    │
│  │  我把今天的情况整理给你了：    │    │ ← 进入对话时自动塞的
│  │  屏幕 2h14m / 抖音 38m       │    │   "系统消息"，L2 容器
│  │  / 检入 1 次                  │    │   用户可点击展开收起
│  └─────────────────────────────┘    │
│                                       │
│                       ┌──────────┐    │
│                       │ 为什么建议│   │ ← 用户消息，右对齐
│                       │ 我 22:30 │   │   80% 宽，L2 PrimaryDim
│                       │ 放手机？  │   │
│                       └──────────┘    │
│                                       │
│  这周你已经连续三晚熬到 22:40         │ ← AI 消息，左对齐
│  之后……                              │   L1 无容器纯文字
│                                       │
│                                       │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━
│                                       │
│  ┌───────────────────────────┐  ⊕   │ ← 输入框 + 发送
│  │  问点什么……                │       │
│  └───────────────────────────┘       │
└──────────────────────────────────────┘
```

### 6.2 顶部栏

**当前**：`Row` 内 5 个 IconButton（Add / History / ClearAll / Refresh / Science）+ 标题 + 状态副标题

**改动**：
- 用 `TopAppBar` 替代当前 `Row`
- `navigationIcon` = ← 返回（如果是从首页/回顾进入 → popBackStack；如果是从底部直接进入 → 无返回，无 Tab，已经是顶层）
  - 这里出现一个矛盾：AI 不是 Tab 了，那"返回"返回到哪里？
  - **决策**：AI 全屏页用 `composable("ai")` 注册，进入即压栈。返回 = `popBackStack` 回到调用方（首页或回顾页或拒绝建议的 dialog）。
- `title` = 当前会话的 `currentTitle`（沿用）
- **删除状态副标题**那一整段（"本地模型:就绪 · API 通道:已配置"），模型 / API 状态用户在设置页 AI 通道卡里看
- `actions` = 单一 IconButton `⋯`（`Icons.Outlined.MoreVert`）
- ⋯ 点击展开 `DropdownMenu`：
  - 新建对话 → `viewModel.newSession()`
  - 历史会话 → 当前 `showHistory = true` 弹底部 sheet
  - 清空当前会话 → `viewModel.clearCurrentSession()`
  - 刷新模型状态 → `viewModel.refreshModelState()`
  - **运行自检** → `viewModel.runSmokeTest()`，但 **仅当 `BuildConfig.DEBUG` 为 true 时显示该项**

### 6.3 空状态（带上下文进入 vs 无上下文进入）

**当前**："先试一句简单问题。API 配置和模型路径在 Settings 页里调整。"

**改动 — 三种入口分别处理**：

| 入口 | 行为 |
|------|------|
| 从首页"和 AI 聊聊" / 拒绝→聊聊 | 自动发送预填消息（AiContextBridge，已有）。空状态不出现 |
| 从回顾页"问 AI" | 自动发送 §9.2 表格定义的预填消息 |
| 从 FAB 进入（无具体建议） | 显示话题入口 + 3 个推荐起点 |

FAB 入口的空状态 UI：
```
┌──────────────────────────────────────┐
│                                       │
│         今天怎么样？                   │ ← headlineLarge
│                                       │
│  ▸ 帮我看看今天的情况                 │ ← L2 行式，可点击
│  ▸ 我现在有点烦/累                    │
│  ▸ 我想聊聊昨天                       │
│                                       │
└──────────────────────────────────────┘
```
点击任一行 = 把那一行作为用户首句自动发送。

实施 Agent：3 个推荐起点的文案以本表为准，不要改写。

### 6.4 消息气泡

**当前**：每条消息一张全宽 `Card`，标签 "你" / "AI" + 内容

**改动**：
- 用户消息：右对齐，最大 80% 宽（用 `Modifier.widthIn(max = ...)` + 父 Row 的 `Arrangement.End`），L2 容器但底色用 `PrimaryDim` 而非 `SurfaceDim`，前景文字 White
- AI 消息：左对齐，最大 90% 宽，**L1 无容器**，纯文字
- **删除** "你" / "AI" 标签 Text
- AI 名字如果要显示，用 "KHUP"，不用 "AI"

### 6.5 生成中状态

**当前**：底部一行 "生成中..." + `LinearProgressIndicator`

**改动**：
- 删除底部 LinearProgressIndicator
- 在 AI 气泡位置临时显示一个 3 点呼吸动画（`••• alpha 0.3 → 1 → 0.3`，`animateFloatAsState` 实现，不引入新库）
- 响应快了之后这个状态短暂出现就消失

### 6.6 顶部"今日上下文"系统消息卡

进入 AI 对话时，如果有上下文（建议 / 模式 / 今日观察），第一条消息位置渲染一个 L2 容器卡：
```
┌─────────────────────────────────┐
│  我把今天的情况整理给你了：       │
│  屏幕 2h14m / 抖音 38m / 检入 1   │
│  ⌄ 展开看全部                    │
└─────────────────────────────────┘
```
- 不进 ChatMessage 列表
- 默认折叠
- 视觉上和气泡气场区分（PrimaryDim 边框 1dp，无填充）

---

## 7. 设置页重设计（D10 ✅ 已决策：9 卡 → 4 组列表）

### 7.1 4 组分组

```
┌──────────────────────────────────────┐
│  设置                                  │
│                                       │
│  让 KHUP 工作                         │ ← Group 1 title
│  ─────────                            │
│  · 读取通知            ✓ 已开           │
│  · 应用使用统计        ✓ 已开           │
│  · 悬浮窗（拦截）      ⚠ 未开           │
│  · 系统通知            ✓ 已开           │
│  · MIUI 自启动         ✓ 已确认         │
│  · MIUI 省电策略       ⚠ 需手动确认     │
│                                       │
│  AI 设置                              │ ← Group 2 title
│  ─────────                            │
│  · 调用模式            本地优先     →  │
│  · API 配置            ✓ 已配置     →  │
│  · 本地模型            ✓ 已就绪     →  │
│                                       │
│  干预阈值                              │ ← Group 3 title
│  ─────────                            │
│  · 抖音超时提醒        30 分钟     ─ + │
│  · 小红书超时提醒      40 分钟     ─ + │
│                                       │
│  数据与隐私                            │ ← Group 4 title
│  ─────────                            │
│  · 导出全部数据                    →  │
│  · 清空历史数据                    →  │
│  · 数据保留策略                    →  │
│  · 隐私说明                        →  │
│                                       │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━
│  KHUP v0.1.0                          │
│  你的数据从不离开这台手机              │
└──────────────────────────────────────┘
```

### 7.2 权限项的折叠规则

- **未授权** 的项：展开显示完整描述 + "去开启" 按钮
- **已授权** 的项：折叠为一行 标题 + ✓
- 点击已授权的行不展开（避免误操作）；想再次进入权限页 → 长按

### 7.3 AI 设置分子页

当前 `AiChannelCard` 是一张大卡所有字段平铺。改造：

- 主设置页只显示 3 行：调用模式 / API 配置 / 本地模型
- 每行点击进入子页 `composable("settings/ai_call_mode")` / `settings/ai_api` / `settings/ai_local_model`
- 子页 UI 沿用当前 OutlinedTextField + 按钮，无需大改

### 7.4 干预阈值

`InterventionSettingsCard` 内部 `ThresholdRow` 沿用，**不放进子页**（用户经常调，留在主设置页）。

### 7.5 数据与隐私

- "导出全部数据" / "清空历史数据" / "数据保留策略" / "隐私说明" 全部成为可点击行
- "清空" 行的二次确认 dialog 沿用当前实现

---

## 8. 文案规范

### 8.1 术语翻译表（强制全局替换）

| 当前 UI 词 | 替换为 | 检索文件位置举例 |
|-----------|--------|-----------------|
| 异常值 / 回归值 / 异常值建议 | **建议** / **我想到一件事** | `AnomalySuggestionCard.kt:40` "今日异常值" |
| 注意力异常 | **状态变化** / **反复出现的模式** | `MiniObservationCard.kt:37` "注意力异常" |
| 检入 / Check-In | **写一段** / **现在在做什么** | `QuickCheckInCard.kt:36` |
| 接受率 / 总反馈 | **接受了 N / 共 N** | `TrendsTab.kt:41-48` |
| Pending / Accepted / Postponed / Rejected | 待处理 / 已接受 / 换了一条 / 不适合 | `SuggestionsTab.kt:20-25`（已部分做） |
| 模型路径 / API 通道 / Provider Mode | 仅在 Settings 子页出现 | `AiChatScreen.kt:91-101` subtitle 整段删除 |
| 正在观察... | **稍等，让我想想……** | `GeneratingStateBlock.kt:28` |
| 提交 → | **发给 KHUP** | `QuickCheckInCard.kt:67` |
| 你 / AI（消息发送者标签） | （删除标签，靠位置区分） | `AiChatScreen.kt:340` |
| 处境 | **现在在做什么** | `TodayScreen.kt:148` "比如：刚开完会..." |
| 现在是什么处境？ | **现在在做什么？** | `QuickCheckInCard.kt:36` |
| KHUP（标题） | （首页删除标题） | `TodayScreen.kt:68` |

### 8.2 文案语气原则

1. **第二人称对话**：用 "你"，不用 "用户" 或省略主语。AI 自称 "KHUP" 或省略。
2. **不夸张**：避免 "🎉 太棒了"、"加油！"、"做得很好" —— 廉价激励让人累。
3. **观察但不评判**：「你抖音用了 38 分钟」(可) / 「你抖音用得太多了」(不可)
4. **不卖弄精确**：「差不多 2 小时」优于「2 小时 14 分 37 秒」（首页阅读句场景）。具体数字仍可在折叠数据区显示。
5. **空态友好**：所有空态文案要让用户**知道下一步做什么**而不是只显示"暂无数据"。

### 8.3 关键文案样板（实施 Agent 直接用）

| 场景 | 文案 |
|------|------|
| 首页问候（早 5-11 点） | 早上好 |
| 首页问候（11-17 点） | 下午好 |
| 首页问候（17-23 点） | 晚上好 |
| 首页问候（23-5 点） | 还醒着？ |
| 今日观察 Empty | 今天还没攒够数据。写一段你现在在做什么，我来帮你看看。 |
| 建议卡 Empty | （整张卡不渲染，无文案） |
| 建议卡 Generating | 稍等，让我想想…… |
| 建议卡 RecentAccepted 副标题 | ✓ 已写入今日计划 · {hh:mm} |
| 计划折叠条 Empty | 今天还没有计划 |
| 计划折叠条非空 | 今天 {N} 件事 · 完成 {M} 件 |
| 检入折叠态 | ＋ 写下你现在在做什么…… |
| 检入展开后 placeholder | 比如：刚开完会，平时回宿舍刷手机 |
| AI 空状态（FAB 入口） | 今天怎么样？ |
| AI 推荐起点 1 | 帮我看看今天的情况 |
| AI 推荐起点 2 | 我现在有点烦/累 |
| AI 推荐起点 3 | 我想聊聊昨天 |
| 设置页底部 | 你的数据从不离开这台手机 |

---

## 9. 跨页面元素

### 9.1 建议卡的多种出场场景

建议卡是 KHUP 最重要的可复用 UI 元素。实施 Agent 抽出一个共用 Composable：

```kotlin
@Composable
fun SuggestionContent(
    suggestion: AnomalySuggestion,
    density: SuggestionDensity,   // Hero | Standard | Compact
    onPrimaryAction: (Long) -> Unit = {},
    onSecondaryActions: SuggestionSecondaryActions = empty(),
)

enum class SuggestionDensity { Hero, Standard, Compact }
```

| 场景 | density | 容器 | 按钮 |
|------|---------|------|------|
| 首页 Pending | `Hero` | L3FocusCard | 主：和 AI 聊聊 / 次：就这样做 + 换一条 |
| 首页 RecentAccepted | `Standard` | L2Surface | 无按钮，仅 ✓ + 时间戳 |
| 回顾页（已归档） | `Compact` | L1 行式 | 无按钮，整行可点击 |

业务逻辑层（ViewModel）**完全不动**，只是把当前 `AnomalySuggestionCard.kt` 拆出一个统一渲染器，让回顾页和首页共用同一份组件代码。

### 9.2 AI 上下文桥 —— 进入 AI 时的预填消息表

`AiContextBridge.setPending(text, anchorId)` 已有。本次改造扩展使用面：

| 来源 | 预填消息文本（实施 Agent 直接用此模版） |
|------|-------------------------------------|
| 首页"和 AI 聊聊"主按钮 | 「我想聊聊这条建议为什么给我。\n\n建议：{suggestion.actionText}\n\n你说是因为：{suggestion.whyText}」 |
| 拒绝 → "和 AI 聊聊"（已有） | 沿用 `TodayViewModel.buildAiContext`，不动 |
| 首页 FAB（无具体建议） | 「{today_narration.narrationText}\n\n帮我看看？」 |
| 回顾页"问 AI"在模式条目上 | 「我注意到 KHUP 提到这个模式：{anomaly.title}。\n这意味着什么？我应该怎么看？」 |
| 回顾页"问 AI"在建议条目上 | 「能再讲讲这条建议吗？\n\n建议：{suggestion.actionText}\n\n你说是因为：{suggestion.whyText}\n\n我当时{status_zh}了。」 |
| 回顾页"问 AI"在趋势数据上 | 「这周相比上周，{narration_diff}。能解读一下吗？」 |

---

## 10. 决策清单（全部锁定 ✅）

| # | 决策项 | 结果 | 章节 |
|---|--------|------|------|
| D1 | dynamicColor 默认值 + 色板 | ✅ 关闭 dynamicColor，用墨绿色板 | §2.1 |
| D2 | 深色模式 | ✅ 不做 | §2.1 |
| D3 | 引入第三方字体 | ✅ 不引入，用系统字体 | §2.2 |
| D4 | 底部 Tab 数量 | ✅ 4 → 3（删独立 AI Tab，AI 改为 FAB + 上下文入口） | §3 |
| D5 | 今日观察句生成时机 | ✅ 后台 WorkManager，每 2 小时一次，结果存数据库 | §4.3 |
| D6 | 今日计划在首页 | ✅ **闭环型**：接受建议自动写入计划，计划默认折叠为一行 | §4.5 |
| D7 | "不适合"按钮主按钮区位置 | ✅ 从主按钮区移走，改为长按建议卡触发 | §4.4 |
| D8 | 历史页结构 | ✅ 3 子 Tab → 单页时间轴，保留所有数据维度 | §5 |
| D9 | AI 顶部 5 IconButton | ✅ 全部收进右上角 ⋯ 溢出菜单，自检按钮仅 DEBUG 显示 | §6.2 |
| D10 | 设置页结构 | ✅ 9 卡 → 4 组列表，AI 设置拆 3 子页 | §7 |

---

## 11. 实施 Spec（给实施 Agent 的可执行清单）

每个 spec 一次 commit，commit message 前缀用 spec 编号。Phase N 全部完成后由 Kian 验收，过了再进 Phase N+1。

### Phase 1 — 视觉地基（仅 theme/ 目录，不动业务）

#### P1.1 — 重做 Color.kt
- **改文件**：`output/ui/theme/Color.kt`
- **改动**：删除所有现有色变量，按 §2.1 替换为新色板。
- **验收**：编译通过，能看到色板被引用的地方颜色变化（但此时 KhupTheme 还没切换，所以全局 UI 还没变）。
- **commit**: `P1.1: 重做 Color.kt 为墨绿色板`

#### P1.2 — 重做 Type.kt
- **改文件**：`output/ui/theme/Type.kt`
- **改动**：按 §2.2 定义完整 `KhupTypography`。
- **验收**：编译通过。
- **commit**: `P1.2: 自定义 KhupTypography 字号节奏`

#### P1.3 — 新建 Spacing.kt
- **新文件**：`output/ui/theme/Spacing.kt`
- **改动**：按 §2.3 定义 `Spacing` object。
- **验收**：编译通过。
- **commit**: `P1.3: 新增 Spacing 设计 token`

#### P1.4 — 新建容器组件
- **新文件**：`output/ui/theme/Containers.kt`
- **改动**：按 §2.4 定义 `L2Surface` / `L3FocusCard` 两个 Composable。
- **验收**：编译通过，可在 `@Preview` 看到样式。
- **commit**: `P1.4: 新增 L2/L3 容器组件`

#### P1.5 — KhupTheme 关闭 dynamic / dark
- **改文件**：`output/ui/theme/Theme.kt`
- **改动**：按 §2.1 末尾代码模版改 `KhupTheme`。`dynamicColor` 默认 false、`darkTheme` 默认 false。
- **验收**：跑一次 App，所有页面应该看起来"色调统一为墨绿"，但内容布局未变。
- **commit**: `P1.5: KhupTheme 切换到固定 light + 墨绿色板`

#### P1.6 — 全局文案术语替换
- **改文件**：grep §8.1 表中所有 "当前 UI 词" 出现的位置，按表替换。
- **特别注意**：不要遗漏 `EmptyStateBlock.kt:14` 的"今天还没有可说的回归值信号"。
- **验收**：grep 当前词后无匹配。
- **commit**: `P1.6: 全局替换 UI 术语为 §8.1 词表`

**Phase 1 总验收**：跑 App，所有页面色板和文字看起来"是另一个 App 了"，但功能和布局结构未动。

---

### Phase 2 — 首页重构

#### P2.1 — 删除首页 TopAppBar
- **改文件**：`output/ui/today/TodayScreen.kt`
- **改动**：Scaffold 不再设 topBar。删除 IconButton（计划 / 应用使用 / 设置）。删除 `onNavigateToSettings` / `onNavigateToDailyPlan` / `onNavigateToAppUsage` 这三个 callback **参数**？**保留参数**，但首页不再调用（备用，因为 §4.5 折叠条会调 `onNavigateToDailyPlan`，§4.7 折叠数据区会调 `onNavigateToAppUsage` / `onNavigateToNotifications`）。
- **加内容**：顶部 Column 加问候（按 §8.3 文案样板根据时间渲染）+ 日期。用 `displayMedium` + `bodyMedium`。
- **验收**：首页顶部干净，只有问候和日期。
- **commit**: `P2.1: 首页删除 TopAppBar，加问候+日期顶部锚点`

#### P2.2 — MiniObservationCard 改造为自然语言句
- **新文件**：`core/data/db/entities/TodayNarration.kt`、`core/data/db/TodayNarrationDao.kt`、Room version+1 + Migration
- **新文件**：`core/ai/TodayNarrationWorker.kt`（WorkManager，每 2 小时一次）
- **改文件**：`common/work/WorkScheduler.kt` 注册新 Worker
- **改文件**：`output/ui/today/TodayViewModel.kt` 加 `observeTodayNarration()`
- **改文件**：`output/ui/today/components/MiniObservationCard.kt` 改造为 L1 渲染 narrationText；如果 null 则回退到当前 KV 渲染（保留 `formatDuration`）
- **验收**：首页中部出现一段自然语言句（或在数据库空时回退 KV）。Worker 跑一次能写入 today_narration 表。
- **commit**: `P2.2: 今日观察改为自然语言句 + TodayNarrationWorker`

#### P2.3 — 建议卡按钮重排 + acceptSuggestion 写入计划闭环
- **Schema**：`DailyPlan` 加 `sourceSuggestionId`，Migration 见 §4.5.2
- **改文件**：`core/data/repository/DailyPlanRepository.kt` 加 `addFromSuggestion(suggestion: AnomalySuggestion)` 方法
- **改文件**：`output/ui/today/TodayViewModel.kt`：
  - `acceptSuggestion(id)` 内调用 `dailyPlanRepository.addFromSuggestion(suggestion)`
  - 新增 `discussSuggestion(id)` 方法（不改 status，仅推 AiContextBridge + 触发 NavigationEvent.GoToAi）
- **改文件**：`output/ui/today/components/AnomalySuggestionCard.kt`：
  - PendingContent 的按钮 Row 重排：1 个 Filled "和 AI 聊聊"（Accent 色）+ 2 个 TextButton（就这样做 / 换一条）
  - 删除 "成本：低 · 收益：高" 行
  - 删除"今日异常值"小标签（已在 P1.6 替换，但卡片标题位置也要确认是否还在）
  - 删除 "为什么这条" 前缀，直接显示 whyText
  - 整卡用 L3FocusCard 替代 Card
  - `Modifier.combinedClickable(onLongClick = { onReject(suggestion.id) })` 加长按手势
  - RecentAccepted 态用 L2Surface + ✓ 图标 + "已写入今日计划 · hh:mm"
- **验收**：点"就这样做"建议变 RecentAccepted 态，下方计划条数 +1；点"和 AI 聊聊"进入 AI 全屏带上下文预填；长按建议卡弹 RejectDialog。
- **commit**: `P2.3: 建议卡按钮重排 + acceptSuggestion 写入今日计划`

#### P2.4 — 计划折叠条
- **新文件**：`output/ui/today/components/PlanFoldStripe.kt`（按 §4.5.4 实现）
- **改文件**：`output/ui/today/TodayViewModel.kt` 加 `observeTodayPlans()` 暴露 `Flow<List<DailyPlan>>`
- **改文件**：`output/ui/today/TodayScreen.kt` 在建议卡下方加 PlanFoldStripe；点击展开调 `onNavigateToDailyPlan`
- **验收**：首页计划条永远显示，能反映 P2.3 写入的项。点击进入 DailyPlanScreen 二级页。
- **commit**: `P2.4: 首页今日计划折叠条`

#### P2.5 — 检入框默认折叠
- **改文件**：`output/ui/today/components/QuickCheckInCard.kt`
- **改动**：加 `expanded` 状态。默认折叠为一行（按 §8.3 文案）；点击展开 OutlinedTextField + 提交按钮；提交后自动收起。容器从 Card 改 L2Surface。
- **验收**：首页检入框默认占一行，点击展开能写字提交。
- **commit**: `P2.5: 检入框默认折叠`

#### P2.6 — 折叠数据区
- **新文件**：`output/ui/today/components/TodayDataFold.kt`
- **改动**：默认一行 `今日数据 · 通知 N 条 · 屏幕 XhYm ⌄`；展开显示 3 个导航行（应用使用 / 通知历史 / 异常详情）
- **验收**：首页最下方出现折叠数据条，能展开导航到二级页。
- **commit**: `P2.6: 首页底部折叠数据区`

#### P2.7 — FAB
- **改文件**：`output/ui/today/TodayScreen.kt` 加 floatingActionButton
- **改动**：Scaffold 的 `floatingActionButton = { FloatingActionButton(...) }`，图标 AutoAwesome，点击进入 AI 全屏并预填今日 narration（沿用 AiContextBridge）
- **验收**：右下角 56dp FAB，点击进入 AI 对话页。
- **commit**: `P2.7: 首页 FAB → AI 对话`

**Phase 2 总验收**：首页结构=问候+日期 / 今日观察句 / 建议卡（L3 焦点）/ 计划折叠条 / 检入折叠 / 数据折叠 + FAB。无 TopAppBar。

---

### Phase 3 — 信息架构调整

#### P3.1 — Tab 从 4 改 3
- **改文件**：`output/ui/MainScreen.kt`
- **改动**：删除 `Tab.Ai`、删除 `Tab.History`，加 `Tab.Today` / `Tab.Review` / `Tab.Settings`。`composable("ai")` 路由保留但不在 BottomBar 显示。`composable("history")` 改名 `composable("review")` 或保留 history 作为 review 的旧别名（路由保持 review）。
- **保留**：`navigateToAiWithBridge` 函数
- **验收**：底部 3 个 Tab，AI 不在 Tab 上但仍可从首页 FAB / 建议卡进入。
- **commit**: `P3.1: 底部 Tab 4 → 3`

#### P3.2 — AI 全屏页顶部栏重构
- **改文件**：`output/ui/ai/AiChatScreen.kt`
- **改动**：
  - 删除当前顶部 Row 内的 5 IconButton + 状态副标题
  - 替换为 `TopAppBar(title = uiState.currentTitle, navigationIcon = 返回, actions = ⋯)`
  - ⋯ 展开 DropdownMenu，5 项按 §6.2 实现
  - 自检项加 `if (BuildConfig.DEBUG)` 守卫
- **验收**：AI 页顶部干净，只有标题和 ⋯，功能未丢。
- **commit**: `P3.2: AI 顶部 5 IconButton → ⋯ 溢出菜单`

#### P3.3 — AI 空状态 / 推荐起点
- **改文件**：`output/ui/ai/AiChatScreen.kt` 替换 `EmptyChatCard`
- **改动**：判断当前是否有 AiContextBridge pending：
  - 有 → 不显示空状态卡（让现有自动发送逻辑接管）
  - 无 → 显示 "今天怎么样？" + 3 个推荐起点（按 §8.3）
- **验收**：FAB 进入时看到话题入口；建议卡进入时直接看到自动发的消息。
- **commit**: `P3.3: AI 空状态加话题入口`

#### P3.4 — AI 消息气泡重做
- **改文件**：`output/ui/ai/AiChatScreen.kt` 改 `ChatMessageCard`
- **改动**：用户消息右对齐 L2 + PrimaryDim 底；AI 消息左对齐 L1 无容器；删除 "你"/"AI" 标签
- **验收**：对话界面像 iMessage 而不是 IRC 日志。
- **commit**: `P3.4: AI 消息气泡左右对齐 + 去标签`

#### P3.5 — AI 生成中 3 点呼吸动画
- **改文件**：`output/ui/ai/AiChatScreen.kt`
- **改动**：删除底部 `LinearProgressIndicator`；在 AI 气泡位置渲染 `••• alpha 循环`（`animateFloatAsState`）
- **验收**：生成时气泡里有 3 点呼吸，无底部进度条。
- **commit**: `P3.5: AI 生成中状态改 3 点呼吸`

#### P3.6 — AI 上下文桥扩展
- **改文件**：`core/ai/AiContextBridge.kt` 不动签名
- **改文件**：`output/ui/today/TodayViewModel.discussSuggestion` 已在 P2.3；本步在 `output/ui/today/TodayScreen.kt` 加 FAB 上下文桥（带 narrationText）
- **新建**：`output/ui/history/HistoryViewModel.discussFromReview` （为 P4.3 准备）
- **验收**：FAB 进入 AI 自动带今日 narration 上下文。
- **commit**: `P3.6: AiContextBridge 多入口扩展`

**Phase 3 总验收**：3 个 Tab、AI 全屏页清爽、所有 AI 功能 100% 可达（验证用：点 5 个原 IconButton 对应功能）。

---

### Phase 4 — 回顾页重构

#### P4.1 — 删 TabRow 改单页时间轴
- **改文件**：`output/ui/history/HistoryScreen.kt`
- **改动**：删除 TabRow + selectedTab。LazyColumn 顺序渲染：故事段 / 趋势 / 模式 / 建议
- **改文件**：`output/ui/history/tabs/PatternsTab.kt` / `TrendsTab.kt` / `SuggestionsTab.kt` 改造为 Composable 函数（不是 Tab），可在 LazyColumn 里被调用
- **验收**：回顾页一个长滚页面，所有数据类型可见
- **commit**: `P4.1: 回顾页 3 子 Tab → 单页时间轴`

#### P4.2 — "这 X 天的故事"
- **改 schema**：`today_narration` 表加 `periodDays: Int`（0 = 当日 / 7 / 30 / 90）。Migration 简单 ADD COLUMN
- **改 Worker** 或新建 `WeeklyNarrationWorker`：实施 Agent 决策，向 Kian 说明选哪个
- **改文件**：`output/ui/history/HistoryScreen.kt` 顶部加叙述段
- **验收**：回顾页顶部看到一段 AI 写的 7 天总结
- **commit**: `P4.2: 回顾页"这 X 天的故事"段`

#### P4.3 — "问 AI" 行内入口
- **改文件**：`output/ui/history/components/PatternRow.kt` / `SuggestionRow.kt`
- **改动**：行右侧加 `IconButton(Icons.Outlined.HelpOutline)`，点击调 `viewModel.discussFromReview(item)` → 推 AiContextBridge → 导航
- **改文件**：`output/ui/history/HistoryViewModel.kt` 加 `discussFromReview` 方法
- **验收**：每条模式 / 建议右侧有 ? 按钮，点击带上下文进 AI
- **commit**: `P4.3: 回顾页每条加问 AI 入口`

#### P4.4 — 趋势数字降级
- **改文件**：`output/ui/history/tabs/TrendsTab.kt`
- **改动**：删除 SummaryRow 的 value 用 primary 色；改用 bodyMedium TextSecondary。"接受率" SummaryRow 整行删除（§8.1 不显示百分比），保留"接受次数 / 总反馈" 但文案改"接受了 N / 共 N"。FilterChip 加 90 天档。
- **验收**：趋势区数字不抢戏，"接受率"消失
- **commit**: `P4.4: 趋势区数字降级 + 加 90 天周期`

#### P4.5 — 建议分段折叠
- **改文件**：`output/ui/history/tabs/SuggestionsTab.kt`
- **改动**：4 个状态分段，"已接受" 默认展开，"换了一条" / "不适合" / "待处理" 默认折叠为标题行 + 计数 + ⌄
- **验收**：默认能看到接受过的建议，其他状态折叠
- **commit**: `P4.5: 建议分段默认折叠次要状态`

#### P4.6 — 回顾页 FAB
- **改文件**：`output/ui/history/HistoryScreen.kt`
- **改动**：Scaffold 加 floatingActionButton 同首页
- **验收**：回顾页右下角也有 FAB
- **commit**: `P4.6: 回顾页 FAB → AI 对话`

**Phase 4 总验收**：回顾页一页到底，4 个数据维度可达，每条都能问 AI。

---

### Phase 5 — 设置页分组重做

#### P5.1 — 4 组分组 + 列表化
- **改文件**：`output/ui/settings/SettingsScreen.kt`
- **改动**：当前 Column 顺序拆 4 组，每组上方一个 `titleLarge` 标题。每张 PermissionCard 改造为列表行：未授权时展开完整描述；已授权时折叠为一行。
- **验收**：设置页分 4 块，已授权项一行显示
- **commit**: `P5.1: 设置页 4 组分组 + 权限项折叠`

#### P5.2 — AI 设置拆子页
- **新文件**：`output/ui/settings/AiCallModeScreen.kt` / `AiApiScreen.kt` / `AiLocalModelScreen.kt`
- **改 Nav**：`MainScreen.kt` 加 3 个新 route
- **改文件**：`SettingsScreen.kt` AiChannelCard 改为 3 行 SettingsRow（指向 3 个子页）
- **验收**：AI 设置主页只 3 行，每行进入子页配置
- **commit**: `P5.2: AI 设置拆 3 子页`

#### P5.3 — 数据隐私行式化
- **改文件**：`SettingsScreen.kt` 的 `DataCard` / `PrivacyCard`
- **改动**：合并为 "数据与隐私" 一个组，4 行 SettingsRow（导出 / 清空 / 保留策略 / 隐私说明）。"清空"行的二次确认 dialog 不动。
- **验收**：设置页底部一组 4 行
- **commit**: `P5.3: 数据与隐私合组`

**Phase 5 总验收**：设置页一目了然，4 组结构清晰，9 张原始卡所有数据/逻辑保留。

---

## 12. 不在本次改造范围

- 加新业务功能（专注重组现有功能）
- 重构 ViewModel 状态机或抽新架构层
- 改 LLM prompt 内容（除非 §4.3 / §5.3 新增的 narration 生成任务需要新 prompt）
- 引入新的 Gradle 依赖
- 多语言（暂时只做中文）
- 平板适配
- 横屏适配
- A11y 深度适配（保持当前 contentDescription，不做额外的 talkback 路径优化）

---

## 13. 文档维护

- 本文档是 UI / 产品决策的单一信息源，所有讨论更新到这份文件，不开新文档
- 每个 Phase / spec 完成后，实施 Agent 在对应章节加 `✅ 完成时间` 标记
- 当所有 Phase 1-5 完成，本文进入"已落地"状态，归档到 `docs/archive/` 但不删除
- 后续如出现新的 UI 改造需求，新建 `docs/ui-redesign-v2.md`，不修改本文

---

## 14. 实施 Agent 验收 checklist（每个 spec 用）

实施 Agent 完成每个 spec 后，在交付物里附上以下 checklist 自查：

- [ ] 编译通过：`./gradlew :app:compileDebugKotlin` 无 error
- [ ] 仅改本 spec 列出的文件（如有额外文件改动，说明原因）
- [ ] 未删除任何 ViewModel / Repository / DAO 方法（仅 UI 层删除）
- [ ] 未引入新依赖
- [ ] §8.1 术语表词在本 spec 改动的代码中无遗漏
- [ ] commit message 用 spec 编号开头
- [ ] 如涉及 schema 改动，Migration 已写规范类（非 destructive）
- [ ] 附 `@Preview` 截图（如涉及 Composable UI 变化）

—— 文档完 ——
