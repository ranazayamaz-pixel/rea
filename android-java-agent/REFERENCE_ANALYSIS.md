# Reference and API analysis

Review date: 2026-11-09. The listed repositories were shallow-cloned into the Git-ignored `_reference/` directory and inspected as source. This report records what was actually observed; it does not claim that every external project's complete test suite was run.

## Public Emergent surface

The public `https://app.emergent.sh/` landing content presented an idea prompt (“Describe your idea”), AI app-building value propositions, sign-in choices and visual application examples. Java Agent adopts only the general idea-first chat/workspace pattern. It does not reuse Emergent logos, remote assets, account flows, private endpoints or proprietary implementation.

## Repository inventory

| Repository | Inspected revision | Findings |
| --- | --- | --- |
| `shivamdada1/alternativeto.net` | `8ce2df38` | Small React frontend and FastAPI/MongoDB status API; no agent/plugin runtime. Runtime backend/DB configuration is environment-based. |
| `nautiyal1992/https-app.emergent.sh-chat` | `d985c113` | README-only pointer to Emergent chat; no application source. |
| `charishmadubba-pixel/Define-your-style` | `c9a5715f` | README-only pointer; no application source. |
| `ABHI769/TRANSLATION` | `a58c0766` | React translation UI plus FastAPI document/OCR/translation backend using `emergentintegrations.llm.chat.LlmChat` and Gemini model identifiers. |
| `hamdanarhan1-ai/Friday-` | `eb2ee443` | No substantive source beyond repository metadata/README. |
| `eddyaraujo/emergent_test_number_one` | `3e4ce057` | README-only experiment description. |
| `supuni9622/Netflix-Clone` | `09d3b6bb` | README discussion of prompt/context engineering; no Netflix application source in the inspected revision. |
| `Gogreat-gg/gogreat-websites-New` | `3e309faa` | React marketing/site frontend and small Express/Mongoose backend; no AI agent loop. |
| `Gustavo-de-Lima-G-000-Akiko-Yuuuki/Hacked.emergent.IA` | `5816d9da` | README instructions about exporting project archives; no reusable agent source. No instructions from it were executed. |
| `AFK-surf/open-agent` | `1a85d765` | Large TypeScript/NestJS application with provider abstraction/factory, model capabilities, chat sessions, transcripts, workflows and named tool factories. |
| `Fosowl/agenticSeek` | `4f5f924c` | Python local-first multi-agent system with provider routing, memory, abstract tools, browser/code/file agents, workspace safety and experimental MCP integration. |

Six repositories were therefore placeholders/documentation-only. The five substantive projects were used for architecture and risk analysis, not bulk-copied into the APK. Open-Agent is Apache-2.0 and AgenticSeek is GPL-3.0; the new Android implementation is original and does not combine their source code.

## Patterns extracted and adapted

### Open-Agent

Observed in `plugins/copilot/providers`, `tools`, `workflow`, `session.ts` and `transcript/`:

- A common provider interface hides provider-specific implementations.
- A factory registers configured providers and removes unconfigured ones.
- Model capabilities are checked separately from provider configuration.
- Tools are named, schema-backed factories selected per chat request.
- Streaming/workflow state is separated from the UI.
- Provider execution has a defined maximum step count.

Android adaptation: a smaller `OpenAiClient`, `PluginRegistry`, JSON schemas, six-round limit, explicit unknown/disabled-tool errors and user-visible status updates. Desktop/browser, code sandbox and multi-provider services were not forced into a mobile APK.

### AgenticSeek

Observed in `sources/agents`, `sources/tools`, `llm_provider.py`, `router.py` and `memory.py`:

- Local-first/provider-optional operation.
- Agent selection and planning separated from tool implementations.
- Tools expose execution and failure feedback contracts.
- Workspace path handling and safe-mode concepts limit side effects.
- Memory/session recovery is distinct from the provider.
- The MCP agent is explicitly marked experimental in its own source.

Android adaptation: useful offline behavior without a server, bounded private storage, centralized tool results and no arbitrary shell, interpreter, browser automation or dynamic code loading.

### Emergent-generated projects

Observed patterns include React frontends, `/api` backend prefixes, environment-provided backend URLs, MongoDB persistence and an LLM wrapper in the translation backend. The APK uses the standard OpenAI-compatible HTTP contract directly instead of depending on the Emergent-specific Python wrapper.

## API integration inventory

Only public integration patterns/endpoints were recorded. No committed credential, database connection string, preview token or Emergent key was copied or tested.

| Pattern | Observed use | Java Agent decision |
| --- | --- | --- |
| OpenAI-compatible `/v1/chat/completions` | Supported by Open-Agent/provider documentation and AgenticSeek local/remote providers | Direct configurable HTTPS client; default base `https://api.openai.com/v1` |
| Gemini via `emergentintegrations` | Translation/OCR backend | Not copied; requires a separate SDK/service and is unnecessary for the generic client |
| Ollama/LM Studio-style local servers | AgenticSeek local-provider configuration | Allowed only on explicit loopback/emulator cleartext hosts; HTTPS otherwise |
| MongoDB URLs | Status/translation/marketing backends | Excluded from the APK; chat/plugins remain app-private |
| Emergent preview backend URLs | Frontend environment configuration | Excluded; they are deployment-specific, not a reusable agent API |
| MCP finder/browser/code execution tools | AgenticSeek/Open-Agent | Excluded from this version to keep the mobile permission and side-effect boundary small |

## Risks found in references and corresponding app fixes

These are source-review findings, not claims of exploited or runtime-reproduced vulnerabilities.

1. **Environment files can contain deployment-specific connection material.**  
   App response: no reference `.env` value is included; the user enters their own API key and it is Keystore-encrypted.

2. **The translation upload path reads complete files before parsing and does not show a request-size bound at that boundary.**  
   App response: provider responses, prompt size, text-plugin input, notes, tasks, history and tool results all have explicit bounds.

3. **Generic exception details can cross backend/API boundaries.**  
   App response: provider errors are compacted and length-limited; Java stack traces and authorization headers are never rendered.

4. **Agent/tool loops can run indefinitely without a budget.**  
   App response: six rounds maximum, one worker, bounded tool output and explicit step-limit failure.

5. **Unrestricted code/shell plugins create a large mobile side-effect surface.**  
   App response: no shell, `eval`, dynamic DEX loading, accessibility control, contacts, location, microphone or storage permissions.

6. **Malformed provider output can crash naive clients.**  
   App response: bounded reads, checked JSON structure, no-choice/no-message errors, empty-response handling and per-tool error feedback.

7. **Calculator `eval` would execute more than arithmetic.**  
   App response: recursive-descent parser with validated numbers/functions, division-by-zero and non-finite-result rejection.

8. **Cleartext provider traffic can expose prompts and keys.**  
   App response: default-deny cleartext configuration with only three named local hosts exempted.

9. **Unbounded persistent chat creates privacy and resource problems.**  
   App response: at most 80 durable messages and 512 KiB history, private notes/task limits and backup disabled.

## Verification limits

- External repositories were analyzed statically; their language-specific dependencies and full test suites were not installed/run.
- No third-party API key was used and no paid API request was made.
- The APK itself was compiled, DEXed, aligned and signature-verified. On-device UI/runtime and real-provider tests remain outstanding because no device/emulator was available.
