package autismclient.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public final class PackAutoReconnectState {
    private static final Minecraft MC = Minecraft.getInstance();
    private static ServerData lastServer;
    private static ServerAddress lastAddress;
    private static Screen countdownScreen;
    private static int ticksLeft;
    private static boolean cancelled;

    private PackAutoReconnectState() {
    }

    public static void remember(ServerData server) {
        if (server == null || server.ip == null || server.ip.isBlank()) return;
        remember(server, ServerAddress.parseString(server.ip));
    }

    public static void remember(ServerData server, ServerAddress address) {
        if (server == null) return;
        if ((server.ip == null || server.ip.isBlank()) && address == null) return;
        ServerData copy = new ServerData(server.name, server.ip, server.type());
        copy.copyFrom(server);
        lastServer = copy;
        lastAddress = address != null ? address : ServerAddress.parseString(server.ip);
        countdownScreen = null;
        ticksLeft = delayTicks();
        cancelled = false;
    }

    public static boolean shouldShow() {
        Module module = ModuleRegistry.get("auto-reconnect");
        return module != null && module.isEnabled() && lastServer != null && lastAddress != null && MC.allowsMultiplayer();
    }

    public static boolean hideButtons() {
        Module module = ModuleRegistry.get("auto-reconnect");
        return module != null && module.isEnabled() && Boolean.parseBoolean(module.value("hide-buttons"));
    }

    public static void tick(Screen screen, Screen parent) {
        if (!shouldShow()) {
            countdownScreen = null;
            return;
        }
        if (countdownScreen != screen) {
            countdownScreen = screen;
            ticksLeft = delayTicks();
        }
        if (cancelled) return;
        if (ticksLeft > 0) {
            ticksLeft--;
            return;
        }
        reconnect(parent);
    }

    public static void cancel() {
        cancelled = true;
    }

    public static boolean isCounting() {
        return shouldShow() && !cancelled && ticksLeft > 0;
    }

    public static String reconnectButtonLabel() {
        if (isCounting()) {
            return String.format(java.util.Locale.ROOT, "Reconnect Now  (%.1fs)", Math.max(0.0, ticksLeft / 20.0));
        }
        return "Reconnect Now";
    }

    public static void tickCurrentScreen() {
        if (MC.gui.screen() instanceof DisconnectedScreen) {
            tick(MC.gui.screen(), MC.gui.screen());
        } else {
            countdownScreen = null;
        }
    }

    public static void reconnect(Screen parent) {
        if (!shouldShow()) return;
        Screen nextParent = parent == null ? MC.gui.screen() : parent;
        ConnectScreen.startConnecting(nextParent, MC, lastAddress, lastServer, false, null);
        countdownScreen = null;
        ticksLeft = delayTicks();
        cancelled = false;
    }

    public static String statusText() {
        if (!shouldShow()) return "";
        double seconds = Math.max(0.0, ticksLeft / 20.0);
        return String.format(java.util.Locale.ROOT, "Auto reconnect in %.1fs", seconds);
    }

    private static int delayTicks() {
        Module module = ModuleRegistry.get("auto-reconnect");
        if (module == null) return 70;
        try {
            return Math.max(0, (int) Math.round(Double.parseDouble(module.value("delay")) * 20.0));
        } catch (NumberFormatException ignored) {
            return 70;
        }
    }
}
