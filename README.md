# KHUP

> Kian Health Use Phone — 个人 Android 端消息聚合 + 数字健康管理工具。
> 详细架构见 [`ARCHITECTURE.md`](./ARCHITECTURE.md)。

## 当前进度

**Phase 1（骨架完成）**：Gradle 工程 + Manifest + Room 数据库 + NotificationListener + Compose UI 四个 tab + 权限引导。
未接入 LLM、UsageStats、规则引擎、拦截系统（按 Phase 2-4 逐步加）。

## 第一次运行

1. 用 Android Studio 打开本目录（`File → Open` 选 `kian_phone/`）。
2. 让 Studio 自动同步 Gradle，下载 SDK / 依赖。
   - 第一次会比较慢，国内用户可能需要科学上网或确认 `settings.gradle.kts` 里的阿里云镜像生效。
3. 用 USB 把小米 14 接电脑，在 Studio 顶部设备选择里选中真机。
4. 点 ▶ Run。
5. 装上后打开 KHUP，进「设置」tab，分别授权三项权限：
   - **通知使用权**（必需，没这个等于没装）
   - **用机时长统计**（Phase 3 才用，可以先不开）
   - **悬浮窗**（Phase 4 才用，可以先不开）
6. 授权完回到「首页」，看看是否开始接收通知。

## 构建 / 重建

Android Studio 如果找不到 `Rebuild Project` 菜单，用下面两步等价替代：

1. `Build → Clean Project`
2. `Build → Make Project`

命令行可以直接跑：

```bash
./gradlew clean assembleDebug
```

如果刚改过 `KhupApplication`、Hilt 注解或 WorkManager 入口，但真机表现像旧代码，先卸载 debug 包再重新 Run：

```bash
adb uninstall com.kian.khup.debug
```

## AI 聊天

AI tab 支持本地模型和 OpenAI-compatible API。默认模式是「本地优先」：

- **本地优先**：先尝试端侧模型；本地模型缺失或生成失败时，如果 API 配置完整就回退到 API。
- **仅本地**：完全离线，不发网络请求。
- **仅 API**：跳过本地模型，直接使用 API，适合先验证聊天体验。

### 本地模型

开发期先把 MediaPipe LLM Inference 可用的 Gemma `.task` 模型命名为 `khup_llm.task`，
放到下面任一位置：

```bash
# App 私有目录，推荐正式使用
/data/data/com.kian.khup.debug/files/models/khup_llm.task

# 开发期临时目录，方便 adb push
/data/local/tmp/llm/khup_llm.task

# App 外部私有目录，也会被检测
/storage/emulated/0/Android/data/com.kian.khup.debug/files/models/khup_llm.task
```

AI 页面会显示当前是否找到模型。找到后可以直接聊天，也可以点自检按钮看 `KHUP/AI` 日志。

### API 模式

AI 页面里填写：

- `API Base URL`：例如 `https://api.openai.com/v1`，也可以填 DeepSeek/通义千问等兼容 OpenAI Chat Completions 的地址。
- `Model`：服务端模型名，例如你账号可用的聊天模型。
- `API Key`：只存在本机 SharedPreferences，不写进代码仓库。

当前 API 请求走 `/chat/completions`，请求格式为 OpenAI-compatible chat completions。

## 项目结构

```
app/src/main/java/com/kian/khup/
├── collection/         # 采集层：通知监听、用机统计、外部 API
├── core/               # 核心层：数据库、AI 引擎、规则引擎
├── output/             # 输出层：UI / 提醒 / 拦截
├── common/di/          # Hilt DI module
├── KhupApplication.kt
└── MainActivity.kt
```

## 开发约定

- 数据库 schema 改动期：直接 bump version + 用 destructive migration。1.0 发布前才补正式 Migration。
- NLS 的 `onNotificationPosted` 跑在 Binder 线程，**绝不能阻塞**——任何 IO 都走 channel + 协程。
- 自己 App 发的通知必须在 NLS 里 filter 掉（已实现），否则会循环。
- MIUI 杀后台很激进，所有引导步骤要图文并茂（Phase 4 完善）。

## 调试常用命令

```bash
# 强制允许 / 禁止 NLS（开发期免得反复点设置）
adb shell cmd notification allow_listener com.kian.khup.debug/com.kian.khup.collection.notification.MessageListener
adb shell cmd notification disallow_listener com.kian.khup.debug/com.kian.khup.collection.notification.MessageListener

# dump 通知完整 extras（开发金矿）
adb shell dumpsys notification --noredact

# 查看 KHUP 日志
adb logcat -s KHUP/NLS:V

# 查看本地 AI 日志
adb logcat -s KHUP/AI:V
```
