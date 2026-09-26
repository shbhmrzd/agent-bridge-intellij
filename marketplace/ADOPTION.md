# Adoption and launch plan

## Positioning

Lead with the first useful task: ask about code in the current editor, then review a suggested change. Explain reuse of installed CLI accounts immediately afterward. Avoid a long provider feature list as the opening pitch.

Suggested headline: **Ask about code. Review changes. Stay in IntelliJ.**

Suggested one-line summary: **Code conversations and reviewable edits through the AI CLI accounts you already use.**

Use the product name alone as the listing title while name availability is being checked. Describe supported providers in the body as compatibility information, without logos or claims of affiliation.

## First-month rollout

| Phase | Work | Evidence to collect |
| --- | --- | --- |
| Before submission | Test the final ZIP with a few consenting developers on the supported IDE; fix setup failures; capture the real UI | Provider/CLI/IDE version, first-request success, reported blockers |
| Approval day | Verify the install link, publish a short demo and a single launch post in an appropriate community | Marketplace installs/downloads available in publisher tooling; support reports |
| Week 1 | Prioritize authentication, executable discovery and first-message problems | Reproducible issues and time to resolution |
| Weeks 2–4 | Improve docs from recurring questions, publish a focused maintenance release | Fewer repeated setup issues; voluntary feedback about daily use |

This is a proposed schedule, not a scheduled automation or a promise to contact anyone. Do not add telemetry merely to measure adoption. Use available publisher metrics and voluntary feedback; disclose any later analytics separately.

## 60-second demo script

- 0–8s: show the source file. “I want to understand this method without leaving the editor.”
- 8–20s: select code, open the popup and ask one focused question.
- 20–32s: show the completed answer. If generation is sped up in editing, label the footage accordingly.
- 32–48s: ask for a reviewable fix, open the real diff and explain the proposed change.
- 48–55s: apply and demonstrate Undo.
- 55–60s: show the compact provider/model menu and the actual Marketplace link. “Uses your installed CLI account; provider access and limits apply.”

Record only working behavior from the released version. Do not splice a different tool’s answer or an unsupported model into the demo.

## Launch post — use only after approval

> I built Agent Bridge for developers who want to ask about code inside IntelliJ using the Claude Code, Codex or Copilot CLI they already have.
>
> Select code to open a nearby chat, add project files, and review suggested edits in IntelliJ’s diff before applying them. The interface keeps the question and response in front, with account settings behind a gear.
>
> The plugin does not require a JetBrains AI subscription. You still need provider access, and provider usage limits apply. This release targets IntelliJ IDEA 2024.3.2.2 through 2026.2.x; it does not offer inline autocomplete or autonomous background editing.
>
> Install: [MARKETPLACE_URL]
> Setup and known limits: https://github.com/shbhmrzd/agent-bridge-intellij/blob/main/marketplace/GETTING-STARTED.md
> Feedback and reproducible issues: https://github.com/shbhmrzd/agent-bridge-intellij/issues
>
> I’d especially value feedback on installation, account setup, and your first useful question.

The code is MIT-licensed; use the public repository link once reachable. Replace all bracketed fields. Do not post this automatically or manufacture user reviews, benchmarks or adoption counts.

## Helpful support templates

**Bug report:** plugin/IDE/OS/CLI versions; provider/model; expected and actual behavior; minimal steps; sanitized error; whether a new chat reproduces it. Do not request tokens or private source.

**Feature request:** the task the user is trying to complete; current workaround; the smallest useful improvement. This keeps feedback tied to the conversational workflow.

**Voluntary feedback question:** “What was the first question Agent Bridge helped you answer, and where did setup or review feel unclear?”

For public replies, acknowledge the concrete problem, provide a tested workaround when available, and link the fix/version once released. Ask satisfied users for honest feedback, without rewards or pressure for a particular rating.
