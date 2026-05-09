# Background 09 — 2026-05-06

> 给下一个对话用的上下文。新会话开始时把 `background01..09.md` 一起贴进去。
> 这一轮落地 `background08.md` 第一阶段：AI 聊天升级为多会话。代码已写完并装机，编译通过、迁移成功；详细行为验收由用户自测。

---

## 本轮目标

按 bg08 第一阶段：把 AI tab 从单会话升级成多会话聊天。

要支持：
- 新建聊天
- 查看历史会话列表
- 切换历史会话
- 删除单个会话
- 清空当前会话（不影响其他会话）
- 当前会话标题自动从首条用户消息生成

---

## 改动清单

### 新增

```text
app/src/main/java/com/kian/khup/core/data/db/entities/ChatSession.kt
app/src/main/java/com/kian/khup/core/data/db/ChatSessionDao.kt
```

`ChatSession` 字段：`id / title / createdAt / updatedAt / lastMessagePreview`，`updatedAt` 索引。

`ChatSessionDao`：`observeAll() Flow / loadAll / findById / insert / rename / touch / deleteById`。

### 修改

```text
M app/src/main/java/com/kian/khup/core/data/db/entities/ChatMessage.kt
M app/src/main/java/com/kian/khup/core/data/db/ChatMessageDao.kt
M app/src/main/java/com/kian/khup/core/data/db/AppDatabase.kt
M app/src/main/java/com/kian/khup/common/di/DatabaseModule.kt
M app/src/main/java/com/kian/khup/output/ui/ai/AiChatViewModel.kt
M app/src/main/java/com/kian/khup/output/ui/ai/AiChatScreen.kt
```

`ChatMessage`：加 `sessionId`，外键 `chat_session(id) ON DELETE CASCADE`，复合索引 `(sessionId, timestamp)` + 保留 `timestamp` 索引。

`ChatMessageDao`：删掉 `loadAll / clear`，换成 `loadBySession / loadRecentBySession / clearSession`。

`AppDatabase`：版本 5 → 6，注册 `ChatSession` + `chatSessionDao()`。

`DatabaseModule`：注册 `ChatSessionDao` 的 `@Provides`，加 `MIGRATION_5_6`（见下）。

### 新增 worklog

```text
worklog/background09.md
```

---

## Room v5 → v6 迁移

`MIGRATION_5_6` 做了下面这些事：

1. 建 `chat_session` 表 + `index_chat_session_updatedAt` 索引。
2. 检测 `chat_message` 是否有遗留消息：
   - 有 → 插入一条默认会话 `('旧对话', now, now, NULL)`，记录其 `id`。
   - 无 → 不创建默认会话，留空状态。
3. 重建 `chat_message`（SQLite 不支持 ALTER TABLE 加外键）：
   - 建临时表 `chat_message_new` 带 `sessionId NOT NULL` + FK + 各列。
   - 如果有默认会话 → 把所有旧消息的 `sessionId` 设成默认会话 id 复制过来。
   - 把默认会话的 `updatedAt` 设成最后一条消息的 `timestamp`，`lastMessagePreview` 设成最后一条消息内容前 60 字。
   - DROP 旧表，RENAME `chat_message_new` 为 `chat_message`。
   - 建索引 `index_chat_message_sessionId_timestamp` 和 `index_chat_message_timestamp`。

迁移已在真机验证：旧的 4 条聊天消息成功挂到 `sessionId=1`「旧对话」。

---

## ViewModel 行为

`AiChatViewModel` 新状态：

```kotlin
data class AiChatUiState(
    ...
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: Long? = null,
) {
    val currentTitle: String
        get() = sessions.firstOrNull { it.id == currentSessionId }?.title ?: "新对话"
}
```

新方法：

- `newSession()` — 重置 `currentSessionId = null`、`messages = []`。**惰性创建**：不立即写 DB，等用户发第一条消息才建 session（避免空会话堆积）。
- `selectSession(sessionId)` — 切换并加载该 session 最近 100 条消息。
- `deleteSession(id)` — DAO 删除（FK CASCADE 把消息也删了），如果删的是当前会话，回退到空状态。
- `clearCurrentSession()` — 只删当前 session 的消息行，session 本身保留，`lastMessagePreview` 置 null、`updatedAt` 刷新。
- `send(text)` 内部调 `ensureSession(...)`，如果 `currentSessionId` 为 null 则建 session，标题取首条用户消息空白合并后前 18 字（>18 字加省略号）。
- 每次写消息后调 `chatSessionDao.touch(id, now, preview)` 更新会话顺序和预览。

`init` 现在用单个 `observeAll().collect`，第一次 emit 时挑最近会话自动选中，后续 emit 仅维护 `sessions` 列表和 `stillExists` 检查。这是为了消除 "observeAll vs loadAll 双协程" 的初始化竞态。

`buildPrompt()` 仍然按 ProviderMode 分 `MAX_CONTEXT_MESSAGES = 8`（local）/ `MAX_CONTEXT_MESSAGES_API = 20`（api），但只取**当前 session** 的消息。

---

## UI 改动

`AiChatScreen.kt` 顶部 Row 现在是：

- 左：当前会话标题（`uiState.currentTitle`，单行省略号）+ 状态副标题（沿用 bg06 那套 ProviderMode 分支）。
- 右：5 个 IconButton
  - `Add` — 新建聊天
  - `History` — 打开历史 ModalBottomSheet
  - `ClearAll` — 清空当前会话（消息为空或正在生成时禁用）
  - `Refresh` — 刷新模型状态
  - `Science` — 自检

历史 sheet（`HistoryListContent`）：
- 标题"历史聊天"。
- 空态："还没有历史聊天。"。
- 否则 `LazyColumn`（max 480.dp）渲染会话卡片，每行：title / lastMessagePreview / updatedAt(`MM/dd HH:mm`) / 右侧 Delete IconButton。
- 当前会话用 `primaryContainer` 高亮，其它用 `surfaceVariant`。
- 点击行调 `selectSession`，并通过 `sheetState.hide()` 关闭 sheet。

`Send` 仍然用 `Icons.Outlined.Send`（编译期有一个 deprecation 警告建议改 `AutoMirrored.Outlined.Send`，本轮不动，保留警告）。

---

## 验证

```bash
./gradlew assembleDebug         # BUILD SUCCESSFUL
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

启动后 `adb logcat -d -b crash` 无内容。

DB 验证：

```text
chat_session:
(1, '旧对话', '我能处理的上下文长度...', <ts>)
chat_message:
sessionId=1, count=4
```

UI dump 顶部 5 个 IconButton（`新建聊天 / 历史聊天 / 清空当前会话 / 刷新模型状态 / 运行自检`）和会话标题、空态卡片均正确渲染。

**剩下交给用户自测的功能点**：
1. 历史 sheet 的打开 / 选择 / 删除交互。
2. 新建聊天 → 发首条消息 → 标题是否自动取消息前 18 字。
3. 清空当前会话是否只清当前 session、其他 session 不动。
4. 删除当前会话后是否回退到"新对话"空状态。
5. 切换会话时 prompt 是否只取当前 session 的消息。

---

## 已知 / 注意事项

1. **新建聊天惰性创建 session**：`newSession()` 只重置内存状态，不写 DB。直到 `send()` 调用 `ensureSession()` 才真正 INSERT。这样反复点"新建"不会堆积空会话。
2. **首条消息标题策略**：MVP 是规则化前 18 字 + 省略号。bg08 提到后续可以让本地 LLM 生成 8 字以内标题，留作下一阶段。
3. **clearCurrentSession 不删 session 本身**：用户可能想"换个话题但保留会话条目"，所以只删消息。如果用户期望的是"把当前会话整条删掉"，应改用历史列表里的 Delete 按钮。
4. **Migration 用了 ON DELETE CASCADE**：删除 `chat_session` 行会自动级联清掉对应消息，简化 `deleteSession`。
5. **`fallbackToDestructiveMigration` 仍在 DatabaseModule 里**：开发期保留，1.0 前再清。
6. **Compose 的 `Icons.Outlined.Send` 编译期警告**：建议未来切到 `Icons.AutoMirrored.Outlined.Send`，本轮没改。
7. `AiSettings`、`AiProviderMode`、`HybridLlmEngine` 等 AI 通道相关代码本轮没动。

---

## 下一步（按 bg08 排序）

第一阶段（多会话聊天）：✅ 代码完成，待用户验收。

第二阶段：异常值检测

- 新建 `attention_anomaly` 表（dayStartMs / type / sourcePackage / windowStartMs / windowEndMs / score / baselineValue / currentValue / explanation 等）。
- 实现 `AttentionAnomalyDetector`，纯规则、无 LLM：
  - App 屏前时长 > 7 天均值 + 2σ
  - 22:30 后高刺激 App 阈值
  - `algorithmic` / `promotion` 通知激增
  - 1 小时内单一来源高频打断
  - 睡前高刺激连续使用
- 调度：每天一次 + 每小时一次（轻量）。
- Dashboard 加异常值卡片，或先并入"最近 1 小时"卡的副信息。

第三阶段：每日复盘深化（注意力复盘 prompt）。
第四阶段：诱因标签 + 信息茧房干预（本地 LLM 打 trigger label）。

---

## 调试速查

```bash
# 拉 DB 看会话状态（机器没装 sqlite3，用 python3 读）
adb exec-out run-as com.kian.khup.debug cat databases/khup.db > /tmp/khup.db
python3 -c "
import sqlite3
c = sqlite3.connect('/tmp/khup.db')
for r in c.execute('SELECT id, title, lastMessagePreview, updatedAt FROM chat_session ORDER BY updatedAt DESC'): print(r)
for r in c.execute('SELECT sessionId, COUNT(*) FROM chat_message GROUP BY sessionId'): print(r)
"

# Schema 确认
python3 -c "
import sqlite3
c = sqlite3.connect('/tmp/khup.db')
for n in ['chat_session','chat_message']:
    for r in c.execute(f'SELECT sql FROM sqlite_master WHERE name=\"{n}\"'): print(r[0])
"
```
