# Changelog

## 0.10.0 - 2026-11-23

- Save conversations locally per project, with resume, rename and delete controls in both chat surfaces.
- Preserve completed and interrupted exchanges across popup closure and IDE restart; resume completed context with fresh editor buffers.
- Keep saved transcripts outside project files, write them atomically, and report storage failures without overwriting unreadable data.
- Prevent concurrent edits to the same saved conversation and stale completions from restoring deleted chats.
- Document storage, retention limits, and the difference between local deletion and provider history.

## 0.9.8 - 2026-11-14

- Honor native Swing text smoothing for the question placeholder in both chat views.
- Verify placeholder rendering with light/dark themes at 100%, 150% and 200% scale.

## 0.9.4 - 2026-09-30

- Keep exact Codex version and variant IDs visible when CLI catalog labels are generic or ambiguous.
- Add documented Copilot choices for Claude, GPT/Codex and Gemini, using Copilot-specific IDs.
- Retain Default, Auto, custom IDs and account/CLI availability guidance. Codex still loads its catalog from the CLI.

## 0.9.3 - 2026-09-30

- Render tilde and backtick code fences as shaded monospace blocks, including indented and longer fences.
- Format streamed responses at a bounded refresh rate; keep structured edit payloads hidden.
- Add explicitly versioned Claude choices and label family aliases separately. Show exact model IDs in tooltips.
- Versioned choices are documented provider IDs, not an account-specific availability list. Custom IDs remain supported.

## 0.9.2 — 2026-09-26

- Add the public support email to plugin metadata and support documentation.
- Refresh the packaged listing description, clarify multi-file review, and include setup/support links.
- No changes to chat, authentication or editing behavior.

## 0.9.1 — 2026-09-26

- Place the attachment × directly after each file/folder name, without surrounding brackets or borders.
- Retain hover feedback and provide a clear removal tooltip and accessible label.

## 0.9.0 — 2026-09-26

First shareable preview with the MIT license and public plugin ID `io.github.shbhmrzd.agentbridge`.

- Chat with Claude, Codex or Copilot through existing CLI accounts.
- Include current editor buffers, selected code, project files and folders.
- Open selection chat beside the code and review supported changes in native diffs.
- Continue with context or start fresh when switching providers/models.
- Enter sends; Shift+Enter inserts a new line; Cmd/Ctrl+Enter remains supported.
- Compact model/account controls, hover feedback and an original plugin icon.
- Community 2024.3.2.2 through 2026.2.x declared support, strict compatibility CI, and recorded release verification.
- Public setup, data-handling and contribution docs, component-preview media and issue templates.

Users of local 0.8.x previews must uninstall the old plugin first; see [migration instructions](docs/MIGRATION.md). Provider access and usage limits still apply. Marketplace submission is pending.
