# Agent Bridge

Ask about your code, discuss a selected method, and review suggested changes inside IntelliJ IDEA using your existing **Claude Code, OpenAI Codex or GitHub Copilot CLI account**.

Agent Bridge is a free, MIT-licensed plugin. It requires no JetBrains AI subscription. Provider access, subscriptions and usage limits still apply. This is an independent integration, not an official provider or JetBrains product.

![Agent Bridge sidebar component preview](marketplace/assets/01-sidebar-dark.png)

*Production UI components with synthetic conversation data. This is a component preview, not a live IDE screenshot.*

## What you can do

- Ask about the current editor file, including unsaved changes.
- Select code and open a chat beside it from the gutter icon or **Chat About Selection** action.
- Add files and folders through IntelliJ’s project picker or **Add to Agent Bridge** in the Project pane.
- Discover related project files with bounded text-based retrieval.
- Choose a provider and model from one compact menu; manage accounts through the gear.
- Keep the conversation when switching providers/models, or choose **Start fresh**.
- Review supported changes in IntelliJ’s diff viewer, apply them to editor buffers, and Undo.
- Press **Enter** to send and **Shift+Enter** for a new line. Cmd/Ctrl+Enter also sends.

## Install the 0.9.0 preview

Requires **IntelliJ IDEA 2024.3.2.2 through 2026.2.x** (builds `243.23654.189` through `262.*`) and at least one installed provider CLI. No Ultimate-only dependency is declared.

1. Get the installable ZIP from [GitHub Releases](https://github.com/shbhmrzd/agent-bridge-intellij/releases) when a release is available. Until then, build it below or download the `installable-plugin` artifact from a successful [compatibility workflow](https://github.com/shbhmrzd/agent-bridge-intellij/actions/workflows/compatibility.yml). Extract the Actions artifact wrapper to find the plugin ZIP; do not install GitHub’s source-code archive.
2. Open **Settings → Plugins → gear → Install Plugin from Disk**, select `agent-bridge-0.9.0.zip`, and restart if prompted.
3. Open **View → Tool Windows → Agent Bridge**, select your provider, and use **gear → Sign in** if needed.
4. Open a source file and ask: “Explain this file and identify one edge case.”

**Upgrading from 0.8.x:** uninstall the old Agent Bridge preview first. Version 0.9.0 uses the public ID `io.github.shbhmrzd.agentbridge` instead of `dev.agentbridge.intellij`. Do not keep both installed. Chat history is not persisted; finish or copy any conversation you need before restarting. See [migration details](docs/MIGRATION.md).

This preview has not been submitted to or approved by JetBrains Marketplace.

## Accounts and models

Install and authenticate the provider CLI separately, or use the plugin’s Sign in button after installation. If the command cannot be found, set its absolute path under **gear → Settings**.

| Provider | Connection | Model selection |
| --- | --- | --- |
| Claude Code | Installed CLI, streaming output; browser login through an IDE terminal | CLI default, Sonnet, Opus, Haiku, or a custom ID |
| OpenAI Codex | Installed CLI app-server; CLI-managed browser/device login | Models returned by the CLI, default, or custom ID |
| GitHub Copilot | Installed CLI’s ACP plan mode; terminal login | CLI default, Auto, or custom ID |

Claude/Copilot button-based sign-in uses IntelliJ’s bundled Terminal plugin. An existing CLI login can be reused. A model appearing in a list does not guarantee access or compatibility with an outdated CLI.

The plugin does not collect account passwords or read saved credential files. Provider CLIs own authentication and credential storage. Code sent as context is processed through the chosen CLI/provider; read the [data-handling disclosure](marketplace/DATA-HANDLING.md).

## Conversations and context

Switching provider or model after sending a message offers **Continue with context**, **Start fresh**, and **Cancel**. Continue keeps visible messages and sends recent completed exchanges to the new provider on the next question. Start fresh clears the conversation. Both keep your draft and attachments; Cancel leaves the current session intact.

Handoff is limited to 12 completed exchanges / 48,000 text characters, with individual messages capped at 12,000 characters. Failed or interrupted responses, old file snapshots and CLI tool state are excluded. New editor snapshots take precedence over historical code. Chats are separate between sidebar and selection popups and are not restored after restart.

Automatic context is bounded retrieval, not a complete repository upload or semantic index. The plugin attaches at most 12 files within its context budget. The CLI’s read tools may inspect additional files under its own policies. Disable automatic retrieval under **gear → Find related project files**, or choose files explicitly.

## Review changes

Ask for a reviewable change, then choose **Review changes** when a supported proposal appears. Inspect the diff and apply it. Modified buffers remain unsaved and support Undo. If a target changed after the request, the plugin rejects the stale suggestion.

Current scope: existing attached text files only. New files, deletes, renames, ghost-text completion and autonomous background editing are not supported. Read [the setup guide](marketplace/GETTING-STARTED.md) for the full workflow and troubleshooting.

## Build and verify

Use **JDK 21+**, Python 3, and an installed or extracted IntelliJ SDK:

```sh
python3 scripts/build.py --ide /path/to/idea --test
python3 -m unittest discover -s scripts/tests -v
```

On macOS, `--ide` can point to an IntelliJ `.app` or its `Contents` directory. Set `JAVA_HOME` when the IDE runtime lacks a compiler. The output is `build/agent-bridge-0.9.0.zip`. Builds compile against the selected SDK’s libraries without packaging those libraries. Python 3.12+ is needed only for the optional SDK downloader.

Release artifacts are compiled against the minimum Community SDK. Compilation rejects deprecated/removal-marked APIs, and Plugin Verifier checks the same ZIP against four IDE builds. Tests use fake provider CLIs and make no paid model requests. See [compatibility evidence](docs/COMPATIBILITY.md), [release checks](docs/RELEASE-CHECKS.md), and [contribution instructions](CONTRIBUTING.md). Native IDE/provider smoke tests remain necessary.

## Support and publishing

Report reproducible problems through [GitHub Issues](https://github.com/shbhmrzd/agent-bridge-intellij/issues), including plugin, IDE, CLI and model versions. Remove private code, account identifiers and login details from reports. Support is currently public through GitHub; no private support email is configured.

See the [Marketplace preparation pack](marketplace/README.md) for listing text, media, setup instructions and remaining submission steps. Maintained by [shbhmrzd](https://github.com/shbhmrzd), licensed under [MIT](LICENSE).
