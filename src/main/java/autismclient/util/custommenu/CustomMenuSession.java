package autismclient.util.custommenu;

import autismclient.api.custommenu.CustomMenuAdapterRegistry;
import autismclient.api.custommenu.CustomMenuEvent;
import autismclient.api.custommenu.CustomMenuSnapshot;
import net.minecraft.network.protocol.Packet;

public final class CustomMenuSession {
    private long generation;
    private volatile CustomMenuSnapshot current;

    public synchronized boolean accept(Packet<?> packet, String phase) {
        CustomMenuEvent event = CustomMenuAdapterRegistry.inspect(packet, phase);
        if (event.type() == CustomMenuEvent.Type.NONE) return false;
        if (event.type() == CustomMenuEvent.Type.CLEAR) {
            current = null;
        } else if (event.snapshot() != null) {
            current = event.snapshot().withConnectionState(phase, ++generation);
        }
        return true;
    }

    public CustomMenuSnapshot current() { return current; }
    public long generation() { return generation; }

    public synchronized void consume(CustomMenuSnapshot expected, CustomMenuSnapshot replacement) {
        if (expected != null && current != null && current.generation() != expected.generation()) return;
        current = replacement == null ? null : replacement.withConnectionState(
            expected == null ? "" : expected.phase(), ++generation);
    }

    public synchronized void clear() { current = null; }
}
