# Compatibility validation — Agent Bridge 0.9.2

Validated on 26 September 2026. The release ZIP is compiled against Community build **IC-243.23654.189** using Java 21 bytecode.

## Binary/API matrix

The **same ZIP** passed JetBrains Plugin Verifier 1.410 on all four targets, with **no muted rules**:

| IDE | Build | Verdict |
| --- | --- | --- |
| Community 2024.3.2.2 | IC-243.23654.189 | Compatible |
| 2026.1.3 | IU-261.25134.95 | Compatible |
| 2026.2.3 | IU-262.10968.63 | Compatible |
| Published 2026.3 EAP | IU-263.5701.42 | Compatible |

No deprecated, internal, experimental or incompatible API usage was reported. The gate also rejects missing reports, compatibility warnings, invalid descriptors, improper override-only usage and non-extendable API usage. Production and test compilation treats deprecation/removal warnings as errors.

The source-controlled [verification summary](verification-0.9.2.json) records the release ZIP’s SHA-256, targets, plugin identity and results. Full local reports are under `build/verification-0.9.2/run-2flqv059/reports/`; build outputs are intentionally excluded from Git. CI uploads reports as workflow artifacts.

## Automated checks

The minimum Community SDK build passed **214 Java checks** and **5 Python gate checks**:

| Area | Checks |
| --- | --- |
| Conversation history and handoff bounds | 21 |
| Actual platform background read behavior | 5 |
| Model discovery | 11 |
| Selection/range lifecycle | 9 |
| Provider protocols and context transport | 28 |
| Context, edit validation and Swing layout | 86 |
| CLI authentication parsing/process behavior | 15 |
| Codex account protocol | 23 |
| Project retrieval | 16 |
| Verifier failure detection (Python) | 5 |

Provider tests use synthetic fixtures, with no paid model requests. The archive audit confirmed the descriptor, original SVG icon and MIT license are present, and test classes and temporary files are absent.

## Range and limits

Installation is declared from **243.23654.189 through 262.***. The minimum is 2024.3.2.2, not every 2024.3 patch. The EAP result is an early probe and does not enable installation into build 263. Intermediate branches/patches and future IDE versions are not all individually verified.

Native UI, keyboard interaction, live provider requests/login flows and Windows/Linux execution still require smoke checks. Static verification and headless component previews do not establish those behaviors. See [release checks](RELEASE-CHECKS.md).

## Publishing status

The public ID is `io.github.shbhmrzd.agentbridge`. It replaces the unpublished `dev.agentbridge.intellij` ID that required a naming exception. That exception has been removed from both the script and CI. [Preview users must migrate once](MIGRATION.md).

The public contact `shbhmrzd@gmail.com` is configured in the 0.9.2 descriptor; use it in the Marketplace vendor profile. Native smoke checks, final live gallery captures and Marketplace review remain before Marketplace publication. Binary compatibility is not Marketplace approval.
