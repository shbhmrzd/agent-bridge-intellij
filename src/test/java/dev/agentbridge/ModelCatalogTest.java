package dev.agentbridge;

import java.nio.file.Path;
import java.util.List;

public final class ModelCatalogTest {
    private static int passed;
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); passed++; System.out.println("PASS " + label); }
    public static void main(String[] args) throws Exception {
        var result = ModelCatalog.parseCodex(Json.obj("data", Json.arr(
            Json.obj("id", "catalog-row", "model", "actual-model", "displayName", "Friendly model"),
            Json.obj("model", "hidden-model", "hidden", true), Json.obj("model", ""))));
        check(result.size() == 1 && result.get(0).id().equals("actual-model") && result.get(0).label().equals("Friendly model"), "model picker uses model ID rather than catalog row ID and excludes hidden entries");
        var choices = ModelCatalog.choices(List.of(result.get(0), result.get(0)), "enterprise-model");
        check(choices.size() == 4 && choices.get(0).equals(ModelCatalog.DEFAULT) && choices.get(3).equals(ModelCatalog.CUSTOM), "choices deduplicate and preserve custom selections with explicit default/custom entries");
        check(ModelCatalog.defaults(AgentSession.Provider.Claude).stream().anyMatch(m -> m.id().equals("sonnet")), "Claude uses stable CLI family aliases");
        var claude = ModelCatalog.defaults(AgentSession.Provider.Claude);
        check(claude.stream().anyMatch(m -> m.id().equals("opus") && m.label().contains("CLI alias")), "family choice is explicitly an alias rather than a claimed version");
        check(claude.stream().anyMatch(m -> m.id().equals("claude-opus-5-5") && m.label().equals("Opus 5.5")), "versioned Opus choice retains exact provider ID");
        check(claude.stream().anyMatch(m -> m.id().equals("claude-sonnet-5-5") && m.label().equals("Sonnet 5.5")), "versioned Sonnet choice retains exact provider ID");
        check(ModelCatalog.choices(claude, "claude-opus-5-5").stream().filter(m -> m.id().equals("claude-opus-5-5")).count() == 1, "persisted exact choice is not duplicated");
        check(ModelCatalog.defaults(AgentSession.Provider.Copilot).stream().anyMatch(m -> m.id().equals("auto")), "Copilot exposes CLI Auto without inventing account-specific models");
        check(ModelCatalog.validate("  claude-enterprise[1m]  ").equals("claude-enterprise[1m]"), "custom model IDs keep provider suffixes");
        for (String invalid : List.of("bad\nmodel", "--flag", "two words", "x".repeat(201))) {
            try { ModelCatalog.validate(invalid); throw new AssertionError("invalid model accepted"); }
            catch (IllegalArgumentException expected) { check(true, "invalid custom model refused"); }
        }
        check(AgentSession.copilotCommand("/path with space/copilot", "provider/model").equals(List.of("/path with space/copilot", "--acp", "--stdio", "--model", "provider/model")), "Copilot model is a literal argument with ACP retained");
        var pages = ModelCatalog.codex(List.of("/usr/bin/python3", args[0]), Path.of("."));
        check(pages.size() == 2 && pages.get(1).id().equals("fixture-second"), "Codex catalog follows pagination without starting a thread or prompting");
        System.out.println("Passed " + passed + " model catalog checks (fake CLI; no model requests).");
    }
}
