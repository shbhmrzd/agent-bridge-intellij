# Compatibility and API release checks

Agent Bridge must not depend on deprecated, removal-marked, internal or experimental IntelliJ APIs. Compatibility is demonstrated for specific builds; no plugin can guarantee that an unreleased IDE will never change its APIs.

## Enforced checks

1. `scripts/build.py` compiles production and test code with `-Xlint:deprecation,removal -Werror`. A deprecated SDK call fails the build. Release binaries are compiled against the minimum supported Community SDK, with Java 21 bytecode.
2. `scripts/verify_compatibility.py` runs the official Plugin Verifier against the **same binary** for every supplied IDE. It requires a fresh, clean `Compatible` verdict for each requested build and rejects deprecation, internal API, experimental API, compatibility, override-only, and non-extendable API reports. A zero tool exit code alone is insufficient; missing reports fail.
3. The GitHub workflow builds once and checks the minimum supported SDK, a pinned previous release, the current stable release, and the latest published EAP. Stable/EAP targets are resolved from JetBrains' release feed at run time. Every verification failure is blocking; no target uses `continue-on-error`.
4. Headless checks cover provider protocols, context/edit validation, document range behavior, UI layout, authentication state and model discovery. New read-action checks cover the actual platform non-blocking read implementation, exception propagation, interruption, project disposal and rejecting UI-thread reads.
5. A clean IDE smoke test is still required for native UI, sign-in, streaming, context selection, diff review, Apply, Undo and cancellation. Static compatibility cannot establish all runtime behavior.

The workflow is configured for pushes, pull requests and manual dispatch. It runs after repository pushes; inspect the Actions result for each release. It is not a scheduled monitor and does not promise automatic checks when JetBrains publishes a new IDE without a repository event. Use a required status check/branch rule in GitHub before merging release changes.

## Local commands

```sh
# Compile against the minimum supported SDK and run the headless suite.
python3 scripts/build.py --ide /path/to/idea-IC-243.23654.189 --test
python3 -m unittest discover -s scripts/tests -v

# Check exactly that archive across installed/extracted SDKs.
python3 scripts/verify_compatibility.py \
  --plugin build/agent-bridge-0.9.1.zip \
  --verifier /path/to/verifier-cli-1.410-all.jar \
  --ide /path/to/idea-IC-243.23654.189 \
  --ide /path/to/current-idea \
  --ide /path/to/upcoming-idea-eap \
  --runtime /path/to/compatible-local-jdk \
  --reports build/verification
```

No verifier checks are muted for the public plugin ID. Do not add naming or API exceptions to the release workflow.

The verifier is pinned to 1.410 for reproducibility. Review and update the verifier and workflow dependencies periodically. Keep the minimum SDK pinned; expand the supported upper build range only after validation. An EAP probe passing does not automatically enable installation into every future EAP build.

`scripts/fetch_ide.py` requires Python 3.12+, downloads the official Linux SDK selected from JetBrains metadata, validates its published SHA-256, and safely extracts it. It does not install or launch an IDE. Local macOS builds need a macOS JDK even when using an extracted Linux SDK's classes.

## Why the read implementation changed

JetBrains deprecated blocking `ReadAction.compute/run` because background reads can delay editor writes. Context preparation now runs on a background worker using `ReadAction.nonBlocking(...).executeSynchronously()`, expires with the project, and observes interruption. UI choices are captured on the UI thread; file text is read under a cancellable read action. Restartable actions produce results before updating the traversal state, so a retry does not duplicate context. This follows JetBrains' [2026 API migration guidance](https://plugins.jetbrains.com/docs/intellij/api-notable-list-2026.html).

A small Swing thread check rejects accidental UI-thread usage without relying on IntelliJ's experimental assertion API. No reflective workaround is used to hide deprecated calls.

## Conversation-switch smoke test

Run in both sidebar and selection chat:

- Send a question mentioning a distinctive fact, then switch the model and choose Continue with context. Verify existing cards, draft and attachments remain. Ask a follow-up referring to the fact.
- Switch to a different provider and continue. Confirm the next response uses prior exchanges and the latest unsaved editor buffer. Subsequent questions should continue in the new native session.
- Switch twice without sending; the next question should still receive the prior conversation.
- Choose Cancel (and separately dismiss the dialog). Verify the selector returns to its original value and follow-ups retain the existing session.
- Choose Start fresh. Verify messages disappear, draft and attachments remain, and the next request has no prior conversation. Test New chat separately.
- Stop a partial response, then switch and continue. The partial response remains visible but must not be handed off as completed. Repeat after a failed request.

Automated checks cover history bounds, escaping, cancellation/fresh/continue state, retry snapshots and all three providers' prompt transport using fake CLIs. They do not exercise native switch-dialog interaction or live model recall.

## Final native IDE smoke checks

- Install into a clean supported IDE; verify preview-ID migration separately.
- Test Enter to send, Shift+Enter for a new line, Cmd/Ctrl+Enter, empty messages, and sending while busy in both chat surfaces.
- Check narrow/wide sidebar, light/dark themes, selection icon/popup focus, gear/menu hover and project picker behavior.
- Verify saved login, browser login/cancel, Codex device flow and real provider/model requests. Record the CLI versions.
- Attach an unsaved file, request an edit, review, apply and Undo. Reject an obsolete proposal after changing its buffer.
- Close the popup/project during a request; confirm owned provider processes exit.

Before Marketplace submission, add the public vendor email and verify name/ID availability. Rebuild after metadata changes. Keep the MIT license in the packaged JAR and inspect the ZIP for test classes, credentials and private files.
