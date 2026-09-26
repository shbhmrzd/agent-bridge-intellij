# Agent Bridge architecture

This document describes the 0.9.0 implementation. Agent Bridge is a native Java/Swing IntelliJ plugin with installed-CLI adapters for Claude, Codex and Copilot. It does not include a provider backend, embedding service or credential store.

## UI and lifecycle

`ChatSurface` renders the compact composer, conversation cards, provider/model picker and settings menu. `ChatPanel` coordinates project context, account state, session work and edit review. Sidebar and selection chat use the same components and behavior.

Selection chat uses `SelectionContext` and an IntelliJ range marker tied to the original document. Editor switching does not redirect an existing popup. `SelectionChatListener` debounces selection updates and offers a gutter action. Popup/project disposal cancels owned work and closes sessions.

Swing updates run on the event dispatch thread. Provider requests, model listing, authentication and context discovery have separate worker executors. Text deltas are batched for display every 50 ms; response formatting completes after generation. The display is bounded to 30 cards, not fully virtualized. Generation/response budgets limit retained text.

## Context

`ContextPacket` captures current editor buffers, selected text and an optional project inventory. Unsaved buffer text is authoritative. Each file is limited to 64,000 characters; at most 12 distinct files and 180,000 combined context characters are attached.

`ProjectContextResolver` and `ProjectContext` implement bounded lexical discovery from the project's base directory and explicitly selected folders. They prioritize relevant paths and source samples, skip ignored/generated/common credential candidates, and report partial discovery. This does not implement a full semantic index or guarantee every project file was inspected.

`IdeRead` uses cancellable non-blocking platform reads on a worker. UI choices are captured before leaving the UI thread. Context results are published only after a successful read and only if the request generation remains current. Explicit attachments are project-rooted; paths resolving outside the project are rejected.

## Provider transport

`AgentSession` normalizes text, status, completion, errors and permission requests:

| Provider | Transport | Session behavior |
| --- | --- | --- |
| Claude | CLI streaming JSON over standard input/output | The next prompt resumes the returned CLI session ID |
| Codex | App-server JSON-RPC over stdio | Starts a thread, then sends turns in that thread |
| Copilot | ACP JSON-RPC over stdio | Starts a session, requires advertised plan mode, and sends prompts |

`RpcProcess` owns framed I/O, request correlation, bidirectional RPC and pending-request cleanup. Arguments are passed as process arguments rather than interpolated shell commands. Process closure settles outstanding work; request generation checks prevent late callbacks from updating a newer chat.

The chat uses read/plan modes and declines provider write permissions. These settings do not sandbox a user's CLI hooks, managed configuration or every provider-controlled behavior. Protocol availability and model catalogs can change independently of the plugin; provider errors must remain visible.

## Conversation switching

A different provider/model starts a new native session. `ConversationHistory` carries bounded completed user/assistant exchanges when the user selects Continue with context. Opaque session IDs and tool state are never transferred between providers.

The visible conversation remains in place. Up to 12 exchanges and 48,000 text characters are handed off; each message is capped at 12,000 characters, with explicit truncation markers. The prompt encodes history as JSON data before fresh editor snapshots and the current request. Existing sessions do not receive duplicated history each turn. Fresh/Cancel semantics preserve drafts and attachments as documented in the README.

History is in memory only. A restarted session after an error, Stop or settings/login reset can receive retained completed exchanges. Failed and partial responses are not marked completed. Starting a new chat clears retained context.

## Authentication and models

`AccountAuth`, `LoginLauncher` and `CodexAuth` delegate account operations to installed CLIs. Claude/Copilot browser login starts through an optional IntelliJ Terminal integration. Codex uses CLI-managed account endpoints, with browser/device flows and correlated completion/cancellation events. Provider CLIs own credential storage and refresh.

The plugin stores only settings such as provider, executable, model ID and sign-in mode. Temporary login URLs/device codes are UI state. They are not persisted in chat history. Model discovery uses metadata endpoints without generating a coding request; CLI defaults and custom IDs remain available when discovery fails.

## Suggested edits

Responses may include structured `agent-bridge-edit` blocks. `EditProposal` validates paths, JSON, unique nonempty snippets and nonoverlapping replacements against the captured buffers. Ordinary example code is not an applicable edit.

Review uses IntelliJ's native diff components. `EditGuard` checks modification stamps, paths and contents before an undoable write command applies approved changes to writable editor buffers. Files remain unsaved. Changed/stale targets are rejected. File creation, deletion and renaming are outside the current feature set.

## Build and distribution

The build compiles against an IntelliJ SDK and packages only Agent Bridge classes/resources and the MIT license. IDE libraries are not bundled. Release bytecode targets Java 21 and uses the minimum supported Community SDK.

Compilation rejects deprecated/removal-marked calls; Plugin Verifier checks the same ZIP against multiple IDEs without muted rules. Offline tests exercise protocols using fixtures, context limits, edit validation, real document/range behavior, auth state, background reads and headless layout. Native IDE/provider behavior still requires smoke tests.

The public plugin ID `io.github.shbhmrzd.agentbridge` stays stable after publication. Generated artifacts, SDKs, private settings and logs are excluded from Git. See [release checks](docs/RELEASE-CHECKS.md) and [data handling](marketplace/DATA-HANDLING.md).
