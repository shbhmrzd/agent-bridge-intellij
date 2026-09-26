import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Synthetic screenshot example. Intentionally missing an expiry check. */
public final class TaskQueue {
    public record Lease(UUID token, Instant expiresAt) {}
    private final Map<UUID, Lease> leases = new HashMap<>();

    public synchronized void reserve(UUID taskId, Lease lease) {
        leases.put(taskId, lease);
    }

    public synchronized boolean acknowledge(UUID taskId, UUID token, Instant now) {
        Lease lease = leases.get(taskId);
        if (lease == null || !lease.token().equals(token)) {
            return false;
        }
        // Exercise: reject acknowledgement after this lease expires.
        leases.remove(taskId);
        return true;
    }
}
