package dev.agentbridge;

import java.util.*;

/** Small deterministic retrieval policy; no embeddings, daemon, or whole-repository upload. */
final class ProjectContext {
    static final int SCAN_LIMIT = 12000, SAMPLE_LIMIT = 80, READ_LIMIT = 2_000_000, INVENTORY_LIMIT = 14000;
    static final int AUTO_FILES = 6, AUTO_CHARS = 60000;
    private static final Set<String> SKIP = Set.of("node_modules", "target", "build", "dist", "out", "vendor", "coverage", "__pycache__", "venv", "env");
    private static final Set<String> STOP = Set.of("the", "and", "this", "that", "with", "from", "file", "files", "project", "code", "please", "explain", "implement", "change", "what", "how", "does", "can", "for", "use", "into", "its", "our");
    static boolean autoInclude(String path, boolean directory) {
        String normalized = path.replace('\\', '/');
        for (String part : normalized.split("/")) {
            String name = part.toLowerCase(Locale.ROOT);
            if (name.startsWith(".") || SKIP.contains(name)) return false;
        }
        if (directory) return true;
        String name = normalized.toLowerCase(Locale.ROOT);
        return !(name.endsWith(".pem") || name.endsWith(".key") || name.endsWith(".p12") || name.endsWith(".pfx")
            || name.endsWith(".jks") || name.endsWith(".keystore") || name.endsWith(".lock") || name.endsWith("-lock.json")
            || name.endsWith(".min.js") || name.endsWith(".map") || name.endsWith(".log")
            || name.matches(".*(^|/)(credentials|secrets)(\\.[^/]*)?$"));
    }
    static List<String> terms(String query) {
        String expanded = query.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        return Arrays.stream(expanded.split("[^a-z0-9_]+"))
            .filter(s -> s.length() > 2 && s.length() < 80 && !STOP.contains(s)).distinct().limit(24).toList();
    }
    static int pathScore(String path, List<String> terms, String active) {
        String lower = path.toLowerCase(Locale.ROOT);
        String basename = lower.substring(lower.lastIndexOf('/') + 1);
        int score = 0;
        for (String term : terms) {
            if (basename.contains(term)) score += 30;
            else if (lower.contains(term)) score += 10;
        }
        String activeBase = active.substring(active.lastIndexOf('/') + 1).replaceFirst("\\.[^.]*$", "").toLowerCase(Locale.ROOT);
        if (!activeBase.isBlank() && basename.contains(activeBase)) score += 35;
        int slash = active.lastIndexOf('/');
        if (slash >= 0 && path.startsWith(active.substring(0, slash + 1))) score += 4;
        if (basename.startsWith("readme") || basename.equals("pom.xml") || basename.equals("package.json") || basename.startsWith("build.gradle")) score += 8;
        return score;
    }
    static int textScore(String text, List<String> terms) {
        String sample = text.substring(0, Math.min(12000, text.length())).toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            int at = -1;
            for (int count = 0; count < 3 && (at = sample.indexOf(term, at + 1)) >= 0; count++) score += 3;
        }
        return score;
    }
    static List<String> rank(Collection<String> paths, String query, String active) {
        return rank(paths, query, active, List.of());
    }
    static List<String> rank(Collection<String> paths, String query, String active, List<String> folders) {
        List<String> terms = terms(query);
        return paths.stream().distinct().sorted(Comparator.<String>comparingInt(p -> pathScore(p, terms, active) + folderScore(p, folders)).reversed().thenComparing(p -> p)).toList();
    }
    private static int folderScore(String path, List<String> folders) { return folders.stream().anyMatch(f -> !f.isEmpty() && path.startsWith(f + "/")) ? 30 : 0; }
    static List<ContextPacket.File> select(List<ContextPacket.File> sampled, String query, String active, List<String> folders, int count, int budget) {
        List<String> terms = terms(query); Map<String, ContextPacket.File> unique = new LinkedHashMap<>();
        sampled.forEach(file -> unique.putIfAbsent(file.path(), file));
        List<ContextPacket.File> ranked = unique.values().stream().sorted(Comparator.<ContextPacket.File>comparingInt(f ->
            pathScore(f.path(), terms, active) + folderScore(f.path(), folders) + textScore(f.text(), terms)).reversed().thenComparing(ContextPacket.File::path)).toList();
        List<ContextPacket.File> selected = new ArrayList<>();
        for (var file : ranked) {
            if (selected.size() >= count) break;
            if (file.text().length() > ContextPacket.FILE_LIMIT || file.text().length() > budget) continue;
            selected.add(file); budget -= file.text().length();
        }
        return List.copyOf(selected);
    }
    static String fitInventory(String inventory, int available) {
        if (inventory.length() <= available) return inventory;
        if (available < 100) return "";
        return inventory.substring(0, available - 80) + "\nInventory shortened to fit the context budget.";
    }
    static String inventory(List<String> ranked, List<String> scopes, boolean limited) {
        StringBuilder out = new StringBuilder("Search scope: ").append(String.join(", ", scopes)).append("\n")
            .append("This is a bounded file inventory, not all project contents. Current buffers appear separately.\n");
        if (limited) out.append("File discovery reached its time or size budget; the inventory is partial.\n");
        int count = 0;
        for (String path : ranked) {
            if (out.length() + path.length() + 2 > INVENTORY_LIMIT || count >= 200) break;
            out.append(path).append('\n'); count++;
        }
        out.append("Listed ").append(count).append(" of ").append(ranked.size()).append(" discovered files.\n")
            .append("Read/search other relevant project files with your available read-only tools when needed. Only supplied buffers support reviewable edits; ask for additional attachments for other edits.");
        return out.toString();
    }
}
