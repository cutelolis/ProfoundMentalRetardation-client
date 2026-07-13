package autismclient.util.macro;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FpsLimitController {

    private static final long LEGACY_OWNER = 0L;
    private static final ConcurrentHashMap<Long, Override> OVERRIDES = new ConcurrentHashMap<>();

    private record Override(int limit, long expiryNanos, boolean indefinite) {
        boolean active(long now) {
            return indefinite || now - expiryNanos < 0L;
        }
    }

    private FpsLimitController() {}

    public static void apply(int fpsLimit, long durationNanos) {
        apply(LEGACY_OWNER, fpsLimit, durationNanos);
    }

    public static void apply(long ownerRunId, int fpsLimit, long durationNanos) {
        if (durationNanos <= 0L) {
            clear(ownerRunId);
            return;
        }
        OVERRIDES.put(ownerRunId, new Override(Math.max(0, fpsLimit), System.nanoTime() + durationNanos, false));
    }

    public static void applyUntilCleared(int fpsLimit) {
        applyUntilCleared(LEGACY_OWNER, fpsLimit);
    }

    public static void applyUntilCleared(long ownerRunId, int fpsLimit) {
        OVERRIDES.put(ownerRunId, new Override(Math.max(0, fpsLimit), 0L, true));
    }

    public static void clear() {
        clear(LEGACY_OWNER);
    }

    public static void clear(long ownerRunId) {
        OVERRIDES.remove(ownerRunId);
    }

    public static void clearAll() {
        OVERRIDES.clear();
    }

    public static boolean isActive() {
        pruneExpired();
        return !OVERRIDES.isEmpty();
    }

    public static int limit() {
        int active = activeLimit();
        return Math.max(0, active);
    }

    public static int activeLimit() {
        pruneExpired();
        int effective = Integer.MAX_VALUE;
        for (Override override : OVERRIDES.values()) effective = Math.min(effective, override.limit);
        return effective == Integer.MAX_VALUE ? -1 : effective;
    }

    public static boolean shouldFreeze() {
        return activeLimit() == 0;
    }

    public static long remainingMillis() {
        pruneExpired();
        if (OVERRIDES.isEmpty()) return 0L;
        long now = System.nanoTime();
        long longest = 0L;
        for (Override override : OVERRIDES.values()) {
            if (override.indefinite) return Long.MAX_VALUE;
            longest = Math.max(longest, override.expiryNanos - now);
        }
        return longest > 0L ? longest / 1_000_000L : 0L;
    }

    private static void pruneExpired() {
        long now = System.nanoTime();
        for (Map.Entry<Long, Override> entry : OVERRIDES.entrySet()) {
            if (!entry.getValue().active(now)) OVERRIDES.remove(entry.getKey(), entry.getValue());
        }
    }
}
