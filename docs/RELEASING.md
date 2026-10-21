# Releasing Agent Bridge

The [IDE compatibility workflow](../.github/workflows/compatibility.yml) builds the plugin against the minimum supported IntelliJ Community SDK, runs the Java and Python checks, and verifies the same ZIP against supported IDE builds. The upcoming EAP check is informative because it can be outside the declared compatibility range.

## One-time setup

1. In your JetBrains Marketplace profile, open **My Tokens** and create a permanent token with permission to update Agent Bridge. JetBrains documents this in its [plugin upload API](https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html).
2. In the GitHub repository, open **Settings → Secrets and variables → Actions → New repository secret**. Name it `JETBRAINS_MARKETPLACE_TOKEN` and paste the token as its value. Never add the token to the repository or workflow file.
3. Confirm the Marketplace listing belongs to your vendor account and its numeric plugin ID is `34567`.

## Publish an update

1. Change `<version>` and add release notes in `src/main/resources/META-INF/plugin.xml`. Use a new version for each Marketplace upload. Run a native IDE smoke check on that version when behavior changes.
2. Push the change to `main`. CI builds and verifies it. If all required checks pass, the `publish` job uploads the exact tested ZIP to JetBrains Marketplace automatically. Pull requests and pushes without a version change never publish.
3. Watch the GitHub Actions run and the Marketplace **Versions** page. A successful upload means JetBrains received the update; [JetBrains still reviews it](https://plugins.jetbrains.com/docs/marketplace/plugin-updates.html) before it becomes available to users.

To publish the current committed version after adding the secret, open **Actions → IDE compatibility → Run workflow**, choose `main`, and check **Upload this version**. Only use this if that version has not already been uploaded; JetBrains does not accept duplicate version uploads. This manual run goes through the same build and verification jobs.

If the upload fails because the token is missing or invalid, fix the GitHub secret and use the manual run above. Do not bump the version merely to retry a failed upload.
