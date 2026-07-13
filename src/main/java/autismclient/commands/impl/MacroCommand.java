package autismclient.commands.impl;

import autismclient.commands.Command;
import autismclient.commands.AutismCommandSource;
import autismclient.commands.AutismCommands;
import autismclient.commands.args.MacroArgumentType;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismMacroManager;
import autismclient.util.macro.MacroExecutor;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class MacroCommand extends Command {
    private static final ConcurrentHashMap<String, AtomicBoolean> RUNNING_LOOPS = new ConcurrentHashMap<>();

    public MacroCommand() { super("macro", "Run a macro, optionally N times with a delay between starts."); }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> {
            String prefix = AutismCommands.effectivePrefix();
            AutismClientMessaging.sendPrefixed("§eUsage: §f" + prefix + "macro <name> [times] [delayTicks]");
            AutismClientMessaging.sendPrefixed("§7Also: §f" + prefix + "macro stop [name] §7or §f" + prefix + "macro clear");
            return SUCCESS;
        });

        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("stop")
            .executes(ctx -> {
                stopAllLoops();
                AutismMacroManager.get().stopMacro();
                AutismClientMessaging.sendPrefixed("§eStopped all macros.");
                return SUCCESS;
            })
            .then(RequiredArgumentBuilder.<AutismCommandSource, String>argument("name", MacroArgumentType.macroName())
                .executes(ctx -> {
                    String name = MacroArgumentType.get(ctx, "name");
                    AtomicBoolean flag = RUNNING_LOOPS.remove(name.toLowerCase());
                    if (flag != null) flag.set(false);
                    MacroExecutor.stopMacro(name);
                    AutismClientMessaging.sendPrefixed("§eStopped: §f" + name);
                    return SUCCESS;
                })));

        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("clear")
            .executes(ctx -> { stopAllLoops(); AutismClientMessaging.sendPrefixed("§eCleared macro loops."); return SUCCESS; }));

        root.then(RequiredArgumentBuilder.<AutismCommandSource, String>argument("name", MacroArgumentType.macroName())
            .executes(ctx -> startLoop(MacroArgumentType.get(ctx, "name"), 1, 0))
            .then(RequiredArgumentBuilder.<AutismCommandSource, Integer>argument("times", IntegerArgumentType.integer(1, 100_000))
                .executes(ctx -> startLoop(MacroArgumentType.get(ctx, "name"), IntegerArgumentType.getInteger(ctx, "times"), 0))
                .then(RequiredArgumentBuilder.<AutismCommandSource, Integer>argument("delayTicks", IntegerArgumentType.integer(0, 20 * 60 * 60))
                    .executes(ctx -> startLoop(
                        MacroArgumentType.get(ctx, "name"),
                        IntegerArgumentType.getInteger(ctx, "times"),
                        IntegerArgumentType.getInteger(ctx, "delayTicks"))))));
    }

    private static int startLoop(String name, int times, int delayTicks) {
        if (AutismMacroManager.get().get(name) == null) {
            AutismClientMessaging.sendPrefixed("§cMacro not found: §f" + name);
            return SUCCESS;
        }
        if (times <= 1 && delayTicks <= 0) {
            AutismMacroManager.get().executeMacro(name);
            return SUCCESS;
        }
        AtomicBoolean flag = new AtomicBoolean(true);
        RUNNING_LOOPS.put(name.toLowerCase(), flag);
        long sleepMs = Math.max(0L, delayTicks * 50L);
        final int totalTimes = Math.max(1, times);
        Thread t = new Thread(() -> {
            try {
                for (int i = 0; i < totalTimes && flag.get(); i++) {
                    AutismMacroManager.get().executeMacro(name);

                    long start = System.nanoTime();
                    long waitTimeoutNanos = 24L * 60L * 60L * 1_000_000_000L;
                    while (MacroExecutor.isMacroRunning(name) && flag.get()
                        && System.nanoTime() - start < waitTimeoutNanos) {
                        Thread.sleep(50);
                    }
                    if (!flag.get()) break;
                    if (i + 1 < totalTimes && sleepMs > 0) Thread.sleep(sleepMs);
                }
            } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            finally { RUNNING_LOOPS.remove(name.toLowerCase(), flag); }
        }, "MacroLoop-" + name);
        t.setDaemon(true);
        t.start();
        AutismClientMessaging.sendPrefixed("§aQueued " + totalTimes + "x §f" + name + "§a (delay " + delayTicks + "t)");
        return SUCCESS;
    }

    private static void stopAllLoops() {
        for (AtomicBoolean flag : RUNNING_LOOPS.values()) flag.set(false);
        RUNNING_LOOPS.clear();
    }
}
