# Module 3 — Repository 层

## 模块定位

把 Module 1 的 DAO 和 Module 2 的 Generator 编排成业务用例，为 Module 4 (UI) 提供干净的接口。

依赖：
- Module 1：`Event`, `AttentionAnomaly`, `AnomalySuggestion`, `UserFeedback` entities + DAOs
- Module 2：`RegressionPatternGenerator`, `AnomalySuggestionGenerator`

可与 Module 2 并行实现，但合并前需要 Module 1 已稳定。

读本文件前需要先看 `00_总览.md`，特别是第 1.2 节"不衡量执行原则"——它直接决定了 `accept` 不需要"标记完成"的链路。

---

## 1. 三个 Repository

```
core/data/repository/
  BehaviorReportRepository.kt
  AnomalySuggestionRepository.kt
  UserFeedbackRepository.kt
```

每个 Repository 都是接口 + 实现类（`*Impl`），通过 Hilt/Dagger 注入。

---

## 2. BehaviorReportRepository

### 2.1 职责

- 接收用户输入的处境文本
- 写入 `Event(USER_REPORT)`
- **异步**触发 Generator 链路：先 Pattern，再 Suggestion
- 不阻塞 UI：调用方 `submit` 立即返回 eventId

### 2.2 接口

```kotlin
interface BehaviorReportRepository {
    /**
     * 用户提交一段行为处境。
     *
     * @param text 用户输入原文（≤ 500 字符；上层应已截断，本层再做防御性截断）
     * @return 新写入的 Event.eventId
     *
     * 副作用：异步触发 RegressionPatternGenerator → AnomalySuggestionGenerator 链路。
     * 调用方不需要 await，UI 通过 observe AnomalySuggestionRepository 拿到结果。
     */
    suspend fun submit(text: String): String
}
```

### 2.3 实现要点

```kotlin
class BehaviorReportRepositoryImpl(
    private val eventDao: EventDao,
    private val patternGenerator: RegressionPatternGenerator,
    private val suggestionGenerator: AnomalySuggestionGenerator,
    private val anomalyDao: AttentionAnomalyDao,
    private val applicationScope: CoroutineScope,    // @ApplicationScope 注入
) : BehaviorReportRepository {

    override suspend fun submit(text: String): String {
        val sanitized = text.trim().take(500)
        require(sanitized.isNotBlank()) { "report text must not be blank" }

        val now = System.currentTimeMillis()
        val pkg = "com.kian.khup.user_report"
        val eventId = sha256("$pkg|$sanitized|${now / 1000}")

        eventDao.insertIgnore(
            Event(
                eventId = eventId,
                type = EventType.USER_REPORT,
                packageName = pkg,
                timestamp = now,
                title = null,
                text = sanitized,
            )
        )

        // 异步触发 Generator 链路；不 join、不让用户等
        applicationScope.launch {
            try {
                val patternId = patternGenerator.analyzeUserReport(eventId) ?: return@launch
                suggestionGenerator.generateForPattern(patternId, regenerationCount = 0)
            } catch (t: Throwable) {
                // 静默：UI 通过空态文案降级；写一行 redacted log
                Log.w("BehaviorReport", "generation chain failed: ${t.javaClass.simpleName}")
            }
        }

        return eventId
    }
}
```

**关键约束**：
- `applicationScope` 必须是 `Dispatchers.IO` 或 application-scoped `SupervisorJob`，**不能用 viewModelScope**（用户 navigate away 后还要继续）
- `submit` 本身的 IO（写 Event）必须 await，确保返回时数据已落
- Generator 链路异步，UI 通过 `AnomalySuggestionRepository.observeTodayPending()` 收到新建议

---

## 3. AnomalySuggestionRepository

### 3.1 职责

- 给 UI 暴露"今天的建议"observable
- 处理三个反馈按钮（accept / postpone / reject）
- 给 HistoryScreen 暴露按状态分组的列表

### 3.2 接口

```kotlin
interface AnomalySuggestionRepository {

    /**
     * TodayScreen 主卡片观察。返回今天最新的 PENDING 建议（最多一条）。
     * 没有 → emit null（UI 显示空态引导）。
     */
    fun observeTodayPending(): Flow<AnomalySuggestion?>

    /**
     * 按 status 分组观察。HistoryScreen 用。
     */
    fun observeRecentByStatus(status: String, limit: Int = 100): Flow<List<AnomalySuggestion>>

    /** 用户点 [接受]：标记 ACCEPTED 终态，写 UserFeedback。 */
    suspend fun accept(suggestionId: Long)

    /**
     * 用户点 [换一个]：把当前标记 POSTPONED，立即触发同 pattern 的重生成。
     * 不限次数；regenerationCount 累加。
     */
    suspend fun postpone(suggestionId: Long)

    /** 用户点 [不适合]：标记 REJECTED 终态，写 UserFeedback。reason 可选。 */
    suspend fun reject(suggestionId: Long, reason: String? = null)
}
```

### 3.3 实现要点

```kotlin
class AnomalySuggestionRepositoryImpl(
    private val suggestionDao: AnomalySuggestionDao,
    private val feedbackDao: UserFeedbackDao,
    private val suggestionGenerator: AnomalySuggestionGenerator,
    private val applicationScope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : AnomalySuggestionRepository {

    override fun observeTodayPending(): Flow<AnomalySuggestion?> {
        val dayStart = todayStartLocalMs()
        return suggestionDao.observeTodayPending(dayStart)
        // DAO 已 LIMIT 1，返回 Flow<AnomalySuggestion?>
    }

    override fun observeRecentByStatus(status: String, limit: Int): Flow<List<AnomalySuggestion>> =
        suggestionDao.observeByStatus(status, limit)

    override suspend fun accept(suggestionId: Long) {
        val now = clock()
        suggestionDao.updateStatus(suggestionId, "ACCEPTED", now)
        feedbackDao.insert(
            UserFeedback(
                targetType = "SUGGESTION",
                targetId = suggestionId,
                feedbackType = "ACCEPT",
                createdAt = now,
            )
        )
        // 不再追踪是否真的去做（不衡量执行原则）
    }

    override suspend fun postpone(suggestionId: Long) {
        val now = clock()
        val current = suggestionDao.getById(suggestionId) ?: return

        // 1. 标记当前为 POSTPONED
        suggestionDao.updateStatus(suggestionId, "POSTPONED", now)
        feedbackDao.insert(
            UserFeedback(
                targetType = "SUGGESTION",
                targetId = suggestionId,
                feedbackType = "POSTPONE",
                createdAt = now,
            )
        )

        // 2. 异步触发同 pattern 的重生成（绕过 24h 冷却）
        val patternId = current.patternId ?: return
        applicationScope.launch {
            try {
                suggestionGenerator.generateForPattern(
                    patternId = patternId,
                    regenerationCount = current.regenerationCount + 1,
                    parentSuggestionId = suggestionId,
                )
            } catch (t: Throwable) {
                Log.w("SuggestionRepo", "regeneration failed: ${t.javaClass.simpleName}")
            }
        }
    }

    override suspend fun reject(suggestionId: Long, reason: String?) {
        val now = clock()
        suggestionDao.updateStatus(suggestionId, "REJECTED", now)
        feedbackDao.insert(
            UserFeedback(
                targetType = "SUGGESTION",
                targetId = suggestionId,
                feedbackType = "REJECT",
                reason = reason,
                createdAt = now,
            )
        )
        // 不重新生成；24h 内同 patternKey 不会再有新建议（冷却）
    }
}
```

### 3.4 边界条件

| 场景 | 行为 |
|---|---|
| `accept` 一个非 PENDING 的建议 | 仍执行 update（幂等）；UI 应禁用按钮防止重复点击 |
| `postpone` 时 patternId == null（异常数据） | 只标记 POSTPONED，不重生成；写一行警告 log |
| 用户连续点 `postpone` 5 次 | 不限制；regenerationCount 持续累加 |
| 重生成期间 UI 状态 | UI 通过 `observeTodayPending` 看到 null（旧已 POSTPONED，新还没生成）→ 期间显示"正在观察..." |

---

## 4. UserFeedbackRepository

### 4.1 职责

通用反馈写入。SUGGESTION 类型已被 `AnomalySuggestionRepository` 内部用了；本 Repository 给其他 targetType 用（PATTERN / DAILY_REVIEW / AI_MESSAGE 等），未来扩展。

### 4.2 接口

```kotlin
interface UserFeedbackRepository {
    suspend fun record(
        targetType: String,
        targetId: Long,
        feedbackType: String,
        rating: Int? = null,
        reason: String? = null,
    )

    /** 7 天某类反馈历史。Generator 上下文用。 */
    suspend fun recent(targetType: String, sinceMs: Long, limit: Int = 50): List<UserFeedback>
}
```

### 4.3 实现要点

```kotlin
class UserFeedbackRepositoryImpl(
    private val dao: UserFeedbackDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : UserFeedbackRepository {

    override suspend fun record(
        targetType: String, targetId: Long,
        feedbackType: String, rating: Int?, reason: String?,
    ) {
        dao.insert(UserFeedback(
            targetType = targetType, targetId = targetId,
            feedbackType = feedbackType, rating = rating, reason = reason,
            createdAt = clock(),
        ))
    }

    override suspend fun recent(targetType: String, sinceMs: Long, limit: Int) =
        dao.recentByTargetType(targetType, sinceMs, limit)
}
```

**MVP 阶段本 Repo 可能不会被 UI 直接调用**（UI 只调 `AnomalySuggestionRepository`）。它的存在是为下次扩展（PATTERN 反馈、AI 对话反馈）做接口预留。

---

## 5. DI 接线

`common/di/RepositoryModule.kt`（若不存在则新建）：

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindBehaviorReportRepository(impl: BehaviorReportRepositoryImpl): BehaviorReportRepository

    @Binds @Singleton
    abstract fun bindAnomalySuggestionRepository(impl: AnomalySuggestionRepositoryImpl): AnomalySuggestionRepository

    @Binds @Singleton
    abstract fun bindUserFeedbackRepository(impl: UserFeedbackRepositoryImpl): UserFeedbackRepository
}
```

`@ApplicationScope` 的 CoroutineScope 注入（如已有跳过；如没有，加到 `CoroutineModule`）：

```kotlin
@Provides @Singleton @ApplicationScope
fun provideApplicationScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

---

## 6. 工具函数

### 6.1 `todayStartLocalMs()`

如果项目里已有，复用。否则放在 `common/util/TimeUtil.kt`：

```kotlin
fun todayStartLocalMs(zone: ZoneId = ZoneId.systemDefault()): Long {
    val now = ZonedDateTime.now(zone)
    return now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
}
```

### 6.2 `sha256(input)`

如果项目里已有用于 Event.eventId 生成的 hash 函数，复用。**不要重复实现**。

---

## 7. 测试

文件：`app/src/test/java/com/kian/khup/repository/`

最小测试集：

```kotlin
class BehaviorReportRepositoryTest {
    @Test
    fun submit_writesEventAndTriggersChain() = runTest {
        // 用 in-memory Room + mock Generator
        // 验证 Event 被写入、patternGenerator.analyzeUserReport 被调用
    }

    @Test
    fun submit_blankText_throws() = runTest { /* ... */ }

    @Test
    fun submit_textTruncatedTo500() = runTest { /* ... */ }
}

class AnomalySuggestionRepositoryTest {
    @Test
    fun accept_marksAcceptedAndWritesFeedback() = runTest { /* ... */ }

    @Test
    fun postpone_marksPostponedAndTriggersRegenerate() = runTest { /* ... */ }

    @Test
    fun postpone_withoutPatternId_skipsRegeneration() = runTest { /* ... */ }

    @Test
    fun reject_marksRejectedNoRegeneration() = runTest { /* ... */ }
}
```

Mock Generator 接口的实现：返回固定 id 或 null，验证调用次数。

---

## 8. 模块验收

- [ ] 三个 Repository 接口 + Impl 完整实现
- [ ] DI 接线生效，UI 层 inject 后能正常调用
- [ ] `submit` 后 Event 立即落库、Generator 链路异步触发
- [ ] `postpone` 后立即触发 regeneration，绕过 24h 冷却
- [ ] `reject` 后**不**触发 regeneration（依赖 24h 冷却抑制重复）
- [ ] 上述测试集全通过
- [ ] 文件长度：每个 Repo Impl ≤ 200 行

---

## 9. 不在本模块的工作

- LLM prompt / JSON 解析 → Module 2
- DAO 实现 → Module 1
- UI 调用 / Compose 状态管理 → Module 4
- 历史趋势的统计聚合（7/30 天图表） → Module 4 在 ViewModel 里直接 query DAO 即可，不需要 Repo 包一层
