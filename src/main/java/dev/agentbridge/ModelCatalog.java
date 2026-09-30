package dev.agentbridge;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Provider model IDs stay distinct from UI labels; listing never sends a prompt. */
final class ModelCatalog {
    record Option(String id, String label) { @Override public String toString() { return label; } }
    static final Option DEFAULT = new Option("", "CLI default");
    static final Option CUSTOM = new Option("\u0000", "Custom model…");
    static List<Option> defaults(AgentSession.Provider provider) {
        return switch (provider) {
            // Documented Anthropic IDs, checked 2026-09-30. These are choices, not an account entitlement list.
            // https://code.claude.com/docs/en/model-config
            // https://platform.claude.com/docs/en/models/overview
            case Claude -> List.of(DEFAULT,
                new Option("sonnet", "Sonnet (CLI alias)"), new Option("opus", "Opus (CLI alias)"), new Option("haiku", "Haiku (CLI alias)"),
                new Option("claude-opus-5-5", "Opus 5.5"), new Option("claude-opus-5", "Opus 5"), new Option("claude-opus-4-6", "Opus 4.6"),
                new Option("claude-sonnet-5-5", "Sonnet 5.5"), new Option("claude-sonnet-5", "Sonnet 5"), new Option("claude-sonnet-4-6", "Sonnet 4.6"),
                new Option("claude-haiku-4-5-20251001", "Haiku 4.5"));
            case Copilot -> List.of(DEFAULT, new Option("auto", "Auto (Copilot chooses)"));
            case Codex -> List.of(DEFAULT);
        };
    }
    static String validate(String value) {
        String id = value.trim();
        if (id.length() > 200 || id.startsWith("-") || id.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c)))
            throw new IllegalArgumentException("Enter a model ID without spaces or control characters (up to 200 characters).");
        return id;
    }
    static List<Option> choices(List<Option> catalog, String selected) {
        LinkedHashMap<String, Option> unique = new LinkedHashMap<>(); unique.put("", DEFAULT);
        for (Option option : catalog) if (!option.equals(CUSTOM)) unique.putIfAbsent(option.id(), option);
        if (!selected.isBlank()) unique.putIfAbsent(selected, new Option(selected, selected));
        List<Option> options = new ArrayList<>(unique.values()); options.add(CUSTOM); return List.copyOf(options);
    }
    static List<Option> codex(String executable, Path cwd) throws Exception {
        return codex(List.of(AccountAuth.executable(executable), "app-server"), cwd);
    }
    static List<Option> codex(List<String> command, Path cwd) throws Exception {
        RpcProcess.Handler handler = new RpcProcess.Handler() {
            public void notification(String method, JsonObject params) { }
            public void closed(String reason) { }
            public CompletableFuture<JsonObject> request(String method, JsonObject params) {
                return CompletableFuture.failedFuture(new IOException("Model discovery does not perform agent actions"));
            }
        };
        try (RpcProcess rpc = new RpcProcess(command, cwd, false, handler)) {
            rpc.request("initialize", Json.obj("clientInfo", Json.obj("name", "agent_bridge", "version", "0.8.1"))).get(10, TimeUnit.SECONDS);
            rpc.notify("initialized", new JsonObject());
            List<Option> options = new ArrayList<>(); Set<String> cursors = new HashSet<>(); String cursor = "";
            for (int page = 0; page < 5; page++) {
                JsonObject params = Json.obj("limit", 100, "includeHidden", false);
                if (!cursor.isEmpty()) params.addProperty("cursor", cursor);
                JsonObject result = rpc.request("model/list", params).get(10, TimeUnit.SECONDS);
                options.addAll(parseCodex(result));
                cursor = Json.str(result, "nextCursor");
                if (cursor.isEmpty()) return List.copyOf(options);
                if (!cursors.add(cursor)) throw new IOException("Model list returned a repeated page cursor");
            }
            throw new IOException("Model list exceeds five pages");
        }
    }
    static List<Option> parseCodex(JsonObject result) throws IOException {
        if (!result.has("data") || !result.get("data").isJsonArray()) throw new IOException("Missing model list");
        List<Option> options = new ArrayList<>();
        for (JsonElement entry : result.getAsJsonArray("data")) {
            JsonObject item = entry.getAsJsonObject();
            if (item.has("hidden") && item.get("hidden").getAsBoolean()) continue;
            String id = validate(Json.str(item, "model"));
            if (id.isEmpty()) continue;
            String label = Json.str(item, "displayName");
            if (label.isBlank() || label.length() > 120 || label.chars().anyMatch(Character::isISOControl)) label = id;
            options.add(new Option(id, label));
        }
        return List.copyOf(options);
    }
}
