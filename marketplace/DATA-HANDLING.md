# Data handling in Agent Bridge 0.9.0

Maintainer: [shbhmrzd](https://github.com/shbhmrzd). Support is currently through [GitHub Issues](https://github.com/shbhmrzd/agent-bridge-intellij/issues). This disclosure describes the plugin implementation. It is not a statement about the provider’s own retention or training policies.

## When you send a message

Agent Bridge constructs a prompt from your question, attached editor buffers, selected text, and any bounded automatic project context. Buffers may contain unsaved changes. Relative file paths and a partial project inventory can also be included.

The plugin passes that material to the installed provider CLI. That CLI may communicate with its provider or an enterprise endpoint. Its tools may read further project files. Provider contracts, settings, account type and organization policies determine subsequent processing and retention.

The context toggle governs what Agent Bridge attaches automatically. It is not a filesystem sandbox for the CLI, its hooks, managed settings or provider-controlled behavior.

When you select **Continue with context** after switching providers or models, the next question also sends recent completed user/assistant exchanges, including code in messages, to the selected provider. Transfer is limited to 12 exchanges / 48,000 text characters, with individual messages capped at 12,000 characters. Old attached-file snapshots and interrupted/failed exchanges are excluded. Start fresh clears this in-memory history; current file context may still be sent.

## Authentication

Agent Bridge delegates authentication to the installed CLI. It does not add a password/API-key/token-entry form, read saved credential files, or run its own Claude OAuth client. Codex sign-in uses the CLI’s account endpoints. Claude and Copilot sign-in use their installed binaries.

The plugin processes login status and temporary browser URLs/device codes needed to complete the flow. They are not written to chat history or plugin settings. A device code is copied only when you click its copy action. The CLI owns credential storage and refresh.

## Storage and services

The reviewed source contains no plugin-operated backend or analytics service. It stores the selected provider, model ID, executable path and sign-in mode in IDE settings. Chat cards are held in memory and are not restored by the plugin after IDE restart.

This does not mean nothing is logged or stored elsewhere: the IDE, operating system, CLI and provider can maintain their own logs, sessions and histories. Closing a popup or starting a new chat is not a provider-history deletion request.

Codex model discovery can start a short-lived CLI process to request model metadata. It sends no model prompt or coding-thread request. Authentication/status checks likewise use their provider-specific flows rather than a coding prompt.

## Applying changes

The current conversational integration uses provider read/plan modes and does not authorize direct agent writes through its permission handlers. Suggested replacements are validated and shown for explicit diff review. Applying writes editor buffers through an undoable IntelliJ command. Files are not automatically saved by the apply operation.

## Controls available to you

Choose the provider and model, disable automatic project context, remove explicit attachments, stop a request, decline an edit, or close a chat. Only send code you are authorized to share. Use provider documentation for account deletion, history retention and organization data-policy settings.

## Support

Use [GitHub Issues](https://github.com/shbhmrzd/agent-bridge-intellij/issues) for sanitized public reports. No private support email is configured. Never request authentication tokens, one-time codes, private CLI credential files or full unredacted project archives in a support issue.
