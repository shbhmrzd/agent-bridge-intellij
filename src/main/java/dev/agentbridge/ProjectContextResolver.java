package dev.agentbridge;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;

/** Called off the UI thread. Each cancellable read releases the IDE lock between files. */
final class ProjectContextResolver {
    record Buffer(VirtualFile file, Document document, String path, String text, long stamp) { }
    record Result(List<Buffer> files, String inventory, boolean limited) { }
    private record Discovery(List<VirtualFile> children, boolean candidate, boolean limited) {
        static Discovery skip() { return new Discovery(List.of(), false, false); }
    }
    static Result resolve(Project project, List<VirtualFile> roots, String query, ContextPacket attached) {
        Path base = Path.of(Objects.requireNonNull(project.getBasePath())).toAbsolutePath().normalize();
        ArrayDeque<VirtualFile> pending = new ArrayDeque<>(roots);
        Set<String> visited = new HashSet<>(); Map<String, VirtualFile> candidates = new HashMap<>();
        ProjectFileIndex index = ProjectFileIndex.getInstance(project);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        boolean limited = false;
        while (!pending.isEmpty()) {
            cancelled(project);
            if (visited.size() >= ProjectContext.SCAN_LIMIT || System.nanoTime() > deadline) { limited = true; break; }
            VirtualFile file = pending.removeFirst();
            String path = relative(base, file);
            if (path == null || !visited.add(file.getPath())) continue;
            Discovery found = IdeRead.compute(project, () -> {
                if (!file.isValid() || file.is(VFileProperty.SYMLINK) || index.isExcluded(file) || index.isUnderIgnored(file) || index.isInGeneratedSources(file)) return Discovery.skip();
                if (!path.isEmpty() && !ProjectContext.autoInclude(path, file.isDirectory())) return Discovery.skip();
                if (file.isDirectory()) {
                    VirtualFile[] children = file.getChildren();
                    return new Discovery(Arrays.stream(children).limit(ProjectContext.SCAN_LIMIT)
                        .sorted(Comparator.comparing(VirtualFile::getName)).toList(), false, children.length > ProjectContext.SCAN_LIMIT);
                }
                return new Discovery(List.of(), !file.getFileType().isBinary() && file.getLength() <= 256000, false);
            });
            // A non-blocking read can retry. Publish results only after a successful attempt.
            if (found.candidate()) candidates.put(path, file);
            List<VirtualFile> children = found.children();
            limited |= found.limited();
            // Bound the traversal queue as well as the number of visited entries.
            int available = Math.max(0, ProjectContext.SCAN_LIMIT - visited.size() - pending.size());
            if (children.size() > available) limited = true;
            children.stream().limit(available).forEach(pending::addLast);
        }
        List<String> focus = roots.stream().map(r -> relative(base, r)).filter(Objects::nonNull).filter(s -> !s.isEmpty()).toList();
        List<String> ranked = ProjectContext.rank(candidates.keySet(), query, attached.activePath(), focus);
        List<String> scopeNames = roots.stream().map(r -> Optional.ofNullable(relative(base, r)).filter(s -> !s.isEmpty()).orElse("project")).toList();
        int available = ContextPacket.TOTAL_LIMIT - attached.selectedText().length() - attached.files().stream().mapToInt(f -> f.text().length()).sum();
        String inventory = ProjectContext.fitInventory(ProjectContext.inventory(ranked, scopeNames, limited), available);
        List<Buffer> sampled = new ArrayList<>();
        Set<String> explicit = attached.buffers().keySet(); int sampledChars = 0, examined = 0;
        for (String path : ranked) {
            cancelled(project);
            if (explicit.contains(path)) continue;
            if (examined++ >= ProjectContext.SAMPLE_LIMIT || sampledChars >= ProjectContext.READ_LIMIT) break;
            VirtualFile file = candidates.get(path);
            int remainingRead = ProjectContext.READ_LIMIT - sampledChars;
            Buffer buffer = IdeRead.compute(project, () -> {
                if (!file.isValid() || file.isDirectory()) return null;
                Document document = FileDocumentManager.getInstance().getDocument(file);
                if (document == null || document.getTextLength() > Math.min(ContextPacket.FILE_LIMIT, remainingRead)) return null;
                String text = document.getText();
                return new Buffer(file, document, path, text, document.getModificationStamp());
            });
            if (buffer != null) { sampled.add(buffer); sampledChars += buffer.text().length(); }
        }
        Map<String, Buffer> byPath = new HashMap<>(); sampled.forEach(b -> byPath.put(b.path(), b));
        var selected = ProjectContext.select(sampled.stream().map(b -> new ContextPacket.File(b.path(), b.text())).toList(),
            query, attached.activePath(), focus, Math.min(ProjectContext.AUTO_FILES, 12 - attached.files().size()), Math.min(ProjectContext.AUTO_CHARS, available - inventory.length()));
        return new Result(selected.stream().map(f -> byPath.get(f.path())).toList(), inventory, limited);
    }
    private static String relative(Path root, VirtualFile file) {
        Path path = Path.of(file.getPath()).toAbsolutePath().normalize();
        return path.startsWith(root) ? root.relativize(path).toString().replace('\\', '/') : null;
    }
    private static void cancelled(Project project) { if (Thread.currentThread().isInterrupted() || project.isDisposed()) throw new CancellationException(); }
}
