package autismclient.commands.impl;

import autismclient.commands.AutismCommandSource;
import autismclient.commands.AutismCommands;
import autismclient.commands.Command;
import autismclient.commands.args.MultiProfileArgumentType;
import autismclient.gui.screen.AutismMultiConsoleScreen;
import autismclient.gui.screen.AutismMultiScreen;
import autismclient.util.AutismClientMessaging;
import autismclient.util.multi.MultiManager;
import autismclient.util.multi.MultiProfile;
import autismclient.util.multi.MultiProfileManager;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.util.Collection;
import java.util.Locale;

public class MultiCommand extends Command {
    public MultiCommand() {
        super("multi", "Open the Multi menu, or launch/stop a profile.");
    }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> { openMenu(); return SUCCESS; });

        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("open")
            .executes(ctx -> { openMenu(); return SUCCESS; }));

        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("stop")
            .executes(ctx -> { stopBatch(); return SUCCESS; }));

        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("status")
            .executes(ctx -> { status(); return SUCCESS; }));

        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("launch")
            .executes(ctx -> {
                AutismClientMessaging.sendPrefixed("§eUsage: §f" + AutismCommands.effectivePrefix() + "multi launch <profile>");
                return SUCCESS;
            })
            .then(RequiredArgumentBuilder.<AutismCommandSource, String>argument("profile", MultiProfileArgumentType.profileName())
                .executes(ctx -> { launch(MultiProfileArgumentType.get(ctx, "profile")); return SUCCESS; })));
    }

    private static void openMenu() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.execute(() -> {
            if (mc.gui.screen() != null) return;
            Runnable proceed = () -> {
                if (MultiManager.get().isActive()) {
                    mc.gui.setScreen(new AutismMultiConsoleScreen(null));
                } else {
                    mc.gui.setScreen(new AutismMultiScreen(null, currentServerAddress(mc)));
                }
            };

            autismclient.gui.screen.AutismMultiDisclaimerScreen.open(mc, null, proceed);
        });
    }

    private static void launch(String name) {
        MultiProfile profile = resolveByName(MultiProfileManager.get().all(), name);
        if (profile == null) {
            AutismClientMessaging.sendPrefixed("§cNo profile named: §f" + name);
            String available = availableNames();
            if (!available.isEmpty()) AutismClientMessaging.sendPrefixed("§7Profiles: §f" + available);
            return;
        }
        MultiManager.StartResult result = MultiManager.get().start(profile);
        if (result.ok()) {
            AutismClientMessaging.sendPrefixed("§aLaunching Multi profile: §f" + profile.name
                + " §7(" + profile.sessions.size() + " account(s)) - run "
                + AutismCommands.effectivePrefix() + "multi to open the console.");
        } else {
            AutismClientMessaging.sendPrefixed("§cCould not launch: §f" + result.message());
        }
    }

    private static void stopBatch() {
        if (!MultiManager.get().isActive()) {
            AutismClientMessaging.sendPrefixed("§7No Multi batch is active.");
            return;
        }
        MultiManager.get().disconnectAll("Stopped via command");
        AutismClientMessaging.sendPrefixed("§eStopped the Multi batch.");
    }

    private static void status() {
        MultiManager mm = MultiManager.get();
        if (!mm.isActive()) {
            AutismClientMessaging.sendPrefixed("§7Multi: no batch active.");
            return;
        }
        MultiProfile active = mm.activeProfile();
        String name = active == null || active.name == null ? "(unknown)" : active.name;
        AutismClientMessaging.sendPrefixed("§aMulti: §f" + name
            + " §7- §f" + mm.readyCount() + "§7 ready, §f" + mm.connectedCount() + "§7 connected.");
    }

    private static String currentServerAddress(Minecraft mc) {
        ServerData sd = mc.getCurrentServer();
        return sd == null || sd.ip == null ? "" : sd.ip.trim();
    }

    private static String availableNames() {
        StringBuilder sb = new StringBuilder();
        for (MultiProfile p : MultiProfileManager.get().all()) {
            if (p == null || p.name == null) continue;
            if (sb.length() > 0) sb.append("§7, §f");
            sb.append(p.name);
        }
        return sb.toString();
    }

    public static MultiProfile resolveByName(Collection<MultiProfile> profiles, String name) {
        if (profiles == null || name == null) return null;
        String want = name.trim().toLowerCase(Locale.ROOT);
        if (want.isEmpty()) return null;
        for (MultiProfile p : profiles) {
            if (p != null && p.name != null && p.name.trim().toLowerCase(Locale.ROOT).equals(want)) return p;
        }
        return null;
    }
}
