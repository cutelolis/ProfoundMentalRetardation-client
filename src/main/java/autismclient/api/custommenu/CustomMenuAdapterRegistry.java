package autismclient.api.custommenu;

import autismclient.AutismClientAddon;
import autismclient.addons.AddonManager;
import autismclient.util.custommenu.VanillaDialogAdapter;
import net.minecraft.network.protocol.Packet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CustomMenuAdapterRegistry {
    private record Registered(CustomMenuAdapter adapter, String owner) {}
    private static final Map<String, Registered> ADAPTERS = new LinkedHashMap<>();

    static {
        ADAPTERS.put(VanillaDialogAdapter.ID, new Registered(new VanillaDialogAdapter(), "autismclient"));
    }

    private CustomMenuAdapterRegistry() {}

    public static synchronized boolean register(CustomMenuAdapter adapter) {
        if (adapter == null || adapter.id() == null || adapter.id().isBlank()) return false;
        String id = adapter.id().trim().toLowerCase(java.util.Locale.ROOT);
        if (ADAPTERS.containsKey(id)) return false;
        ADAPTERS.put(id, new Registered(adapter, AddonManager.currentAddonId()));
        return true;
    }

    public static synchronized void unregisterAddon(String addonId) {
        if (addonId == null || addonId.isBlank()) return;
        ADAPTERS.entrySet().removeIf(entry -> addonId.equals(entry.getValue().owner()));
    }

    public static CustomMenuEvent inspect(Packet<?> packet, String phase) {
        List<Registered> snapshot;
        synchronized (CustomMenuAdapterRegistry.class) {
            snapshot = new ArrayList<>(ADAPTERS.values());
        }
        for (Registered registered : snapshot) {
            try {
                CustomMenuEvent event = registered.adapter().inspectInbound(packet, phase);
                if (event != null && event.type() != CustomMenuEvent.Type.NONE) return event;
            } catch (Throwable error) {
                AutismClientAddon.LOG.warn("[CustomMenus] Adapter '{}' failed while reading a packet", registered.adapter().id(), error);
            }
        }
        return CustomMenuEvent.NONE;
    }

    public static CustomMenuSubmitResult submit(CustomMenuSnapshot snapshot, CustomMenuSubmission submission) {
        if (snapshot == null) return CustomMenuSubmitResult.failure("No custom menu is open");
        Registered registered;
        synchronized (CustomMenuAdapterRegistry.class) {
            registered = ADAPTERS.get(snapshot.adapterId().toLowerCase(java.util.Locale.ROOT));
        }
        if (registered == null) return CustomMenuSubmitResult.failure("Custom menu adapter is unavailable");
        try {
            CustomMenuSubmitResult result = registered.adapter().submit(snapshot, submission);
            return result == null ? CustomMenuSubmitResult.failure("Custom menu adapter returned no result") : result;
        } catch (Throwable error) {

            AutismClientAddon.LOG.warn("[CustomMenus] Adapter '{}' failed while submitting a form ({})",
                registered.adapter().id(), error.getClass().getSimpleName());
            return CustomMenuSubmitResult.failure("Custom menu adapter failed");
        }
    }

    public static synchronized List<String> ids() {
        return List.copyOf(ADAPTERS.keySet());
    }
}
