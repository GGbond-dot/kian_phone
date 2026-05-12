# KHUP

KHUP is a personal Android app for noticing when phone use has slipped into a
default loop, then turning that signal into a small, optional action.

It is built for one real device first: Kian's Xiaomi 14. The codebase is still a
personal product lab, not a polished public release.

## Product Philosophy

KHUP is not a generic screen-time blocker, productivity tracker, or habit
scoreboard. Its design comes from a different premise: most phone overuse is not
just "too many minutes"; it is a return to a comfortable regression value shaped
by algorithms, routines, fatigue, and friction.

The app is closer to an anomaly cognition coach:

- It observes local signals such as usage time, notifications, check-ins, and
  recent feedback.
- It names recurring regression patterns without moralizing them.
- It proposes low-cost, positive-upside, rejectable actions that create optionality.

Every suggestion should satisfy three constraints:

- `Low cost`: failing to do it should not create meaningful loss.
- `Positive expected upside`: it should open a new feeling, action, perspective,
  or piece of information.
- `Rejectable`: the user can accept it, ask for another angle, or say it is not
  suitable.

KHUP deliberately avoids completion tracking. Accepting a suggestion is the end
of that interaction. The app does not score discipline, compute completion rate,
or ask the user to prove they followed through. That boundary keeps the product
closer to a coach than an examiner.

Tone matters: sharp, but not humiliating. The app should be able to say "the
algorithm is feeding you, not challenging you" without turning the user into the
problem.

## Current Features

- Today view with a compact daily observation and quick check-in.
- Notification collection through Android Notification Listener Service.
- Usage statistics collection for app foreground time and opening patterns.
- Behavior-line MVP:
  - user check-in
  - regression pattern detection
  - anomaly suggestion generation
  - accept / postpone / reject feedback loop
- AI chat with local phone context.
- OpenAI-compatible API support, including DeepSeek-style endpoints.
- Optional LiteRT-LM local model path for on-device generation.
- History views for suggestions, patterns, trends, and linked AI discussions.
- Data export and clear-data flows.

## Tech Stack

- Android, Kotlin, minSdk 33, compileSdk 35
- Jetpack Compose + Material 3
- Navigation Compose
- Hilt for dependency injection
- Room for local persistence and schema migrations
- WorkManager for periodic background jobs
- Kotlin coroutines and Flow
- Kotlinx Serialization
- Ktor + OkHttp for OpenAI-compatible Chat Completions
- LiteRT-LM for optional on-device LLM inference
- Android Notification Listener Service
- Android UsageStatsManager

## Project Structure

```text
app/src/main/java/com/kian/khup/
├── collection/          # notification and usage collection
├── common/              # DI, workers, shared utilities
├── core/
│   ├── ai/              # local/API/hybrid LLM engines and prompt policy
│   ├── anomaly/         # regression pattern and suggestion generation
│   ├── classification/  # rule-based notification classification
│   ├── data/            # Room database, DAOs, entities, repositories
│   ├── intervention/    # foreground monitoring and intervention hooks
│   └── summary/         # hourly/daily review generation
└── output/ui/           # Compose screens and ViewModels
```

## Requirements

- Android Studio or command-line Android Gradle setup.
- JDK 17.
- Android 13+ device. The app is developed against a Xiaomi 14 / HyperOS setup,
  so permission behavior may vary on other devices.
- USB debugging enabled if installing from the command line.

## Build

```bash
./gradlew assembleDebug
```

Install the debug build:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug application id is:

```text
com.kian.khup.debug
```

## First Run

1. Open the app.
2. Go to `Settings`.
3. Grant the required Android permissions:
   - notification posting
   - notification listener access
   - usage access
   - overlay permission if testing intervention flows
4. On MIUI / HyperOS, also confirm:
   - autostart
   - unrestricted battery policy
5. Return to `Today` and use the quick check-in or the AI entry point.

## AI Setup

KHUP supports three modes:

- `Local first`: try the on-device model first, then fall back to API when configured.
- `Local only`: never call the network.
- `API only`: use the configured OpenAI-compatible API directly.

### API Mode

Open `Settings -> AI Settings -> API Config` and fill:

- `API Base URL`, for example `https://api.deepseek.com`
- `Model`, for example the model name available to your account
- `API Key`

When the API config is complete, the app switches to `API only`.

The key is stored locally through Android preferences with Android Keystore-backed
encryption. It is not committed to the repository.

The chat path uses `/chat/completions` with OpenAI-compatible request/response
format. Streaming responses are supported for the chat UI, so the assistant text
can appear progressively once the server starts returning tokens.

### Local Model

The local engine looks for a LiteRT-LM model named:

```text
gemma-4-E2B-it.litertlm
```

Candidate locations:

```text
/data/data/com.kian.khup.debug/files/models/gemma-4-E2B-it.litertlm
/storage/emulated/0/Android/data/com.kian.khup.debug/files/models/gemma-4-E2B-it.litertlm
/data/local/tmp/llm/gemma-4-E2B-it.litertlm
```

The local path is optional if you use API-only mode.

## Common Debug Commands

Enable notification listener access during development:

```bash
adb shell cmd notification allow_listener com.kian.khup.debug/com.kian.khup.collection.notification.MessageListener
```

Inspect KHUP AI logs:

```bash
adb logcat -s KHUP/AI:V
```

Inspect notification listener logs:

```bash
adb logcat -s KHUP/NLS:V
```

Read AI settings from a debug install:

```bash
adb shell run-as com.kian.khup.debug cat shared_prefs/khup.ai_settings.xml
```

## Development Notes

- Do not log full LLM prompts or full model outputs. Debug logs should only contain
  redacted summaries.
- Notification listener callbacks must stay lightweight; database work and model
  work should happen off the Binder callback path.
- Room migrations should preserve data. Current schemas are checked into
  `app/schemas/`.
- AI output used for suggestions must remain low-cost, positive-upside, and
  rejectable.
- The product should not introduce scoring, discipline grades, streak pressure,
  or completion-rate mechanics.
