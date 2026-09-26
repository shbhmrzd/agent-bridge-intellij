# Getting started with Agent Bridge

## 1. Check your environment

Use IntelliJ IDEA 2024.3.2.2–2026.2.x for the current build. Other releases are not covered by the descriptor. No Ultimate-only dependency is declared, but separate free-mode validation remains on the release checklist.

Install one of the supported CLIs using the provider’s own instructions. You do not need to install all three:

- [Claude Code setup](https://code.claude.com/docs/en/setup)
- [OpenAI Codex CLI](https://developers.openai.com/codex/cli/)
- [GitHub Copilot CLI installation](https://docs.github.com/en/copilot/how-tos/copilot-cli/set-up-copilot-cli/install-copilot-cli)

You need a provider account or configuration that permits use of its CLI. Installing Agent Bridge does not create an entitlement or bypass a provider limit.

The plugin can reuse an existing CLI login. For Claude and Copilot’s Sign in buttons, enable the bundled Terminal plugin under Settings → Plugins. Codex uses its app-server account flow directly.

The plugin ZIP contains no maintainer credentials. Each installation uses that machine’s selected CLI account/configuration, which determines access and billing. People sharing an operating-system user account may share its saved CLI login. No API key is required to build or run the automated tests.

## 2. Install the plugin

**Before the Marketplace listing is approved:** use an installable ZIP from [Releases](https://github.com/shbhmrzd/agent-bridge-intellij/releases) when available, a successful [Actions run](https://github.com/shbhmrzd/agent-bridge-intellij/actions/workflows/compatibility.yml), or a local build. Extract the Actions artifact wrapper to get the plugin ZIP; GitHub’s source-code ZIP is not an installable plugin. Then open Settings → Plugins → gear → Install Plugin from Disk. Select the ZIP itself and restart when prompted. The development artifact for this pack is `build/agent-bridge-0.9.1.zip`.

**After approval:** open Settings → Plugins → Marketplace, search for the final plugin name, verify the publisher, and install. This route is conditional: the draft pack does not imply that the listing is already live.

**Upgrading from 0.8.x:** uninstall the old preview first, then install 0.9.1. The plugin ID changed; see [migration instructions](../docs/MIGRATION.md).

## 3. Select the CLI connection

Open View → Tool Windows → Agent Bridge. Click the provider/model control below the question box and open **Provider**. Choose Claude, OpenAI Codex or GitHub Copilot.

Agent Bridge checks common executable locations. If it cannot find your CLI, use **⚙ → Settings** to enter the executable path. On macOS/Linux, `command -v claude`, `command -v codex` or `command -v copilot` can help identify it. On Windows, PowerShell’s `Get-Command claude` (or the relevant CLI name) shows command resolution; Windows runtime support still needs validation for this release.

Use the path to an installed executable, not a shell command with extra arguments. A path visible in your interactive shell may differ from the environment inherited by the IDE.

Check the selected CLI before using the plugin, for example `claude --version` (or `codex --version` / `copilot --version`). Keep the provider tools current. An outdated CLI may list a model that the service refuses to run with that client version.

## 4. Connect your account

If you are already signed into the selected CLI, start with **⚙ → Check login**. If a sign-in is required, choose **⚙ → Sign in**.

### Claude Code

The plugin starts Claude’s own browser-login command in a dedicated IntelliJ terminal. Complete the browser prompts. If a one-time code is requested, enter it in that terminal. You do not need to type the login command manually.

For organizational SSO, select **⚙ → Settings → Sign-in method → Claude account — organization SSO**, then Save and sign in. After the login process exits, the plugin checks CLI status. Process termination alone is not treated as proof of successful login.

### OpenAI Codex

The plugin asks the installed Codex app-server to start a ChatGPT login and opens the returned URL. Complete authentication in the browser. Codex handles the callback and saved credentials.

If you need a device-code flow, cancel the pending attempt, open **⚙ → Settings**, choose **ChatGPT — device code**, and use Save and sign in. Enter the displayed code only on the provider’s sign-in page. Do not include it in a screenshot or support issue.

### GitHub Copilot CLI

The plugin opens the installed Copilot CLI’s login flow in an IntelliJ terminal. Follow the prompts there. The current integration has no separate authoritative Copilot login-status check; the CLI confirms sign-in, and a subsequent request verifies usable access.

### If the integrated login is unavailable

You may sign in directly through your provider CLI and then return to IntelliJ:

```sh
claude auth login --claudeai
codex login
copilot login
```

Run only the command for your selected provider. Do not paste account tokens into the chat composer. Cancelling a login attempt does not sign you out of an existing saved account.

## 5. Choose a model

Click the compact provider/model control. Choose a model from the menu or select **Custom model…** for a provider-supported ID.

Switching after sending a message offers **Continue with context**, **Start fresh**, and **Cancel**. Continue keeps the visible chat and sends recent completed exchanges to the selected provider with your next question. Start fresh clears the chat; both choices keep your draft and attachments. Cancel leaves the current session unchanged. Handoff is limited to 12 exchanges / 48,000 text characters, with messages capped at 12,000 characters; failed/partial responses and earlier file snapshots are excluded. Fresh file context is captured on send.

- Default: use the CLI configuration with no model override.
- Claude: Sonnet, Opus and Haiku are family aliases resolved by the CLI.
- Codex: model choices come from the installed CLI’s catalog. Use **Refresh models** in the selector menu if needed.
- Copilot: choose Default, Auto, or a custom ID obtained from your CLI’s model selector.

A dropdown option does not guarantee account access. Switching starts a new native provider session; **Continue with context** preserves the visible conversation and transfers recent completed exchanges. Current account and organization rules continue to apply.

## 6. Ask about a file

Open a text source file. With its current-file checkbox enabled, the next message includes its unsaved editor contents and any selection.

Try:

> Explain what this class does and identify one edge case worth testing.

Press **Enter** or click **Send**. Use **Shift+Enter** for a new line; **Cmd/Ctrl+Enter** also sends. Type follow-up questions in the same composer. **Stop** appears while context or a response is being prepared.

## 7. Add related files

Click **+ Files**. Select files or folders in IntelliJ’s project-rooted picker. Alternatively, select items in the Project pane, right-click, and choose **Add to Agent Bridge**. Use × on an attachment to remove it from future context.

**⚙ → Find related project files** controls automatic discovery. It is on by default in the sidebar and off in selection chat. Turning it off does not remove explicit folder attachments and does not revoke the CLI’s read-tool permissions. Start a new conversation if you want to stop carrying previously sent context in the current agent session.

Automatic discovery adds at most six additional full buffers / 60,000 characters. Each full buffer is limited to 64,000 characters, with at most twelve full buffers and a 180,000-character total context budget. Oversized explicit files produce an error instead of silently sending an incomplete buffer. Scope is the project base directory; outside-project files and external content roots are not included.

## 8. Chat about selected code

Select one contiguous code range in a normal source editor. Click the blue gutter chat icon or right-click → **Chat About Selection**.

The popup is tied to the original file, even if you switch tabs. Its small footer includes the provider/model menu, gear and Send. It has no top account/settings/new-chat toolbar.

Ask about the selection. On the first message the popup expands to make room for the response. You can resize or move it. Close with Escape or ×. Closing discards that popup’s visible conversation and cancels its active request.

## 9. Review a suggested edit

Ask explicitly for a reviewable change. When **Review changes** appears, open it and inspect IntelliJ’s diff. For multi-file proposals, choose each file in the review dialog’s dropdown.

Choose **Apply change** or **Apply all changes** only after reviewing the result. Multi-file proposals are applied together; the file dropdown changes the preview and does not select a subset to apply. Edits go to editor buffers and support IntelliJ Undo; save when ready. Discard removes the pending proposal without applying it.

If a file changed after you sent the request, the plugin refuses to apply the stale proposal. Ask for a new one against the latest buffer. New files, renames and deletions are not supported in this version.

## Troubleshooting

| Symptom | What to try |
| --- | --- |
| Failed to load plugin descriptor | Select the actual plugin ZIP, not the GitHub source archive or outer Actions artifact wrapper. |
| IDE says incompatible | Check Help → About. Supported builds are 243.23654.189 through 262.*. |
| CLI executable not found | Check ⚙ → Settings and use the resolved executable path. Restart IntelliJ after changing shell installation paths if needed. |
| Login succeeds, request fails | Check provider entitlement, quota, network/proxy access and CLI version. Saved login is not proof of usable inference access. |
| Claude/Copilot Sign in cannot open | Enable IntelliJ’s bundled Terminal plugin, or authenticate directly with the CLI. |
| Codex model menu has only Default/Custom | Check login, inspect the configured Codex executable and retry Refresh models. Default/custom remain usable if discovery is unavailable. |
| Model requires a newer Codex version | Update the executable configured in Settings using its supported update/install method. Save Settings to close the old session, refresh models and retry. A separate desktop-app update may leave that CLI unchanged. |
| Model is rejected | Select Default or a model supported by the account. Agent Bridge does not unlock restricted models. |
| No selection icon | Use one selection in a normal source editor, enable gutter icons, or use Chat About Selection from the context menu. |
| No Review changes button | The reply may be illustrative code. Ask for a reviewable change to an attached existing file. Invalid proposals are not applied. |
| File changed since request | Request a fresh proposal. Your current edits are preserved. |
| Context too large | Remove attachments, narrow folder scope, or work with a smaller file. |
| Copilot does not advertise plan mode | Update the CLI. The integration refuses to proceed without its required plan-mode support; it does not silently enable writes. |
| Where did my chat go? | IDE chat history is not restored after restart. Closing inline chat discards its transcript. CLI/provider history is separate. |

When reporting a problem, include the plugin version, IDE build, OS, provider, CLI version, steps and a sanitized error message. Remove credentials, browser login URLs, device codes, private paths and proprietary source.
