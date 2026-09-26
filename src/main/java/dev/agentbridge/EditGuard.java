package dev.agentbridge;

/** Conservative: any document modification invalidates the proposal, even outside its selection. */
final class EditGuard {
    static boolean sameBuffer(long capturedStamp, long currentStamp, String capturedPath, String currentPath, String before, String now) {
        return capturedStamp == currentStamp && capturedPath.equals(currentPath) && before.equals(now);
    }
}
