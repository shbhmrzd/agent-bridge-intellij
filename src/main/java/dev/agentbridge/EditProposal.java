package dev.agentbridge;

import com.google.gson.*;
import java.util.*;
import java.util.regex.*;

/** Strict snippet replacement: only explicitly captured paths and unique, non-overlapping matches. */
final class EditProposal {
    private static final Pattern BLOCK = Pattern.compile("(?m)^```agent-bridge-edit[ \\t]*\\r?\\n([\\s\\S]*?)^```[ \\t]*(?:\\r?\\n|$)");
    record Change(String path, String before, String after) { }
    record Result(String explanation, List<Change> changes) { }
    private record Span(int start, int end, String replacement) { }
    static Result parse(String response, Map<String, String> context) {
        if (response.length() > 256000) throw new IllegalArgumentException("Response too large for edit review.");
        Matcher matcher = BLOCK.matcher(response);
        Map<String, List<Span>> byPath = new LinkedHashMap<>();
        int count = 0;
        while (matcher.find()) {
            JsonObject root;
            try { root = JsonParser.parseString(matcher.group(1)).getAsJsonObject(); }
            catch (RuntimeException invalid) { throw new IllegalArgumentException("The suggested edit is not valid JSON. Ask the agent to regenerate it."); }
            if (!root.has("edits") || !root.get("edits").isJsonArray()) throw new IllegalArgumentException("Suggested edit must contain an edits array.");
            for (JsonElement item : root.getAsJsonArray("edits")) {
                if (++count > 40) throw new IllegalArgumentException("Review is limited to 40 edits per response.");
                if (!item.isJsonObject()) throw new IllegalArgumentException("Invalid edit entry.");
                JsonObject edit = item.getAsJsonObject();
                for (String field : List.of("path", "oldText", "newText")) {
                    if (!edit.has(field) || !edit.get(field).isJsonPrimitive() || !edit.getAsJsonPrimitive(field).isString())
                        throw new IllegalArgumentException("Missing string field: " + field);
                }
                String path = Json.str(edit, "path"), before = context.get(path);
                if (before == null) throw new IllegalArgumentException("Edit targets a file not attached to this message: " + path);
                String old = Json.str(edit, "oldText").replace("\r\n", "\n");
                String replacement = Json.str(edit, "newText").replace("\r\n", "\n");
                if (replacement.indexOf('\r') >= 0) throw new IllegalArgumentException("Unsupported line separator in replacement.");
                int start = before.indexOf(old);
                if (old.isEmpty() || start < 0 || before.indexOf(old, start + 1) >= 0)
                    throw new IllegalArgumentException("Edit does not identify a unique snippet in " + path + ". Ask for more surrounding code.");
                byPath.computeIfAbsent(path, k -> new ArrayList<>()).add(new Span(start, start + old.length(), replacement));
            }
        }
        if (BLOCK.matcher(response).replaceAll("").contains("```agent-bridge-edit") || (response.contains("```agent-bridge-edit") && count == 0))
            throw new IllegalArgumentException("Incomplete or empty edit block. Ask the agent to regenerate it.");
        List<Change> changes = new ArrayList<>();
        byPath.forEach((path, spans) -> {
            spans.sort(Comparator.comparingInt(Span::start));
            int end = -1;
            for (Span s : spans) {
                if (s.start() < end) throw new IllegalArgumentException("Overlapping edits in " + path);
                end = s.end();
            }
            String before = context.get(path); StringBuilder after = new StringBuilder(before);
            for (int i = spans.size() - 1; i >= 0; i--) {
                Span s = spans.get(i); after.replace(s.start(), s.end(), s.replacement());
            }
            if (after.length() > 128000) throw new IllegalArgumentException("Proposed file exceeds 128,000 characters.");
            if (!before.contentEquals(after)) changes.add(new Change(path, before, after.toString()));
        });
        return new Result(BLOCK.matcher(response).replaceAll("").trim(), List.copyOf(changes));
    }
}
