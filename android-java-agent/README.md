# Java Agent — native Android AI agent

Java Agent is a small, native Java Android application with an Emergent-inspired idea/chat workspace and a real local plugin system. It works immediately with offline tools and can use a user's own OpenAI-compatible Chat Completions provider for open-ended AI conversation and automatic tool calling.

**APK:** [`java-agent-debug.apk`](java-agent-debug.apk)  
**Package:** `com.javaagent`  
**Android:** 6.0 / API 23 and newer; target API 35

## Included

- Native Android UI; no WebView wrapper, AndroidX, analytics, ads, or runtime dependencies.
- Agent, Plugins and Settings workspaces.
- Bounded OpenAI-compatible agent loop: at most 6 model/tool rounds per request.
- Modular `AgentPlugin` contract, JSON tool schemas, opt-in toggles and centralized dispatch.
- Six local plugins:
  - safe arithmetic calculator (no `eval` or script engine);
  - date/time with IANA timezones;
  - private notes;
  - local task list;
  - word/character/case/trim text tools;
  - non-sensitive device, battery and memory status.
- Offline command routing for calculations, notes, tasks, time, text and device questions.
- App-private, bounded chat history with atomic replacement.
- API keys encrypted using AES-GCM and an app-only Android Keystore key.
- HTTPS required, except `localhost`, `127.0.0.1` and Android emulator host `10.0.2.2` for a user's own local model server.
- No credentials from the analyzed repositories and no Emergent proprietary code or branding.

## Install

Transfer `java-agent-debug.apk` to an Android device, allow installation from the chosen source when Android prompts, and open **Java Agent**. With ADB:

```bash
adb install -r java-agent-debug.apk
```

The APK is **debug-signed** for testing. It is not a Play Store release build. Sign a separate release build with your own protected release key before distribution.

## Use offline

No setup is required for local commands. Examples:

```text
Calculate 18% of 250
sqrt(81) + max(4, 9)
Save a note: call Sam tomorrow
Show my notes
Add task: finish the project
Show my tasks
Word count hello from Java Agent
What is the time?
Device status
```

Open-ended conversation requires a provider. Without one, Java Agent explains the available offline actions instead of pretending to be an online model.

## Configure an AI provider

Open **Settings** and enter:

| Field | Example |
| --- | --- |
| API base URL | `https://api.openai.com/v1` |
| Model | A model supported by your provider |
| API key | Your own key; leave blank only if your server does not require one |

The app appends `/chat/completions` to the base URL. A full URL already ending in `/chat/completions` is also accepted. The selected provider/model must support OpenAI-style function tools for automatic plugin use; plain chat remains provider-dependent.

For an OpenAI-compatible local server on the same computer as an Android emulator, a typical base URL is:

```text
http://10.0.2.2:11434/v1
```

`10.0.2.2` is emulator-specific. On a physical device, use a properly secured HTTPS endpoint; arbitrary LAN cleartext HTTP is intentionally not enabled.

Configuration is used only when a message is sent. The Settings screen does not silently spend API credits on a test request.

## Plugin SDK

Every capability implements `src/main/java/com/javaagent/plugin/AgentPlugin.java`:

```java
public interface AgentPlugin {
    String getId();                    // stable snake_case tool name
    String getName();                  // user-visible label
    String getDescription();           // model-facing usage guidance
    JSONObject getParametersSchema();  // JSON Schema for model arguments
    String execute(Context context, JSONObject arguments) throws Exception;
}
```

Register a validated implementation in `PluginRegistry`. IDs must match `[a-z][a-z0-9_]{2,39}` and be unique. The registry alone owns enablement, tool-schema creation and dispatch. Unknown or disabled plugins fail closed.

Plugin implementations must:

1. Treat all model arguments as untrusted and validate them.
2. Bound input, output, storage, time and iterations.
3. Avoid UI-thread work; execution occurs on the agent worker.
4. Avoid arbitrary shell/code execution and unnecessary Android permissions.
5. Return a concise factual result and never claim an unperformed side effect.

This version uses compile-time Java plugins, not untrusted dynamic DEX loading. That is a deliberate security boundary.

## Architecture

```text
MainActivity (native UI)
        |
        v
AgentEngine ---- offline router ----+
        |                           |
        v                           v
OpenAiClient                  PluginRegistry
        |                           |
        v                           +-- Calculator
Chat Completions API                +-- Clock
                                    +-- Notes
SecureSettings (Keystore)            +-- Tasks
ChatStore (private JSON)             +-- Text tools
                                    +-- Device info
```

Internal tool messages are request-scoped. Only user-visible user/assistant messages are persisted.

## Build without Gradle

Linux x86_64 automatic setup uses pinned npm packages containing the Java compiler/runtime, Android platform JAR, AAPT2, D8 and APK signer. Build intermediates and the toolchain are ignored by Git.

```bash
cd android-java-agent
bash scripts/setup-toolchain.sh
bash scripts/test.sh
bash scripts/build.sh
```

Requirements for setup/build: Bash, npm, Python 3, tar and standard Unix tools. On another platform, install Java 17 and set `JAVA_HOME`, then provide `AAPT2`, `ANDROID_JAR`, `D8_JAR`, `APKSIGNER_JAR` and `DEBUG_KEYSTORE` as environment variables before running `build.sh`.

The build performs resource compilation/linking, Java compilation, D8 DEX generation, 4-byte alignment of uncompressed payloads, debug signing, signature verification and APK badging inspection.

## Validation performed

- `ExpressionEvaluatorTest`: 16 host-side arithmetic and rejection checks.
- Full Java compilation and D8 conversion.
- APK ZIP integrity check.
- v1, v2 and v3 signature verification with one signer.
- Manifest/badging inspection for package, label, launcher activity, permission and SDK levels.
- Uncompressed `resources.arsc` and `classes.dex` alignment checks.
- Secret/TODO/shell-execution source scan.

No physical Android device or emulator was available in the build environment, so UI/runtime and real-provider behavior still need on-device verification.

## Scope

This is an original Java Agent application inspired by the public idea-first interaction pattern of Emergent. It is **not** a copy of Emergent's private backend, hosted build infrastructure, account system or full no-code deployment platform. Repository findings and security decisions are documented in [REFERENCE_ANALYSIS.md](REFERENCE_ANALYSIS.md).
