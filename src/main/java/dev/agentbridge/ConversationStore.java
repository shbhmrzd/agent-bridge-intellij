package dev.agentbridge;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Local transcripts only: no editor snapshots, credentials, or native provider session IDs. */
final class ConversationStore {
    static final int MAX_CHATS = 100, MAX_TURNS = 100, MAX_TEXT = 2_000_000;
    static final long MAX_BYTES = 12_500_000;
    enum Status { INTERRUPTED, COMPLETED, FAILED }
    record Turn(String id, String provider, String model, String user, String assistant, Status status) { }
    record Chat(int version, String id, String title, long updated, List<Turn> turns, boolean truncated) {
        Chat { turns = List.copyOf(turns); }
    }
    record Summary(String id, String title, long updated, boolean readable) { }
    private final Path directory;

    ConversationStore(Path directory) { this.directory = directory; }
    static Path projectDirectory(Path config, String projectPath) {
        try {
            String key = Path.of(projectPath).toAbsolutePath().normalize().toString();
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
            return config.resolve("agent-bridge/conversations").resolve(hash);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private Path file(String id) {
        if (id == null || !UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException("Invalid conversation ID");
        return directory.resolve(id + ".json");
    }
    List<Summary> list() throws IOException {
        if (!Files.exists(directory)) return List.of();
        List<Summary> result = new ArrayList<>();
        try (var paths = Files.newDirectoryStream(directory, "*.json")) {
            for (Path path : paths) {
                String id = path.getFileName().toString().replaceFirst("\\.json$", "");
                try { file(id); } catch (IllegalArgumentException invalid) { continue; }
                try {
                    Chat chat = read(id);
                    result.add(new Summary(id, chat.title(), chat.updated(), true));
                } catch (IOException | IllegalArgumentException invalid) {
                    result.add(new Summary(id, "Unreadable saved conversation", 0, false));
                }
                // Bounded even if another program has populated this directory.
                if (result.size() > MAX_CHATS) throw new IOException("Too many saved conversations in the storage folder.");
            }
        }
        result.sort(Comparator.comparingLong(Summary::updated).reversed().thenComparing(Summary::id));
        return List.copyOf(result);
    }
    boolean exists(String id) { return Files.exists(file(id), LinkOption.NOFOLLOW_LINKS); }
    Chat read(String id) throws IOException {
        Path path = file(id);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_BYTES)
            throw new IOException("Saved conversation is missing, unreadable, or too large.");
        try {
            Chat chat = Json.GSON.fromJson(Files.readString(path), Chat.class);
            validate(chat, id);
            return chat;
        } catch (RuntimeException invalid) {
            throw new IOException("This saved conversation cannot be read. Its file has been preserved.", invalid);
        }
    }
    void create(Chat chat) throws IOException {
        if (exists(chat.id())) throw new IOException("A saved conversation already has this ID.");
        if (list().size() >= MAX_CHATS) throw new IOException("Saved chats are full (100). Delete a saved chat to make room.");
        write(chat);
    }
    void update(Chat chat) throws IOException {
        // Do not resurrect a conversation deleted while a response was in flight.
        read(chat.id());
        write(chat);
    }
    private void write(Chat chat) throws IOException {
        validate(chat, chat.id());
        Files.createDirectories(directory);
        privatePermissions(directory, true);
        byte[] bytes = Json.GSON.toJson(chat).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Saved conversation exceeds the storage limit.");
        Path temporary = Files.createTempFile(directory, "." + chat.id() + "-", ".tmp");
        try {
            privatePermissions(temporary, false);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            // An unsupported atomic move is a visible save failure; never truncate the old file.
            Files.move(temporary, file(chat.id()), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    void delete(String id) throws IOException {
        Path target = file(id);
        if (Files.isDirectory(directory)) try (var leftovers = Files.newDirectoryStream(directory, "." + id + "-*.tmp")) {
            for (Path leftover : leftovers) Files.deleteIfExists(leftover);
        }
        Files.deleteIfExists(target);
    }
    private static void privatePermissions(Path path, boolean directory) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
    }
    static String title(String value) {
        String text = value.replaceAll("\\s+", " ").strip();
        if (text.isEmpty() || text.length() > 120 || text.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Use a name between 1 and 120 characters, without control characters.");
        return text;
    }
    static String automaticTitle(String question) {
        String text = question.replaceAll("\\s+", " ").strip();
        if (text.length() > 80) {
            int end = Character.isHighSurrogate(text.charAt(78)) ? 78 : 79;
            text = text.substring(0, end) + "…";
        }
        return title(text);
    }
    static String savedAnswer(String response) {
        int edit = response.indexOf("```agent-bridge-edit");
        if (edit < 0) return response;
        int end = Math.min(edit, 255800);
        if (end > 0 && Character.isHighSurrogate(response.charAt(end - 1))) end--;
        return response.substring(0, end).stripTrailing()
            + "\n\n[Earlier edit suggestion omitted. Ask for a fresh suggestion against the current files.]";
    }
    static Chat bounded(Chat chat) {
        List<Turn> turns = new ArrayList<>(chat.turns());
        int size = turns.stream().mapToInt(t -> t.user().length() + t.assistant().length()).sum();
        boolean truncated = chat.truncated();
        while (turns.size() > MAX_TURNS || size > MAX_TEXT) {
            Turn removed = turns.removeFirst(); size -= removed.user().length() + removed.assistant().length(); truncated = true;
        }
        return new Chat(1, chat.id(), chat.title(), chat.updated(), turns, truncated);
    }
    private static void validate(Chat chat, String id) {
        if (chat == null || chat.version() != 1 || !id.equals(chat.id()) || chat.updated() < 0
            || !title(chat.title()).equals(chat.title()) || chat.turns().isEmpty() || chat.turns().size() > MAX_TURNS)
            throw new IllegalArgumentException("Invalid saved conversation");
        Set<String> ids = new HashSet<>(); int size = 0;
        for (Turn turn : chat.turns()) {
            if (turn == null || turn.id() == null || !ids.add(turn.id()) || turn.status() == null
                || turn.provider() == null || turn.model() == null || turn.user() == null || turn.assistant() == null
                || turn.user().isBlank() || turn.user().length() > 16000 || turn.assistant().length() > 256000)
                throw new IllegalArgumentException("Invalid saved message");
            UUID.fromString(turn.id()); AgentSession.Provider.valueOf(turn.provider()); ModelCatalog.validate(turn.model());
            size += turn.user().length() + turn.assistant().length();
        }
        if (size > MAX_TEXT) throw new IllegalArgumentException("Saved conversation exceeds its text limit");
    }
}
