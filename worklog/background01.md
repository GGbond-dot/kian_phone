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
| **UsageStats 接入** | ⏳ 下一步 |
| **Settings 权限 checklist** | ⏳ 下一步 |

---

## 下一步：建议先做 Settings 权限 checklist

**为什么先做这个**：
- UsageStats 需要先有「使用情况访问」权限引导 UI，checklist 是前置条件
- 顺手统一现有的「通知使用权」按钮，UI 更整洁
- 工作量小，1 小时左右

### Settings 页要展示的 5 项权限

每项后面跟 ✅/❌ 状态 + 「去开启」按钮，点击直接跳到对应系统设置页：

1. **通知使用权（NLS）** — `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`
2. **使用情况访问（UsageStats）** — `Settings.ACTION_USAGE_ACCESS_SETTINGS`
3. **悬浮窗权限**（Phase 4 干预弹窗用）— `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`
4. **MIUI 自启动管理** — 需要跳小米专属 Intent（`com.miui.securitycenter` 的 `AutoStartManagementActivity`）
5. **MIUI 省电策略：无限制** — 同样跳小米专属 Intent

第 4、5 项小米的 Intent 在不同 HyperOS 版本上可能失效，要做 try-catch 兜底跳到 App 详情页。

### 当前 Settings 实现位置

- `app/src/main/java/com/kian/khup/output/ui/settings/SettingsScreen.kt`
- 现在应该只有「通知使用权」一个按钮（来自 `collection/notification/NotificationPermissions.kt`）

### 后续（做完 checklist 之后）

**UsageStats 接入**（Phase 1 最后一块）：
- 需要 `PACKAGE_USAGE_STATS` 权限（已经在 manifest 里）
- 写 `collection/usage/UsageStatsCollector.kt`，按小时聚合每个 App 的前台时长
- 写进 events 表（`EventType.APP_SESSION` 已存在）或单独的 `app_sessions` 表（看 `core/data/db/entities/AppSession.kt`）
- Dashboard tab 接上，显示「今日 Top 5 使用时长」
- 详见架构文档 §5.2

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
