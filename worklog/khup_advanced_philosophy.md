# KHUP 进阶设计文档：精神肥胖、均值与异常值

> 本文是 KHUP 后续功能的上层设计约束。它不是营销文案，而是产品、模型和规则系统的共同方向。

---

## 1. 项目世界观

KHUP 面向的是一个新的肥胖症时代：精神肥胖症。

工业革命让食物从稀缺变成过剩，身体开始在丰盛中失控；信息革命让内容从稀缺变成过剩，心智开始在丰盛中失控。

这个时代最稀缺的不是信息，而是专注力。算法并不需要恶意，它只要不断把人带回历史偏好，就足以把一个人的明天压回昨天的均值。

KHUP 的任务不是替用户过一种“正确生活”，而是识别回归模式，在合适的时候把人从均值拉出来，让用户重新生产异常值。

但异常值分两类：

- 创造性异常：学新东西、深读、动手、深谈、运动、独处、写作、与具体的人见面。
- 逃逸性异常：用一种刺激替换另一种刺激，例如报复性熬夜、深夜刷屏、冲动消费、暴怒切屏。

KHUP 要奖励和保护前者，识别和温和拦截后者。

---

## 2. 分层架构

### 第一层：共享世界观

适用范围：

- AI 对话
- 每日复盘
- 今日反馈
- 后续主动提醒

要求：

- 不道德审判。
- 不把用户当病人。
- 不把“自律”当作唯一答案。
- 只在数据有意义时开口，避免廉价重复。

### 第二层：思想资源

思想资源只能化用，不能掉书袋。

允许的方向：

- 人的价值生于极端值领域，算法把人困在均值领域。
- 数字时代的自我剥削经常伪装成自由。
- 注意力有残留效应，切换一次就付一次代价。
- 注意力放在哪里，生命就在哪里。
- 独处、发呆、漫游这些“无用之用”是创造性异常的土壤。

### 第三层：本地诊断官

本地层只报告事实，不写导师话术。

原则：

- 优先用规则和数据库计算，不让小模型猜。
- 输出结构化 JSON。
- 诊断项必须带数值或明确空值。

目标输出：

```json
{
  "总时长": "Xh Ym",
  "类目超基线": [{"类目":"","今日":"","中位数":"","倍数":0}],
  "茧房收敛": {"是否":false,"主导标签":"","占比":""},
  "异常模式": [],
  "异常性质": "创造性 | 逃逸性 | 混合 | 无明显异常"
}
```

第一版数据来源：

- `UsageStatsCollector`：今日总用机、Top App。
- `app_sessions`：已同步的 App 使用聚合。
- `attention_anomaly`：今日异常。
- `trigger_tags`：诱因标签。
- `events + derived_results`：通知来源与分类。

暂缺能力：

- 稳定的系统解锁次数。
- 短间隔重复解锁。
- 报复性切屏。
- 真正的内容主题标签。
- 类目 7 日中位数的长期缓存。

### 第四层：无名导师

云端 API 负责把诊断 JSON 变成一句对用户有用的话。

要求：

- 不寒暄。
- 不复述数据。
- 不超过 200 字。
- 有值得说的才说。
- 一次只抓一个点。
- 给出一个具体、今晚或明天可以执行的创造性异常行动。
- 如果是逃逸性异常，语气更靠近，少建议，多问一句。

---

## 3. 下一阶段实现顺序

1. 新增共享 `KhupPromptPolicy`，提供世界观和导师约束的压缩版。
2. 在 MCP-style 本地工具层新增 `get_today_diagnosis`。
3. 让 `get_today_context` 包含诊断 JSON。
4. AI 对话命中“为什么失控 / 明天守哪条防线”时优先调用诊断工具。
5. 每日复盘后续拆成两段：事实复盘 + 无名导师一句话。
6. 后续补解锁、切屏、内容主题和 7 日类目中位数缓存。

---

## 4. 产品判断

这套哲学的核心不是“减少手机使用”，而是“减少被均值牵引”。

少用手机只是表象。真正目标是让用户重新拥有生产异常值的空间。

---

## 5. 2026-05-09 睡前讨论：明日优先做逃逸性异常检测

今晚已经完成：

- `KhupPromptPolicy`：把世界观、导师表达、本地工具规则压缩进共享 policy。
- `AiLocalToolRegistry.get_today_diagnosis`：新增本地诊断 JSON 基础版。
- `get_today_context`：已包含诊断 JSON、用机、异常、诱因、通知、复盘。
- AI 聊天和每日复盘已接入共享世界观。

明天建议优先做：**逃逸性异常检测增强**。

原因：当前诊断工具已经能判断一部分“逃逸性”，但主要依赖用时、通知异常和诱因标签。真正的逃逸性异常往往不是总时长高，而是行为模式失控：

- 一会儿微信。
- 一会儿短视频。
- 一会儿通知。
- 一会儿又点亮屏幕。
- 没有完成任何一个具体选择，只是在入口之间来回逃。

这才是“被均值拉回去”的行为纹理。

### 明天第一阶段：前台切换检测

目标：检测报复性切屏 / 快速切换。

建议实现：

- 在 `UsageStatsCollector` 新增前台切换事件读取：
  - `ACTIVITY_RESUMED`
  - `MOVE_TO_FOREGROUND`
- 过滤：
  - KHUP 自己
  - 系统包
  - 连续相同 package
- 在 `AttentionAnomalyDetector` 新增规则：
  - `rapid_app_switching`
  - 10 分钟内前台 App 切换 >= 10 次
- 写入 `attention_anomaly`，不用新表。

展示文案示例：

```text
10 分钟内切换 12 次 App
```

### 明天第二阶段：亮屏 / 重复解锁近似检测

目标：检测短间隔重复解锁和睡前反复点亮。

先用 `UsageEvents.Event.SCREEN_INTERACTIVE` 近似解锁事件。

建议规则：

- `repeated_unlocks`
  - 5 分钟内亮屏 >= 3 次
- `late_repeated_unlocks`
  - 22:30 后亮屏 >= 5 次

注意：这不是严格系统 unlock，但足够作为第一版“反复回到手机”的行为信号。

### 明天第三阶段：并入诊断 JSON

`get_today_diagnosis` 的 `异常模式` 应包含：

- 深夜长时使用
- 通知暴增
- 单一来源高频打断
- App / 类目超基线
- 快速切屏
- 重复亮屏
- 睡前重复亮屏

`异常性质` 判断权重建议：

- 逃逸性加分：
  - `rapid_app_switching`
  - `repeated_unlocks`
  - `late_repeated_unlocks`
  - `late_algorithm_usage`
  - `notification_burst`
  - `notification_source_burst`
  - 算法内容 / 社交打断 / 促销推广 / 情绪逃避标签
- 创造性加分：
  - 学习工作标签
  - 必要事务标签
  - 后续深读 / 写作 / 运动类 App 稳定长时间使用

### 明天第四阶段：导师反馈预留

等本地诊断更硬之后，再接“无名导师一句话”。

目标链路：

```text
本地诊断官 JSON
→ 云端 API 无名导师
→ 不超过 200 字的一句话反馈
```

逃逸性异常时，导师不要奖励“异常”，而要指出它不是出走，是更深的困住。

示例方向：

```text
这不是出走，是在原地换入口。今晚别再找下一个刺激了，把手机放远，坐十分钟，等那阵想逃的劲过去。
```

### 明日开工检查清单

1. 读 `UsageStatsCollector`，确认 `UsageEvents` 可复用。
2. 新增前台切换事件模型和方法。
3. 新增亮屏事件模型和方法。

---

## 6. 2026-05-09 继续：逃逸性异常检测第一版

本轮完成：

- `UsageStatsCollector` 新增前台切换事件读取：
  - `ForegroundSwitchEvent`
  - `getForegroundSwitchEvents(startMs, endMs)`
  - 过滤 KHUP 自身、系统包、连续相同 package
- `UsageStatsCollector` 新增亮屏事件读取：
  - `ScreenInteractiveEvent`
  - `getScreenInteractiveEvents(startMs, endMs)`
- `AttentionAnomalyDetector` 新增 3 个逃逸性规则：
  - `rapid_app_switching`：10 分钟内前台 App 切换 >= 10 次
  - `repeated_unlocks`：5 分钟内亮屏 >= 3 次
  - `late_repeated_unlocks`：22:30 后亮屏 >= 5 次
- `get_today_diagnosis` 已把新类型并入 `异常模式` 和 `异常性质=逃逸性` 权重。
- Dashboard 异常卡片已补对应建议文案。

验证：

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.kian.khup.debug/com.kian.khup.MainActivity
adb logcat -d -b crash
```

结果：

- `assembleDebug` 成功。
- APK 安装成功。
- 主界面启动成功。
- crash buffer 无内容。
- 真机日志确认 `AttentionAnomalyDetector.detectToday()` 已执行。
- 导出 debug 数据库只读查询，已实际命中新类型：
  - `repeated_unlocks`：5 分钟内亮屏 8 次
  - `rapid_app_switching`：10 分钟内切换 10 次 App
- `late_repeated_unlocks` 需要 22:30 后样本，白天验证时不会命中。

后续建议：

1. 用真机实际切换/亮屏一段时间后，确认 `attention_anomaly` 是否出现新类型。
2. 如果误报偏高，再调阈值或给连续亮屏增加最小间隔去重。
3. 后面再接“无名导师一句话”，让云端只基于诊断 JSON 输出一条行动建议。

---

## 7. 2026-05-09 继续：无名导师一句话

本轮完成：

- `DailyReviewGenerator` 在每日复盘生成后，额外构造本地诊断 JSON：
  - `总时长`
  - `类目超基线`
  - `茧房收敛`
  - `异常模式`
  - `异常性质`
- 新增无名导师 prompt：
  - 输入只给诊断 JSON 和共享世界观 / 表达约束。
  - 输出只允许一句中文。
  - 不超过 200 字。
  - 不寒暄、不复述数据、不道德审判。
  - 逃逸性异常时语气更靠近，少建议，多问一句。
- 结果写入 `daily_review.highlights` 的 `无名导师` 字段，不改 Room schema。
- 同时把本轮诊断 JSON 写入 `daily_review.highlights` 的 `诊断` 字段，方便后续调试和复用。
- LLM 失败时有规则 fallback，避免每日复盘因为导师句子失败而整体失败。
- Dashboard 今日复盘卡片会优先展示 `无名导师` 字段，再展示原复盘正文。

验证：

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.kian.khup.debug/com.kian.khup.MainActivity
adb logcat -d -b crash
```

结果：

- `assembleDebug` 成功。
- APK 安装成功。
- 主界面启动成功。
- crash buffer 无内容。

注意：

- 真机启动验证后 ADB 设备临时断开，未能继续点击“重新生成今日复盘”做入库字段验证。
- 设备恢复后建议手动重新生成一次复盘，再查询 `daily_review.highlights` 是否包含 `无名导师` 和 `诊断`。

设备恢复后补充验证：

- 点击“重新生成今日复盘”成功。
- UI 已展示 `无名导师` 一句话。
- 导出 `khup.db + khup.db-wal + khup.db-shm` 后确认：
  - `modelVersion=daily-review-v3`
  - `highlights` 包含 `无名导师`
  - `highlights` 包含 `诊断`
  - `诊断.异常性质=混合`

---

## 8. 2026-05-09 继续：AI 最近 7 天查询能力

本轮完成：

- 新增 DAO 只读查询，不改 schema：
  - `AppSessionDao.loadDailyUsageSince`
  - `TriggerTagDao.loadTagTotalsSince`
  - `TriggerTagDao.loadDailyTagTotalsSince`
  - `AttentionAnomalyDao.loadSince`
- `AiLocalToolRegistry` 新增 `get_weekly_context`：
  - 最近 7 天每日用机
  - 最近 7 天 Top App
  - 最近 7 天 Top 诱因
  - 每日主导诱因
  - 最近 7 天主要异常
  - 最重用机日
- AI 工具选择新增周维度关键词：
  - `这周`
  - `本周`
  - `最近7天`
  - `哪天最失控`
  - `最大诱因`
  - `week`
  - `7 days`
  - `biggest trigger`
- 周关键词优先级高于“今天上下文”，避免“这周为什么失控”被误判为今日查询。

验证：

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

真机 AI 对话输入：

```text
this week biggest trigger
```

结果：

- API 正常返回。
- 回答命中最近 7 天诱因统计：`社交打断，7 天共 186 次`。
- crash buffer 无内容。

---

## 9. 2026-05-09 继续：内容主题、信息茧房、类目基线缓存

本轮严格对应文档中的三项暂缺能力：

- `真正的内容主题标签`
- `信息茧房 / 内容偏食干预`
- `类目 7 日中位数的长期缓存`

完成内容：

1. 新增 `content_theme_tags` 表，Room 升到 v9。
   - 实体：`ContentThemeTag`
   - DAO：`ContentThemeTagDao`
   - migration：`MIGRATION_8_9`

2. 新增 `ContentThemeTagger`。
   - 只基于文档允许的数据推断：
     - 通知标题 / 正文 / channel / category
     - 小时摘要
     - App 使用
   - 不读取屏幕内容，不做 OCR，不联网检索。
   - 第一版主题：
     - 社交关系
     - 学习课程
     - 工作任务
     - 消费种草
     - 娱乐内容
     - 奖励循环
     - 争议新闻
     - 状态焦虑
     - 比赛观赛
     - 算法内容

3. 新增 `InformationCocoonAnalyzer`。
   - 基于 `content_theme_tags` 判断：
     - 今日主导主题
     - 主导主题占比
     - 连续主导天数
     - 是否茧房收敛
     - 轻量干预建议
   - 干预保持文档边界：
     - 冷却提醒
     - 自我提问
     - 事实核查式提醒
     - 兴趣迁移方向

4. `DailyReviewGenerator` 接入内容主题和茧房分析。
   - 生成复盘前刷新内容主题。
   - prompt 增加“内容主题”和“信息茧房风险”。
   - `daily_review.highlights["诊断"]` 增加：
     - `内容主题`
     - 基于主题的 `茧房收敛`

5. `AiLocalToolRegistry` 接入内容主题和茧房分析。
   - `get_today_diagnosis` 输出 `内容主题`。
   - `茧房收敛` 改为基于内容主题，而不是只看诱因标签。
   - `get_weekly_context` 增加：
     - 最近 7 天 Top 内容主题
     - 每日主导内容主题

6. 新增 `category_usage_cache` 表，Room 升到 v10。
   - 实体：`CategoryUsageCache`
   - DAO：`CategoryUsageCacheDao`
   - migration：`MIGRATION_9_10`

7. 新增 `CategoryUsageCacheRefresher`。
   - 按天缓存类目用时：
     - 算法内容
     - 社交
     - 学习工作
     - 消费
     - 必要事务
     - 其他
   - `get_today_diagnosis` 现在刷新最近 8 天缓存，再从 `category_usage_cache` 读取过去 7 天类目中位数。
   - 避免每次诊断都临时扫 7 天 UsageEvents。

验证：

```bash
./gradlew assembleDebug
```

结果：

- `assembleDebug` 成功。
- 本轮按用户要求未做上机验证。
- 编译只剩已有的 Room destructive migration deprecation warning。

后续仍未做：

- 稳定系统 unlock 事件，不再用 `SCREEN_INTERACTIVE` 近似。
- 切屏序列的链路结构分析。
- 信息茧房更高级的反向视角 / 事实核查 / 外部来源推荐。
