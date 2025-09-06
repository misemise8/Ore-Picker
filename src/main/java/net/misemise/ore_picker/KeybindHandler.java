package net.misemise.ore_picker;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side key state holder.
 * - setHolding(UUID, boolean)
 * - toggleHolding(UUID)
 * - isHolding(UUID)
 */
public class KeybindHandler {
    private static final Map<UUID, Boolean> HOLDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_PRESSED_MS = new ConcurrentHashMap<>();
    private static final long SHORT_HOLD_WINDOW = 700L;

    public static void setHolding(UUID id, boolean holding) {
        if (holding) {
            HOLDING.put(id, true);
            LAST_PRESSED_MS.put(id, System.currentTimeMillis());
        } else {
            HOLDING.remove(id);
            LAST_PRESSED_MS.remove(id);
        }
    }

    public static void toggleHolding(UUID id) {
        boolean was = HOLDING.getOrDefault(id, false);
        if (was) {
            HOLDING.remove(id);
            LAST_PRESSED_MS.remove(id);
        } else {
            HOLDING.put(id, true);
            LAST_PRESSED_MS.put(id, System.currentTimeMillis());
        }
    }

    public static boolean isHolding(UUID playerId) {
        Boolean b = HOLDING.get(playerId);
        if (b != null && b) return true;

        Long last = LAST_PRESSED_MS.get(playerId);
        if (last != null) {
            long delta = System.currentTimeMillis() - last;
            if (delta >= 0 && delta <= SHORT_HOLD_WINDOW) {
                return true;
            } else {
                LAST_PRESSED_MS.remove(playerId);
            }
        }
        return false;
    }
}
