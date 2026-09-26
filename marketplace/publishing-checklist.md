# Publishing Agent Bridge 0.9.1

## Prepared

- Public ID: `io.github.shbhmrzd.agentbridge`; old-preview [migration instructions](../docs/MIGRATION.md).
- Publisher: `shbhmrzd`; website/source: https://github.com/shbhmrzd/agent-bridge-intellij.
- MIT license in the repository and packaged JAR.
- Description, change notes, setup instructions, data disclosure and an original SVG logo.
- Labeled component-preview media generated from the current UI with synthetic content.
- Minimum Community SDK build, automated checks and strict cross-version Plugin Verifier workflow with no muted rules. See [release evidence](../docs/COMPATIBILITY.md).
- GitHub bug/feature templates, contribution instructions, and generated-file exclusions.

## Still needed before Marketplace submission

- Supply a monitored **public support email** in the vendor profile. Add it to the descriptor as well if you want it displayed as IDE contact metadata, then rebuild and verify that ZIP. Current support is GitHub-only. [JetBrains requires a valid vendor website and email](https://plugins.jetbrains.com/docs/marketplace/jetbrains-marketplace-approval-guidelines.html).
- Check final name/ID availability through the submission flow; the verifier does not reserve either.
- Run the [native smoke checklist](../docs/RELEASE-CHECKS.md) against the exact release ZIP. Record IDE, OS and provider CLI versions. Include real Codex and Copilot requests and login flows; fake transport tests are not live-provider coverage.
- Capture actual final-IDE gallery images using the synthetic demo project. Existing component previews are labeled and must not be presented as live screenshots.
- Confirm the repository, source/license and support links are publicly reachable.
- Accept the Marketplace developer agreement, select the appropriate vendor/trader details, and complete the portal’s license/privacy fields.

## Upload

1. Create or select your JetBrains Marketplace vendor profile.
2. Rebuild and verify after final contact/metadata changes; use the minimum SDK for the release binary.
3. Choose Upload plugin and upload **the plugin ZIP**, not the GitHub source archive or a documentation pack.
4. Use `description.html`, `getting-started.html` and `change-notes.html`; supply source, issues, documentation, license and data-disclosure links.
5. Add accurate media captions and submit for review. Static verification is not Marketplace approval.
6. After approval, test installation from the public listing and use that verified link in announcements.

[Official upload workflow](https://plugins.jetbrains.com/docs/marketplace/uploading-a-new-plugin.html).

## Claims

Describe the plugin as an independent integration using installed CLIs and existing eligible accounts, with reviewable edits and no JetBrains AI subscription requirement. State the declared IDE range and known limits.

Do not claim official affiliation, unlimited free AI, complete repository understanding, untested OS/provider coverage or Marketplace approval before it exists. MIT licensing does not grant provider access.
