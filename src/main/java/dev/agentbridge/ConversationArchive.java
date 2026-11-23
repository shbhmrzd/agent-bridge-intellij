package dev.agentbridge;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** One serialized I/O queue per project; an open chat can be owned by only one surface. */
final class ConversationArchive implements AutoCloseable {
    private final ConversationStore store;
    private final Map<String, Object> owners = new HashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "agent-bridge-saved-chats"); thread.setDaemon(true); return thread;
    });
    ConversationArchive(ConversationStore store) { this.store = store; }
    private interface Operation<T> { T run() throws Exception; }
    private <T> CompletableFuture<T> submit(Operation<T> operation) {
        try { return CompletableFuture.supplyAsync(() -> {
            try { return operation.run(); }
            catch (Exception error) { throw new CompletionException(error); }
        }, io); } catch (RejectedExecutionException closed) {
            return CompletableFuture.failedFuture(new IOException("The project is closing; saved chats are unavailable.", closed));
        }
    }
    private void available(String id, Object owner) throws IOException {
        if (owners.containsKey(id) && owners.get(id) != owner)
            throw new IOException("This conversation is open in another chat. Use New chat there or close that chat first.");
    }
    CompletableFuture<List<ConversationStore.Summary>> list() { return submit(store::list); }
    CompletableFuture<ConversationStore.Chat> resume(String id, Object owner) {
        return submit(() -> {
            available(id, owner); var chat = store.read(id);
            owners.values().removeIf(value -> value == owner); owners.put(id, owner); return chat;
        });
    }
    CompletableFuture<ConversationStore.Chat> begin(String id, Object owner, ConversationStore.Turn turn) {
        return submit(() -> {
            available(id, owner);
            var chat = store.exists(id) ? store.read(id) : null;
            List<ConversationStore.Turn> turns = new ArrayList<>(chat == null ? List.of() : chat.turns());
            turns.add(turn);
            var next = ConversationStore.bounded(new ConversationStore.Chat(1, id,
                chat == null ? ConversationStore.automaticTitle(turn.user()) : chat.title(), System.currentTimeMillis(), turns,
                chat != null && chat.truncated()));
            if (chat == null) store.create(next); else store.update(next);
            owners.put(id, owner); return next;
        });
    }
    CompletableFuture<ConversationStore.Chat> finish(String id, Object owner, String turnId, String answer, ConversationStore.Status status) {
        return submit(() -> {
            available(id, owner); var chat = store.read(id);
            List<ConversationStore.Turn> turns = new ArrayList<>(chat.turns()); boolean found = false;
            for (int i = 0; i < turns.size(); i++) {
                var turn = turns.get(i);
                if (!turn.id().equals(turnId)) continue;
                turns.set(i, new ConversationStore.Turn(turn.id(), turn.provider(), turn.model(), turn.user(),
                    ConversationStore.savedAnswer(answer), status)); found = true; break;
            }
            if (!found) throw new IOException("The saved message is no longer available.");
            var next = ConversationStore.bounded(new ConversationStore.Chat(1, id, chat.title(), System.currentTimeMillis(), turns, chat.truncated()));
            store.update(next); return next;
        });
    }
    CompletableFuture<Void> rename(String id, Object owner, String title) {
        return submit(() -> {
            available(id, owner); var chat = store.read(id);
            store.update(new ConversationStore.Chat(1, id, ConversationStore.title(title), chat.updated(), chat.turns(), chat.truncated()));
            return null;
        });
    }
    CompletableFuture<Void> delete(String id, Object owner) {
        return submit(() -> { available(id, owner); store.delete(id); owners.remove(id); return null; });
    }
    CompletableFuture<Void> release(Object owner) {
        return submit(() -> { owners.values().removeIf(value -> value == owner); return null; });
    }
    @Override public void close() { io.shutdown(); } // Drain already-queued saves; never cancel them on popup close.
    boolean awaitClosed(long seconds) throws InterruptedException { return io.awaitTermination(seconds, TimeUnit.SECONDS); }
}
