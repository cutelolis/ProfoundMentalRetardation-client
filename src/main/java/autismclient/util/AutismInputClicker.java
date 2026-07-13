package autismclient.util;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import autismclient.modules.PackHideState;

public final class AutismInputClicker {
    private static final Minecraft MC = Minecraft.getInstance();
    private static boolean attackQueued;
    private static boolean useQueued;
    private static int hotbarSlotQueued = -1;
    private static boolean attackPressed;
    private static boolean usePressed;
    private static KeyMapping hotbarPressed;

    private AutismInputClicker() {
    }

    public static void queueAttackClick() {
        if (PackHideState.isHardLocked()) return;
        attackQueued = true;
    }

    public static void queueUseClick() {
        if (PackHideState.isHardLocked()) return;
        useQueued = true;
    }

    public static void queueHotbarSlot(int slot) {
        if (PackHideState.isHardLocked()) return;
        hotbarSlotQueued = Math.max(0, Math.min(8, slot));
    }

    public static void beforeHandleKeybinds() {
        if (!canProcessInput()) {
            clear();
            return;
        }
        if (attackQueued) {
            simulate(MC.options.keyAttack, true);
            attackPressed = true;
        }
        if (useQueued) {
            simulate(MC.options.keyUse, true);
            usePressed = true;
        }
        if (hotbarSlotQueued >= 0 && MC.options.keyHotbarSlots != null && hotbarSlotQueued < MC.options.keyHotbarSlots.length) {
            hotbarPressed = MC.options.keyHotbarSlots[hotbarSlotQueued];
            simulate(hotbarPressed, true);
        }
        attackQueued = false;
        useQueued = false;
        hotbarSlotQueued = -1;
    }

    public static void onClientTickStart() {
        if (!canProcessInput()) clear();
    }

    public static void afterHandleKeybinds() {
        if (MC == null || MC.options == null) {
            attackPressed = false;
            usePressed = false;
            return;
        }
        if (attackPressed) {
            simulate(MC.options.keyAttack, false);
            attackPressed = false;
        }
        if (usePressed) {
            simulate(MC.options.keyUse, false);
            usePressed = false;
        }
        if (hotbarPressed != null) {
            simulate(hotbarPressed, false);
            hotbarPressed = null;
        }
    }

    public static void clear() {
        attackQueued = false;
        useQueued = false;
        hotbarSlotQueued = -1;
        afterHandleKeybinds();
    }

    private static boolean canProcessInput() {
        return MC != null
            && MC.player != null
            && MC.level != null
            && MC.options != null
            && MC.getWindow() != null
            && MC.gui.screen() == null
            && MC.gui.overlay() == null
            && !PackHideState.isActive();
    }

    private static void simulate(KeyMapping mapping, boolean pressed) {
        if (mapping != null) AutismKeyMappingBridge.of(mapping).autism$simulatePress(pressed);
    }
}
