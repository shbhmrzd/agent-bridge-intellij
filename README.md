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

## Install the 0.9.1 preview

Requires **IntelliJ IDEA 2024.3.2.2 through 2026.2.x** (builds `243.23654.189` through `262.*`) and at least one installed provider CLI. No Ultimate-only dependency is declared.

1. Get the installable ZIP from [GitHub Releases](https://github.com/shbhmrzd/agent-bridge-intellij/releases) when a release is available. Until then, build it below or download the `installable-plugin` artifact from a successful [compatibility workflow](https://github.com/shbhmrzd/agent-bridge-intellij/actions/workflows/compatibility.yml). Extract the Actions artifact wrapper to find the plugin ZIP; do not install GitHub’s source-code archive.
2. Open **Settings → Plugins → gear → Install Plugin from Disk**, select `agent-bridge-0.9.1.zip`, and restart if prompted.
3. Open **View → Tool Windows → Agent Bridge**, select your provider, and use **gear → Sign in** if needed.
4. Open a source file and ask: “Explain this file and identify one edge case.”

**Upgrading from 0.8.x:** uninstall the old Agent Bridge preview first. Version 0.9.0 uses the public ID `io.github.shbhmrzd.agentbridge` instead of `dev.agentbridge.intellij`. Do not keep both installed. Chat history is not persisted; finish or copy any conversation you need before restarting. See [migration details](docs/MIGRATION.md).

This preview has not been submitted to or approved by JetBrains Marketplace.

## Accounts and models

Install one provider CLI first: [Claude Code](https://code.claude.com/docs/en/setup), [OpenAI Codex](https://developers.openai.com/codex/cli/), or [GitHub Copilot CLI](https://docs.github.com/en/copilot/how-tos/copilot-cli/set-up-copilot-cli/install-copilot-cli). The plugin does not install these tools for you. Signing into a separate desktop app does not guarantee its standalone CLI is configured.

In IntelliJ’s Terminal, locate and check your chosen CLI, for example:

```sh
command -v claude
claude --version
```

Use `codex` or `copilot` instead for those providers. Copy the resolved executable path into **gear → Settings** if discovery fails. Use an executable path only, without flags or a shell command. Select the provider, use **Sign in** if needed, complete the browser/terminal prompts, and return to chat. See the [provider-by-provider setup guide](marketplace/GETTING-STARTED.md).

**Each installer uses their own local CLI account or API configuration.** The maintainer’s credentials are not bundled in the plugin. Their provider configuration determines billing and access. People using the same operating-system user account may share its saved CLI login.

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

## Build and test locally

Installing the plugin ZIP does **not** require Git, Python or a separate JDK. These tools are needed only to build from source.

Prerequisites for development:

- **Git**, **Python 3.9+**, and a **JDK with `javac` supporting Java 21**. Python 3.12+ is required only by the optional SDK downloader.
- An installed or extracted **IntelliJ IDEA SDK** in the supported range, including its `lib/` directory. The bundled Terminal plugin libraries are needed at compile time; no Ultimate subscription is required.
- A macOS or Linux shell. The current script/fixtures use Unix-style executable paths; native Windows building is not validated.
- No provider account, CLI installation or API key is needed for automated tests.

Clone the source:

```sh
git clone https://github.com/shbhmrzd/agent-bridge-intellij.git
cd agent-bridge-intellij
```

Set `IDEA_HOME` to your actual installation. For example, on macOS:

```sh
export IDEA_HOME="/Applications/IntelliJ IDEA.app/Contents"
```

On Linux, use the extracted IDE directory containing `lib/` and `product-info.json`, for example `export IDEA_HOME="/opt/idea"`. The build uses the IDE’s compiler when available; otherwise set `JAVA_HOME` to a full JDK installation with `bin/javac`. A JRE alone is insufficient.

Build and run the automated checks from the repository root:

```sh
python3 scripts/build.py --ide "$IDEA_HOME" --test
python3 -m unittest discover -s scripts/tests -v
```

Expected results: **214 Java checks**, **5 Python checks**, and `build/agent-bridge-0.9.1.zip`. Checks print their results to the terminal and return a nonzero exit status on failure. The suite uses fake provider processes and performs no paid model requests. Some tests exercise real platform documents/background reads and headless Swing layouts; they do not launch an IDE window. Layout previews are written under `build/`.

For packaging alone, omit `--test`. To keep a build separate, add `--output build/local`. No Gradle/Maven setup or Python package installation is required. SDK libraries are used for compilation and are not bundled in the plugin.

### Try your changes in IntelliJ

1. Use a test IDE profile if you want to keep your everyday plugin setup separate. The build script does not launch a sandbox IDE or install the ZIP automatically.
2. Install the generated ZIP through **Settings → Plugins → gear → Install Plugin from Disk**, then restart when prompted.
3. Open the included `marketplace/demo-project` as a directory and `src/TaskQueue.java` as a source file.
4. For a live smoke test, configure your own provider CLI/account. Ask about the current file, try selection chat, add/remove a file, and test Enter / Shift+Enter.
5. Ask for a reviewable change, inspect the diff, apply it, and Undo. Live requests use your provider account and may consume quota.
6. After further edits, rebuild and reinstall the new ZIP. There is no automatic hot reload.

Compilation rejects deprecated/removal-marked APIs. Local builds against a newer SDK are useful for development; builds intended for the full declared range must compile against the minimum Community SDK. See [compatibility evidence](docs/COMPATIBILITY.md) and [API verification commands](docs/RELEASE-CHECKS.md). Headless checks cannot replace native UI/provider smoke testing.

## Contribute

Fork the repository, create a focused branch, and submit a pull request against `main`. Explain the user-visible problem, the change, and what you tested. Include a screenshot for visible UI changes and update the relevant usage documentation. Keep generated ZIPs, classes, logs, SDKs and private account files out of commits.

The main areas are `src/main/java/dev/agentbridge/` (UI, context and providers), `src/main/resources/` (descriptor/icons), `src/test/java/` (checks), and `scripts/` (build/verification). See [CONTRIBUTING.md](CONTRIBUTING.md) and [DESIGN.md](DESIGN.md) for implementation conventions and lifecycle requirements.

## Troubleshooting

| Problem | Next step |
| --- | --- |
| Failed to load plugin descriptor | Install `agent-bridge-0.9.1.zip`, not a source-code ZIP or the outer GitHub Actions download wrapper. |
| IDE says incompatible | Check the exact build under Help → About; the minimum is `243.23654.189` and the maximum declared branch is `262.*`. |
| CLI not found or login differs from terminal | Set the resolved executable’s absolute path in gear → Settings; the IDE can inherit a different PATH. |
| Model requires a newer CLI | Update that exact CLI using its supported installer/update command, save Settings to restart the session, then refresh models. Updating a separate desktop app may not update the executable configured in the plugin. |
| Request rejected despite successful login | Check account entitlement, provider quota, model availability and network access. |
| Build cannot find javac or IDE classes | Check `JAVA_HOME`, `IDEA_HOME`, and that the IDE includes `lib/` and `plugins/terminal/lib/`. |

More account/context/edit troubleshooting is in the [setup guide](marketplace/GETTING-STARTED.md).

## Support and license

Report reproducible problems through [GitHub Issues](https://github.com/shbhmrzd/agent-bridge-intellij/issues), including plugin, IDE, CLI and model versions. Remove private code, account identifiers and login details from reports. Support is currently public through GitHub; no private support email is configured.

Maintained by [shbhmrzd](https://github.com/shbhmrzd), licensed under [MIT](LICENSE).
