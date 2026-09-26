# Moving from the local 0.8.x preview to 0.9.0

The first shareable preview uses `io.github.shbhmrzd.agentbridge` as its permanent plugin ID. Local builds through 0.8.6 used `dev.agentbridge.intellij`, which triggered the verifier’s template-word naming rule. The new ID removes the exception from release checks.

Because IntelliJ identifies plugins by ID, it will not treat these as the same installed plugin:

1. Finish or copy any conversations you need. IDE chat history is held only in memory.
2. In Settings → Plugins → Installed, uninstall the old Agent Bridge preview.
3. Restart if IntelliJ requests it, then install `agent-bridge-0.9.0.zip` through Install Plugin from Disk.
4. Restart if requested. Open Agent Bridge, check the provider/executable/model settings, and run Check login where supported.

Do not keep both IDs installed: they register the same actions and tool-window name. Existing CLI credentials remain owned by the provider CLI and are not deleted or migrated by Agent Bridge. The plugin uses the same IDE settings keys, but verify settings after the installation rather than relying on them being retained by an uninstall.

Keep the new public plugin ID stable in all subsequent releases.
