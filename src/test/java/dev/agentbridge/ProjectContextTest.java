package dev.agentbridge;

import java.util.*;

public final class ProjectContextTest {
    private static int passed;
    private static void check(boolean ok, String name) { if (!ok) throw new AssertionError(name); passed++; System.out.println("PASS " + name); }
    public static void main(String[] args) {
        List<String> paths = List.of("README.md", "src/AuthService.java", "src/TaskQueue.java", "src/TaskQueueTest.java", "docs/setup.md");
        check(ProjectContext.rank(paths, "Fix TaskQueue retries", "").get(0).contains("TaskQueue"), "query selects matching project paths first");
        check(ProjectContext.rank(paths, "explain this", "src/TaskQueue.java").subList(0, 2).stream().allMatch(p -> p.contains("TaskQueue")), "current-file names prioritize related implementations and tests");
        check(ProjectContext.rank(paths, "overview", "", List.of("docs")).get(0).equals("docs/setup.md"), "explicit folder context influences project retrieval");
        check(ProjectContext.rank(List.of("b.java", "a.java", "a.java"), "", "").equals(List.of("a.java", "b.java")), "ranking is deterministic and deduplicated");
        check(ProjectContext.terms("Please explain OAuthCallback in this project").containsAll(List.of("oauth", "callback")), "camel-case query terms inform retrieval");
        List<ContextPacket.File> files = List.of(new ContextPacket.File("src/A.java", "nothing relevant"), new ContextPacket.File("src/B.java", "refreshToken OAuthCallback implementation"));
        check(ProjectContext.select(files, "OAuthCallback", "", List.of(), 1, 1000).get(0).path().endsWith("B.java"), "bounded source samples improve relevance beyond filenames");
        check(ProjectContext.select(List.of(new ContextPacket.File("best.java", "x".repeat(40)), new ContextPacket.File("small.java", "small")), "best", "", List.of(), 2, 10).get(0).path().equals("small.java"), "retrieval skips oversized candidates without truncating code buffers");
        check(ProjectContext.select(files, "", "", List.of(), 0, 1000).isEmpty(), "explicit attachments retain all slots when their count reaches the limit");
        check(ProjectContext.select(files, "", "", List.of(), 12, 0).isEmpty(), "automatic context never exceeds remaining prompt budget");
        for (String path : List.of(".env", ".env.local", ".git/config", "src/.cache/data", "node_modules/pkg/code.js", "target/classes/A.java", "keys/service.pem", "credentials.json", "config/secrets.yaml"))
            if (ProjectContext.autoInclude(path, false)) throw new AssertionError("Unexpected auto context: " + path);
        check(true, "automatic discovery skips hidden, generated, dependency and common credential files");
        check(ProjectContext.autoInclude("src/main/App.java", false) && ProjectContext.autoInclude("docs", true), "source files and documentation folders remain eligible");
        List<String> many = java.util.stream.IntStream.range(0, 400).mapToObj(i -> "src/" + "name".repeat(20) + i + ".java").toList();
        String inventory = ProjectContext.inventory(many, List.of("project"), true);
        check(inventory.length() < 15000 && inventory.contains("partial") && inventory.contains("not all project contents"), "bounded inventory discloses partial discovery and avoids claiming complete context");
        check(ProjectContext.fitInventory(inventory, 200).length() <= 200, "inventory yields space to explicit buffers");
        check(ProjectContext.fitInventory(inventory, 0).isEmpty(), "full explicit budget does not fail due to automatic inventory");
        try { new ContextPacket(List.of(new ContextPacket.File("a", "x".repeat(64000)), new ContextPacket.File("b", "x".repeat(64000)), new ContextPacket.File("c", "x".repeat(51000))), "a", "", "x".repeat(2000)); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { check(true, "project inventory counts toward the same total context budget"); }
        var packet = new ContextPacket(files, "src/A.java", "", inventory);
        check(packet.prompt("Fix the bug", false).contains("No mode switch is required") && packet.prompt("Explain this", false).contains("never claim all project files were read"), "single chat flow permits requested edits and explains scope limits to the agent");
        System.out.println("Passed " + passed + " project retrieval checks (no IDE, network or model calls).");
    }
}
