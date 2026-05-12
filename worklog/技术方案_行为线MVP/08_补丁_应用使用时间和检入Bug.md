# Module 8 — 补丁：应用使用时间入口 + 检入无结果 Bug

---

## Part A：检入无结果 Bug

### A.1 症状

用户提交"当前处境" → AI 转了一圈 → 没有建议出现。AI 聊天界面正常。

### A.2 根因分析

根据 Module 3 的实现结构，触发链路是：

```
BehaviorReportRepository.submit()
  → applicationScope.launch {
      patternGenerator.analyzeUserReport(eventId) → 返回 null（JSON 解析失败）
      ↓
      return@launch   ← 直接退出，没有走 fallback
    }
```

LLM 通道本身工作正常（AI 聊天能用），但 **结构化 JSON 输出解析失败** 时返回 null，而 `return@launch` 让整条链路静默退出，用户看到的是永远停在"正在观察..."或切回空态。

次要可能原因（按概率排）：
1. JSON 解析失败 → null → 无 fallback（**最可能**）
2. `AnomalySuggestionGenerator` 24h 冷却误触发（同日提交过相似处境）
3. `applicationScope` 没有用 `SupervisorJob`，上游异常把协程取消了

### A.3 修复：保证 fallback 建议一定产生

**修改文件**：`core/data/repository/BehaviorReportRepositoryImpl.kt`

**改动前（问题代码）**：

```kotlin
applicationScope.launch {
    try {
        val patternId = patternGenerator.analyzeUserReport(eventId) ?: return@launch
        suggestionGenerator.generateForPattern(patternId)
    } catch (t: Throwable) {
        Log.w("BehaviorReport", "generation chain failed: ${t.javaClass.simpleName}")
        // ← 这里也没有创建 fallback！
    }
}
```

**改动后（修复代码）**：

```kotlin
applicationScope.launch {
    var suggestionCreated = false
    try {
        val patternId = patternGenerator.analyzeUserReport(eventId)
        if (patternId != null) {
            val id = suggestionGenerator.generateForPattern(patternId)
            suggestionCreated = (id != null)
        }
    } catch (t: Throwable) {
        Log.w("BehaviorReport", "generation chain failed: ${t.javaClass.simpleName}")
    }

    // 无论链路成不成功，都保证用户看到内容
    if (!suggestionCreated) {
        createFallbackSuggestion()
    }
}

private suspend fun createFallbackSuggestion() {
    val now = System.currentTimeMillis()
    suggestionDao.insert(
        AnomalySuggestion(
            title = "暂停一下",
            suggestionDomain = "BEHAVIOR",
            actionText = KhupPromptPolicy.FALLBACK_SUGGESTION_ACTION,
            whyText = KhupPromptPolicy.FALLBACK_SUGGESTION_WHY,
            costLevel = "LOW",
            expectedUpside = KhupPromptPolicy.FALLBACK_SUGGESTION_UPSIDE,
            status = "PENDING",
            patternId = null,
            patternKey = null,
            modelVersion = "fallback-static",
            dayStartMs = todayStartLocalMs(),
            regenerationCount = 0,
            createdAt = now,
            updatedAt = now,
        )
    )
}
```

**同时检查 `AnomalySuggestionGeneratorImpl`**：确认 JSON 解析失败时也有 fallback 写库（Module 2 的规格要求了这一点，但可能实现漏掉）：

```kotlin
// 在 AnomalySuggestionGeneratorImpl 里，LLM 失败/校验失败的 catch/else 分支：
// 应该写入一条 fallback AnomalySuggestion，而不是直接 return null
// 如果当前是 return null，改为写入 fallback 再 return id
```

### A.4 如何验证修复

```bash
# 触发 bug 后看 logcat
adb logcat | grep -iE "BehaviorReport|generation|fallback|pattern"
```

修复后期望：
- 看到 `"generation chain failed: ..."` 或 `"analyzeUserReport returned null"` 的警告日志
- 紧接着看到 fallback suggestion 写库的日志（或 anomaly_suggestion 表里多了一条 modelVersion='fallback-static' 的记录）
- TodayScreen 卡片显示出建议内容（即使是 fallback 静态文案）

### A.5 调试：确认是哪个环节失败

在 `Database Inspector` 执行：

```sql
-- 检查 EVENT 有没有落库
SELECT * FROM events WHERE type = 'USER_REPORT' ORDER BY timestamp DESC LIMIT 5;

-- 检查 pattern 有没有生成
SELECT * FROM attention_anomaly WHERE type = 'USER_REPORTED_DEFAULT_PATH' ORDER BY createdAt DESC LIMIT 5;

-- 检查 suggestion 有没有生成
SELECT id, title, status, modelVersion, createdAt FROM anomaly_suggestion ORDER BY createdAt DESC LIMIT 10;
```

- Event 有，pattern 没有 → `RegressionPatternGenerator` JSON 解析失败
- Event 和 pattern 都有，suggestion 没有 → `AnomalySuggestionGenerator` 失败或 24h 冷却
- 都有但 suggestion 是 REJECTED/ACCEPTED → 24h 内已处理过同 patternKey，UI 正常显示空态

---

## Part B：应用使用时间入口

### B.1 方案

参考每日计划的处理方式：**TodayScreen 顶部加一个时钟小图标，点击进入应用使用时间子页**。

不新增 tab，不改主布局。

**TodayScreen 顶部条最终形态**：

```
┌─────────────────────────────────────┐
│  KHUP              [📋]  [🕐]  [⚙]  │
│                    ↑     ↑     ↑    │
│                   计划  用时  设置  │
└─────────────────────────────────────┘
```

- `📋` 每日计划（已有，Module 6）
- `🕐` 应用使用时间（本补丁新增）
- `⚙` 设置（如已有保留，如无可去掉，设置已在底部 tab）

图标用 `Icons.Outlined.Timer` 或 `Icons.Default.AvTimer`，24dp，与 📋 图标风格一致。

### B.2 文件清单

```
output/ui/usage/
  AppUsageScreen.kt              ≤ 150 行
  AppUsageViewModel.kt           ≤ 120 行
  components/
    AppUsageRow.kt               ≤ 80 行
    UsageSummaryHeader.kt        ≤ 60 行
```

### B.3 路由

在 `MainScreen.kt` NavHost 里：

```kotlin
composable("today") {
    TodayScreen(
        onNavigateToDailyPlan = { navController.navigate("daily_plan") },
        onNavigateToAppUsage = { navController.navigate("app_usage") },   // ← 新增
        // ...
    )
}

composable("app_usage") {
    AppUsageScreen(onBack = { navController.popBackStack() })
}
```

### B.4 AppUsageScreen 布局

```
┌─────────────────────────────────────┐
│  ←  应用使用时间                    │  ← 顶部条 + 返回
│  [今日]  [7天]  [30天]              │  ← 时段切换 Tab
├─────────────────────────────────────┤
│                                     │
│  总屏幕时间  2 小时 14 分           │  ← 大字摘要
│  较昨日 +23 分                      │  ← 对比（可选，有数据再做）
│                                     │
│  ─────────────────────────          │
│                                     │
│  抖音          47 分    ████████    │
│  微信          31 分    █████       │
│  知乎          18 分    ███         │
│  Chrome        12 分    ██          │
│  ...                                │
│                                     │
└─────────────────────────────────────┘
```

**列表排序**：按使用时长降序。**不加"健康 / 不健康"标签，不给 App 打分**（避免 KHUP 变成评分工具）。

横向进度条宽度 = 该 App 时长 / 当日最长时长（不是 / 总时长，避免条太短）。

### B.5 AppUsageViewModel

```kotlin
@HiltViewModel
class AppUsageViewModel @Inject constructor(
    private val appSessionDao: AppSessionDao,
) : ViewModel() {

    enum class Period { TODAY, WEEK, MONTH }

    private val _period = MutableStateFlow(Period.TODAY)
    val period: StateFlow<Period> = _period.asStateFlow()

    val usageData: StateFlow<AppUsageUiState> = _period.flatMapLatest { p ->
        val (startMs, endMs) = p.toTimeRange()
        appSessionDao.observeUsageSummary(startMs, endMs)
            .map { sessions -> buildUiState(sessions) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUsageUiState.Loading)

    fun selectPeriod(period: Period) { _period.value = period }

    data class AppEntry(
        val packageName: String,
        val appLabel: String,       // 从 PackageManager 取，缓存
        val totalMs: Long,
        val iconDrawable: Any?,     // Drawable，UI 层处理
    )

    sealed class AppUsageUiState {
        object Loading : AppUsageUiState()
        data class Ready(
            val totalMs: Long,
            val apps: List<AppEntry>,    // 已按 totalMs 降序排列
        ) : AppUsageUiState()
    }
}
```

**`AppSessionDao` 可能需要新增聚合查询**（如果没有）：

```kotlin
@Query("""
    SELECT packageName, SUM(durationMs) as totalMs
    FROM app_sessions
    WHERE startAt >= :startMs AND startAt < :endMs
    GROUP BY packageName
    ORDER BY totalMs DESC
""")
fun observeUsageSummary(startMs: Long, endMs: Long): Flow<List<PackageUsage>>

data class PackageUsage(val packageName: String, val totalMs: Long)
```

（`PackageUsage` 是一个 Room `@DatabaseView` 或直接用 `data class` 作为查询结果映射，不需要新表）

### B.6 AppLabel 缓存

从 `PackageManager` 取 App 名称可能慢，用 `CategoryUsageCache`（已有）或简单 `remember { }` 缓存：

```kotlin
// 在 AppUsageRow.kt 的 Composable 里
val appLabel = remember(packageName) {
    runCatching {
        context.packageManager.getApplicationInfo(packageName, 0)
            .loadLabel(context.packageManager).toString()
    }.getOrElse { packageName }   // 取不到用包名兜底
}
```

### B.7 时长格式化工具

如果项目里已有，复用。否则放在 `common/util/TimeUtil.kt`：

```kotlin
fun formatDuration(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 -> "${h} 小时 ${m} 分"
        m > 0 -> "${m} 分钟"
        else  -> "< 1 分钟"
    }
}
```

### B.8 TodayScreen 改动

`TodayScreen.kt`：

```kotlin
@Composable
fun TodayScreen(
    onNavigateToDailyPlan: () -> Unit,
    onNavigateToAppUsage: () -> Unit,     // ← 新增参数
    onNavigateToAi: () -> Unit,
    // ...
)
```

TopAppBar actions 区域：

```kotlin
actions = {
    IconButton(onClick = onNavigateToDailyPlan) {
        Icon(Icons.Outlined.ChecklistRtl, contentDescription = "今日计划")
    }
    IconButton(onClick = onNavigateToAppUsage) {          // ← 新增
        Icon(Icons.Outlined.AvTimer, contentDescription = "应用使用时间")
    }
    // ⚙ 按钮（如果有保留）
}
```

---

## 验收清单

### Bug 修复验收

- [ ] 提交任意一段处境（"我很无聊"），5-30 秒内 TodayScreen 必须出现建议卡片
- [ ] 即使 LLM 返回非标准 JSON，也应出现 fallback 建议（actionText 包含 `FALLBACK_SUGGESTION_ACTION` 的内容）
- [ ] Database Inspector 查 `anomaly_suggestion`，每次提交后都会新增一行（要么 AI 生成，要么 modelVersion='fallback-static'）
- [ ] logcat 中出现建议生成相关日志，不再静默退出

### 应用使用时间验收

- [ ] TodayScreen 顶部出现时钟小图标，风格与计划图标一致
- [ ] 点击跳转到 AppUsageScreen，有返回键
- [ ] 今日列表按时长降序显示各 App
- [ ] 总时长显示正确（与原有 MiniObservationCard 的屏幕时间数字对得上）
- [ ] [今日] / [7天] / [30天] 切换工作
- [ ] App 名称显示正确（非包名）；取不到名称时降级显示包名
- [ ] 列表中没有任何"健康 / 不健康 / 评分 / 危险"标签

---

## 不在本补丁

- 应用使用时间的通知 / 提醒（今日已用 X 分钟） → 下迭代
- 应用黑名单 / 白名单 → 下迭代
- 使用时间与异常值建议的关联（"你今天微信用了 2 小时，建议..."）→ 下迭代（信息线）
