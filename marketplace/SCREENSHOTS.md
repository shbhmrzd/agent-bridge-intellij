# Screenshots and demo capture plan

## Included assets

All four PNGs are **1280 × 800** and rendered from the production 0.9.2 Swing components. Conversations and account state are synthetic. Each slide visibly says it is a component preview, not a live IDE screenshot. No private user screenshots, personal paths, login URLs, credentials or real account identifiers are included.

| Order | File | Caption |
| --- | --- | --- |
| 1 | assets/01-sidebar-dark.png | Ask about the current file and inspect a reviewable suggestion. |
| 2 | assets/02-selection-chat.png | Start a focused conversation about selected code. |
| 3 | assets/03-model-and-settings.png | Choose the provider and model; reach account settings from the gear. |
| 4 | assets/04-sidebar-light.png | Read explanations and suggestions in a light theme. |

These can support documentation and serve as clearly labeled informational media. For the public listing’s lead screenshot, prefer a fresh live IDE capture of the released ZIP. The previews do not demonstrate actual gutter placement, native diff application or a successful provider request.

JetBrains [listing guidance](https://plugins.jetbrains.com/docs/marketplace/best-practices-for-listing.html#screenshots) recommends at least 1200 × 760 pixels and consistent aspect ratios. These assets use 1280 × 800. Keep the final images consistent in aspect ratio and readable at listing size.

## Captions for the prepared gallery

- **Ask about your current file:** “Discuss your open code and review a proposed fix. UI component preview with sample content.”
- **Chat beside selected code:** “Keep the question next to the selected method. UI component preview; editor placement is not shown.”
- **Choose your own provider:** “Select your provider and model; account settings are under the gear. Sample account state.”
- **Light theme:** “Read the same chat in a light component theme. Sample content; native IDE rendering may differ.”

Use the files in `assets/` in the listing’s Media section. They are informational slides; do not rename them as live screenshots. Supplement them with real IDE captures to demonstrate the full workflow.

## Capture real IDE screenshots

Use the supplied `demo-project/src/TaskQueue.java`, a default IntelliJ theme, and the exact release ZIP. Do not use your private working repository. Make the code and UI readable before capture rather than relying on later redaction.

1. **Sidebar hero:** open TaskQueue.java, send “Explain acknowledge and identify the missing lease check.” Capture the editor and completed answer with the compact composer visible. Avoid terminal/output panes unless relevant.
2. **Selection composer:** select the acknowledge method. Open Chat About Selection. Capture its original placement beside the highlighted code, with a short question typed but not yet sent.
3. **Selection answer:** capture a completed reply in that popup, with the original editor visible behind it. Verify the popup belongs to the selected file.
4. **Review flow:** request a reviewable expiry check, open Review changes, and capture IntelliJ’s real before/after diff with Apply visible. Do not manufacture a diff screenshot from a text mockup.
5. **Context:** open + Files in the demo project and capture the actual IntelliJ picker. Keep project names public and paths free of personal identifiers.
6. **Model choice:** open the selector with Claude aliases or actual account-supported Codex choices. Do not fabricate account access to a model.

Check every image at actual display size: correct version, readable labels, no clipped menus, no one-time code, email, private filename, OS desktop, browser window or unrelated app. Retake screenshots if the UI changes before publication.

## Suggested gallery after live captures

Use the sidebar hero first, inline chat second, native diff third, context picker fourth, and model choice fifth. The ordering follows a new user’s questions: What can I do? Where do I ask? How do I review? What context is used? Which account/model works?

## Reproducing the included previews

Build the plugin first so `build/classes` contains the production UI classes, then compile `tools/RenderPreviews.java` against those classes and the installed IDE libraries. The companion `tools/render-previews.sh` does this locally without making a provider request. Its output is component imagery, not live IntelliJ screenshots.
